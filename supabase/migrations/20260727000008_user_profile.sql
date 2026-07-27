-- ============================================================================
-- BudgetManager — Profil utilisateur persistant (Agent Constructeur, Phase 5)
--   Une ligne par utilisateur : ce que l'IA doit savoir pour bâtir le budget
--   idéal (priorités, projets, ce qu'il ne coupe jamais, confort, revenus).
--   Éditable par l'utilisateur ; alimenté par l'onboarding guidé puis affiné.
-- Ré-exécutable (IF NOT EXISTS / DROP IF EXISTS).
-- ============================================================================

create table if not exists public.user_profiles (
    -- user_id EST la clé primaire : garantit une seule ligne de profil par utilisateur
    user_id        uuid primary key default auth.uid() references auth.users(id) on delete cascade,
    -- Revenu mensuel net habituel (optionnel ; sert de base au budget)
    monthly_income numeric(15, 2),
    -- Champs libres remplis pendant l'accueil guidé, puis éditables
    priorities     text,   -- ce qui compte le plus pour lui
    projects       text,   -- projets à venir (voyage, achat, etc.)
    never_cut      text,   -- dépenses qu'il refuse de réduire
    comfort        text,   -- niveau de confort / train de vie souhaité
    notes          text,   -- notes libres supplémentaires
    -- L'accueil guidé a-t-il déjà été fait ?
    onboarding_done boolean not null default false,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- updated_at auto
drop trigger if exists trg_user_profiles_updated on public.user_profiles;
create trigger trg_user_profiles_updated before update on public.user_profiles
    for each row execute function public.set_updated_at();

-- RLS : propriétaire uniquement
alter table public.user_profiles enable row level security;
drop policy if exists owner_all on public.user_profiles;
create policy owner_all on public.user_profiles
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());
