-- ============================================================================
-- BudgetManager — Fonctions transactionnelles (atomiques) : CRUD transaction
-- avec ajustement du solde, et traitement des récurrences.
-- SECURITY INVOKER → RLS appliquée. plpgsql → une seule transaction par appel.
-- ============================================================================

-- Créer une transaction + ajuster le solde du compte. Retourne le nouvel id.
create or replace function public.create_transaction(
    p_account_id bigint,
    p_title text,
    p_amount numeric,
    p_type text,
    p_date timestamptz,
    p_category_id bigint default null,
    p_notes text default null,
    p_is_recurring boolean default false,
    p_recurring_id bigint default null
)
returns bigint
language plpgsql
security invoker
as $$
declare
    v_id bigint;
    v_delta numeric;
begin
    insert into public.transactions
        (account_id, category_id, title, amount, transaction_type, date, notes, is_recurring, recurring_transaction_id)
        values (p_account_id, p_category_id, p_title, p_amount, p_type, p_date, p_notes, p_is_recurring, p_recurring_id)
        returning id into v_id;

    v_delta := case p_type when 'EXPENSE' then -p_amount when 'INCOME' then p_amount else 0 end;
    if v_delta <> 0 then
        update public.accounts set balance = balance + v_delta where id = p_account_id;
    end if;
    return v_id;
end;
$$;

-- Mettre à jour une transaction : annule l'effet solde de l'ancienne, applique la nouvelle.
create or replace function public.update_transaction(
    p_id bigint,
    p_account_id bigint,
    p_title text,
    p_amount numeric,
    p_type text,
    p_date timestamptz,
    p_category_id bigint default null,
    p_notes text default null,
    p_is_recurring boolean default false,
    p_recurring_id bigint default null
)
returns void
language plpgsql
security invoker
as $$
declare
    v_old_account bigint;
    v_old_amount numeric;
    v_old_type text;
    v_rev numeric;
    v_new numeric;
begin
    select account_id, amount, transaction_type
        into v_old_account, v_old_amount, v_old_type
        from public.transactions where id = p_id;
    if not found then return; end if;

    v_rev := case v_old_type when 'EXPENSE' then v_old_amount when 'INCOME' then -v_old_amount else 0 end;
    if v_rev <> 0 then
        update public.accounts set balance = balance + v_rev where id = v_old_account;
    end if;

    update public.transactions set
        account_id = p_account_id, category_id = p_category_id, title = p_title,
        amount = p_amount, transaction_type = p_type, date = p_date, notes = p_notes,
        is_recurring = p_is_recurring, recurring_transaction_id = p_recurring_id
        where id = p_id;

    v_new := case p_type when 'EXPENSE' then -p_amount when 'INCOME' then p_amount else 0 end;
    if v_new <> 0 then
        update public.accounts set balance = balance + v_new where id = p_account_id;
    end if;
end;
$$;

-- Supprimer une transaction + annuler son effet sur le solde.
create or replace function public.delete_transaction(p_id bigint)
returns void
language plpgsql
security invoker
as $$
declare
    v_account bigint;
    v_amount numeric;
    v_type text;
    v_rev numeric;
begin
    select account_id, amount, transaction_type
        into v_account, v_amount, v_type
        from public.transactions where id = p_id;
    if not found then return; end if;

    v_rev := case v_type when 'EXPENSE' then v_amount when 'INCOME' then -v_amount else 0 end;
    delete from public.transactions where id = p_id;
    if v_rev <> 0 then
        update public.accounts set balance = balance + v_rev where id = v_account;
    end if;
end;
$$;

-- Traiter les récurrences échues : génère les transactions manquées jusqu'à aujourd'hui,
-- ajuste les soldes et avance next_due_date (idempotent : avance le curseur après chaque échéance).
create or replace function public.process_recurring_transactions()
returns void
language plpgsql
security invoker
as $$
declare
    r record;
    v_today date := current_date;
    v_current date;
    v_interval int;
    v_iter int;
    v_next date;
    v_src_exists boolean;
    v_dest_exists boolean;
    v_delta numeric;
begin
    for r in
        select * from public.recurring_transactions
        where is_active = true and next_due_date <= v_today
    loop
        select exists(select 1 from public.accounts where id = r.account_id) into v_src_exists;
        if r.destination_account_id is not null then
            select exists(select 1 from public.accounts where id = r.destination_account_id) into v_dest_exists;
        else
            v_dest_exists := true;
        end if;
        if not v_src_exists or not v_dest_exists then
            update public.recurring_transactions set is_active = false where id = r.id;
            continue;
        end if;

        v_current := r.next_due_date;
        v_interval := case when r.repeat_interval <= 0 then 1 else r.repeat_interval end;
        v_iter := 0;

        while v_current <= v_today and v_iter < 1000 loop
            v_iter := v_iter + 1;

            if r.end_date is not null and v_current > r.end_date then
                update public.recurring_transactions
                    set is_active = false, last_generated_date = v_today where id = r.id;
                exit;
            end if;

            if r.transaction_type = 'TRANSFER' and r.destination_account_id is not null then
                insert into public.transactions
                    (account_id, category_id, title, amount, transaction_type, date, notes, is_recurring, recurring_transaction_id)
                    values (r.account_id, r.category_id, r.title || ' (sortant)', r.amount, 'TRANSFER', v_current::timestamptz, r.notes, true, r.id);
                insert into public.transactions
                    (account_id, category_id, title, amount, transaction_type, date, notes, is_recurring, recurring_transaction_id)
                    values (r.destination_account_id, r.category_id, r.title || ' (entrant)', r.amount, 'TRANSFER', v_current::timestamptz, r.notes, true, r.id);
                update public.accounts set balance = balance - r.amount where id = r.account_id;
                update public.accounts set balance = balance + r.amount where id = r.destination_account_id;
            else
                insert into public.transactions
                    (account_id, category_id, title, amount, transaction_type, date, notes, is_recurring, recurring_transaction_id)
                    values (r.account_id, r.category_id, r.title, r.amount, r.transaction_type, v_current::timestamptz, r.notes, true, r.id);
                v_delta := case r.transaction_type when 'EXPENSE' then -r.amount when 'INCOME' then r.amount else 0 end;
                if v_delta <> 0 then
                    update public.accounts set balance = balance + v_delta where id = r.account_id;
                end if;
            end if;

            v_next := (v_current + case r.frequency_type
                when 'DAILY'     then make_interval(days   => v_interval)
                when 'WEEKLY'    then make_interval(weeks  => v_interval)
                when 'BI_WEEKLY' then make_interval(weeks  => v_interval * 2)
                when 'MONTHLY'   then make_interval(months => v_interval)
                when 'QUARTERLY' then make_interval(months => v_interval * 3)
                when 'YEARLY'    then make_interval(years  => v_interval)
                else make_interval(days => v_interval)
            end)::date;

            update public.recurring_transactions
                set last_generated_date = v_today, next_due_date = v_next where id = r.id;
            v_current := v_next;
        end loop;
    end loop;
end;
$$;
