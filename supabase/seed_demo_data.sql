-- ============================================================================
-- BudgetManager — Données de DÉMO (12 mois, avec saisonnalité) pour tester l'IA.
--
-- À exécuter dans le SQL Editor de Supabase. Crée un compte « Compte démo IA »
-- et ~1 an de transactions réalistes pour TON utilisateur.
--
-- ⚠️ AVANT DE LANCER : remplace l'e-mail ci-dessous par le tien (ligne v_email).
-- Ré-exécutable : supprime d'abord l'ancien compte démo (et ses transactions).
-- Pour tout effacer plus tard : delete from accounts where name = 'Compte démo IA';
-- ============================================================================

do $$
declare
    v_email text := 'dev@finanssor.fr';   -- <<< REMPLACE PAR TON E-MAIL
    v_user  uuid;
    v_acc   bigint;
    c_alim bigint; c_transport bigint; c_logement bigint;
    c_loisirs bigint; c_sante bigint; c_shopping bigint; c_salaire bigint;
    m int; d date; mois int; w int;
    base date := (date_trunc('month', current_date) - interval '11 months')::date;
begin
    select id into v_user from auth.users where email = v_email limit 1;
    if v_user is null then
        raise exception 'Utilisateur introuvable pour l''e-mail %. Corrige v_email.', v_email;
    end if;

    -- Repartir propre : supprime un éventuel compte démo précédent (cascade -> transactions)
    delete from public.accounts where user_id = v_user and name = 'Compte démo IA';

    insert into public.accounts(user_id, name, balance, account_type, currency_code)
        values (v_user, 'Compte démo IA', 0, 'CHECKING', 'EUR')
        returning id into v_acc;

    -- Catégories par défaut de l'utilisateur (créées au 1er login)
    select id into c_alim      from public.categories where user_id=v_user and name='Alimentation'    limit 1;
    select id into c_transport from public.categories where user_id=v_user and name='Transport'       limit 1;
    select id into c_logement  from public.categories where user_id=v_user and name='Logement'        limit 1;
    select id into c_loisirs   from public.categories where user_id=v_user and name='Loisirs'         limit 1;
    select id into c_sante     from public.categories where user_id=v_user and name='Santé'           limit 1;
    select id into c_shopping  from public.categories where user_id=v_user and name='Shopping'        limit 1;
    select id into c_salaire   from public.categories where user_id=v_user and name='Salaire'         limit 1;

    for m in 0..11 loop
        d := (base + (m || ' months')::interval)::date;
        mois := extract(month from d)::int;

        -- Revenu : salaire mensuel (le 1er)
        insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
            values (v_user, v_acc, c_salaire, 'Salaire', 2500, 'INCOME', d::timestamptz);

        -- Loyer (le 5)
        insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
            values (v_user, v_acc, c_logement, 'Loyer', 800, 'EXPENSE', (d + interval '4 days')::timestamptz);

        -- Courses hebdo (4x/mois), montant qui varie un peu
        for w in 0..3 loop
            insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
                values (v_user, v_acc, c_alim, 'Courses', 70 + (w*7) % 25, 'EXPENSE', (d + ((3 + w*7) || ' days')::interval)::timestamptz);
        end loop;

        -- Transport (le 10)
        insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
            values (v_user, v_acc, c_transport, 'Carburant', 60, 'EXPENSE', (d + interval '9 days')::timestamptz);

        -- Loisirs de base (le 15)
        insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
            values (v_user, v_acc, c_loisirs, 'Sorties', 40, 'EXPENSE', (d + interval '14 days')::timestamptz);

        -- Santé un mois sur deux
        if m % 2 = 0 then
            insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
                values (v_user, v_acc, c_sante, 'Pharmacie', 30, 'EXPENSE', (d + interval '20 days')::timestamptz);
        end if;

        -- ===== SAISONNALITÉ (les pics à détecter) =====
        -- Assurance auto : une fois par an, en MARS
        if mois = 3 then
            insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
                values (v_user, v_acc, c_transport, 'Assurance auto (annuelle)', 720, 'EXPENSE', (d + interval '11 days')::timestamptz);
        end if;
        -- Vacances d'été : juillet + août
        if mois in (7, 8) then
            insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
                values (v_user, v_acc, c_loisirs, 'Vacances', 650, 'EXPENSE', (d + interval '18 days')::timestamptz);
        end if;
        -- Cadeaux de Noël : décembre
        if mois = 12 then
            insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
                values (v_user, v_acc, c_shopping, 'Cadeaux de Noël', 450, 'EXPENSE', (d + interval '15 days')::timestamptz);
        end if;
        -- Impôts : septembre
        if mois = 9 then
            insert into public.transactions(user_id, account_id, category_id, title, amount, transaction_type, date)
                values (v_user, v_acc, c_logement, 'Taxe habitation', 500, 'EXPENSE', (d + interval '12 days')::timestamptz);
        end if;
    end loop;

    -- Recalcule le solde du compte démo
    update public.accounts set balance = (
        select coalesce(sum(case transaction_type
            when 'INCOME' then amount when 'EXPENSE' then -amount else 0 end), 0)
        from public.transactions where account_id = v_acc
    ) where id = v_acc;

    raise notice 'Données de démo créées pour % (compte id=%)', v_email, v_acc;
end $$;
