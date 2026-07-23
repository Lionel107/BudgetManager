-- ============================================================================
-- BudgetManager — Fonctions SQL pour les opérations monétaires atomiques
--
-- Postgrest ne sait pas faire un `UPDATE ... SET balance = balance + ?` ni
-- plusieurs écritures dans une même transaction depuis le client. On expose
-- donc des fonctions RPC. Elles s'exécutent en SECURITY INVOKER (droits de
-- l'appelant) → la RLS s'applique : impossible de toucher les comptes d'autrui.
-- Une fonction plpgsql s'exécute dans une seule transaction (atomique).
-- ============================================================================

-- Incrémente (ou décrémente si négatif) le solde d'un compte, atomiquement.
create or replace function public.increment_account_balance(
    p_account_id bigint,
    p_delta numeric
)
returns void
language sql
security invoker
as $$
    update public.accounts
    set balance = balance + p_delta
    where id = p_account_id;
$$;

-- Transfert entre deux comptes : débit + crédit + 2 transactions, en une transaction.
create or replace function public.transfer_between_accounts(
    p_from_id bigint,
    p_to_id bigint,
    p_amount numeric,
    p_notes text default null
)
returns void
language plpgsql
security invoker
as $$
begin
    update public.accounts set balance = balance - p_amount where id = p_from_id;
    update public.accounts set balance = balance + p_amount where id = p_to_id;

    insert into public.transactions (account_id, title, amount, transaction_type, date, notes)
        values (p_from_id, 'Transfert sortant', p_amount, 'TRANSFER', now(), p_notes);
    insert into public.transactions (account_id, title, amount, transaction_type, date, notes)
        values (p_to_id, 'Transfert entrant', p_amount, 'TRANSFER', now(), p_notes);
end;
$$;
