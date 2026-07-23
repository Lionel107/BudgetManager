-- ============================================================================
-- BudgetManager — Row-Level Security (isolation stricte par utilisateur)
--
-- Principe : chaque table porte user_id. On active la RLS et on ajoute une
-- politique "for all" qui n'autorise QUE les lignes appartenant à l'utilisateur
-- courant (auth.uid() = l'id extrait du JWT).
--   • USING       -> lignes visibles en SELECT/UPDATE/DELETE
--   • WITH CHECK  -> lignes autorisées en INSERT/UPDATE
--
-- Combiné au défaut `user_id default auth.uid()` du schéma, un client peut
-- insérer sans préciser user_id : il est rempli automatiquement et validé ici.
--
-- ⚠️ La clé "service_role" (backend/migrations) CONTOURNE la RLS par design.
--    Ne jamais l'exposer côté client. Le client utilise uniquement la anon key.
-- ============================================================================

-- 0. Nettoyage des politiques existantes (ré-exécutable)
drop policy if exists owner_all on public.accounts;
drop policy if exists owner_all on public.categories;
drop policy if exists owner_all on public.transactions;
drop policy if exists owner_all on public.recurring_transactions;
drop policy if exists owner_all on public.templates;
drop policy if exists owner_all on public.tags;
drop policy if exists owner_all on public.transaction_tags;
drop policy if exists owner_all on public.transaction_splits;
drop policy if exists owner_all on public.exchange_rates;
drop policy if exists owner_all on public.challenges;
drop policy if exists owner_all on public.budgets;

-- 1. Activer la RLS sur toutes les tables
alter table public.accounts               enable row level security;
alter table public.categories             enable row level security;
alter table public.transactions           enable row level security;
alter table public.recurring_transactions enable row level security;
alter table public.templates              enable row level security;
alter table public.tags                   enable row level security;
alter table public.transaction_tags       enable row level security;
alter table public.transaction_splits     enable row level security;
alter table public.exchange_rates         enable row level security;
alter table public.challenges             enable row level security;
alter table public.budgets                enable row level security;

-- 2. Politiques "propriétaire seulement" (une par table)
create policy owner_all on public.accounts
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.categories
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.transactions
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.recurring_transactions
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.templates
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.tags
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.transaction_tags
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.transaction_splits
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.exchange_rates
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.challenges
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy owner_all on public.budgets
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());
