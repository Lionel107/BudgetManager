-- ============================================================================
-- BudgetManager — Keep-alive du projet (palier gratuit Supabase)
--   Un simple SELECT anonyme (renvoyant [] à cause de la RLS) n'a PAS suffi à
--   empêcher la mise en pause (pause constatée mi-août 2026 malgré un ping tous
--   les 3 jours). On fait donc une vraie ÉCRITURE en base à chaque ping.
--   Appelée par .github/workflows/keepalive.yml via POST /rest/v1/rpc/keepalive_ping
-- Ré-exécutable (IF NOT EXISTS / OR REPLACE).
-- ============================================================================

-- Une seule ligne (id = 1), mise à jour à chaque ping
create table if not exists public.keepalive (
    id         int primary key default 1 check (id = 1),
    pinged_at  timestamptz not null default now(),
    ping_count bigint not null default 0
);

-- RLS activée sans aucune politique : table inaccessible directement aux clients,
-- seule la fonction ci-dessous (security definer) peut y écrire.
alter table public.keepalive enable row level security;

create or replace function public.keepalive_ping()
returns timestamptz
language sql
security definer
set search_path = public
as $$
    insert into public.keepalive (id, pinged_at, ping_count)
    values (1, now(), 1)
    on conflict (id) do update
        set pinged_at = now(), ping_count = public.keepalive.ping_count + 1
    returning pinged_at;
$$;

-- Ne rien exposer d'autre : seule l'exécution de la fonction est autorisée
revoke all on function public.keepalive_ping() from public;
grant execute on function public.keepalive_ping() to anon, authenticated;
