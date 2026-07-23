-- ============================================================================
-- BudgetManager — Schéma initial Postgres (Supabase)
-- Traduction de src/main/kotlin/.../data/database/Tables.kt (migrations V1–V8)
-- vers un modèle multi-utilisateur, chaque utilisateur 100% isolé.
--
-- Choix d'architecture :
--   • Clés primaires UUID (gen_random_uuid) — sûr pour la synchro multi-appareils
--     et pour un futur mode hors-ligne (génération d'ID côté client sans collision).
--   • Chaque table porte user_id -> auth.users(id), défaut = auth.uid() (rempli
--     automatiquement depuis le JWT à l'insertion). L'isolation est appliquée par
--     la RLS (voir 20260722000002_rls_policies.sql).
--   • Enums stockés en TEXT + contrainte CHECK (souple : ajout de valeurs sans
--     ALTER TYPE, et documente les valeurs valides).
--   • updated_at auto-maintenu par trigger — utile pour la synchro incrémentale.
--   • L'ancienne colonne CSV transactions.tags est ABANDONNÉE au profit des tables
--     normalisées tags / transaction_tags (la migration de données lira le CSV).
-- ============================================================================

-- gen_random_uuid() est fourni par l'extension pgcrypto (dispo par défaut sur Supabase)
create extension if not exists pgcrypto;

-- ----------------------------------------------------------------------------
-- Fonction utilitaire : met à jour updated_at à chaque UPDATE
-- ----------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

-- ============================================================================
-- ACCOUNTS
-- ============================================================================
create table public.accounts (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name            varchar(255) not null,
    balance         numeric(15, 2) not null default 0,
    account_type    text not null check (account_type in ('CHECKING','SAVINGS','CASH','CREDIT_CARD','INVESTMENT')),
    currency_code   varchar(10) not null default 'EUR',
    is_active       boolean not null default true,
    display_order   integer not null default 0,
    color           varchar(20),
    icon_name       varchar(100),
    -- Champs investissement (uniquement pour account_type = INVESTMENT)
    initial_capital numeric(15, 2),
    tax_rate        real not null default 0.30,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index accounts_user_idx on public.accounts (user_id);

-- ============================================================================
-- CATEGORIES (hiérarchiques via parent_id auto-référent)
-- ============================================================================
create table public.categories (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name          varchar(255) not null,
    category_type text not null check (category_type in ('INCOME','EXPENSE')),
    parent_id     uuid references public.categories(id) on delete set null,
    color         varchar(20) not null,
    icon_name     varchar(100),
    is_default    boolean not null default false,
    display_order integer not null default 0,
    is_active     boolean not null default true,  -- soft-delete
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index categories_user_idx on public.categories (user_id);
create index categories_parent_idx on public.categories (parent_id);

-- ============================================================================
-- TRANSACTIONS
-- ============================================================================
create table public.transactions (
    id                       uuid primary key default gen_random_uuid(),
    user_id                  uuid not null default auth.uid() references auth.users(id) on delete cascade,
    account_id               uuid not null references public.accounts(id) on delete cascade,
    category_id              uuid references public.categories(id) on delete set null,
    title                    varchar(500) not null,
    amount                   numeric(15, 2) not null,
    transaction_type         text not null check (transaction_type in ('INCOME','EXPENSE','TRANSFER')),
    date                     timestamptz not null,
    notes                    text,
    is_recurring             boolean not null default false,
    recurring_transaction_id uuid,  -- FK ajoutée après création de recurring_transactions
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now()
);
create index transactions_user_date_idx on public.transactions (user_id, date desc);
create index transactions_account_idx on public.transactions (account_id);
create index transactions_category_idx on public.transactions (category_id);

-- ============================================================================
-- RECURRING_TRANSACTIONS
-- ============================================================================
create table public.recurring_transactions (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null default auth.uid() references auth.users(id) on delete cascade,
    title                  varchar(500) not null,
    amount                 numeric(15, 2) not null,
    category_id            uuid references public.categories(id) on delete set null,
    account_id             uuid not null references public.accounts(id) on delete cascade,
    frequency_type         text not null check (frequency_type in ('DAILY','WEEKLY','BI_WEEKLY','MONTHLY','QUARTERLY','YEARLY')),
    repeat_interval        integer not null default 1,
    start_date             date not null,
    end_date               date,
    last_generated_date    date,
    next_due_date          date not null,
    transaction_type       text not null check (transaction_type in ('INCOME','EXPENSE','TRANSFER')),
    is_active              boolean not null default true,
    notes                  text,
    -- Uniquement pour les récurrences de type TRANSFER : compte destination
    destination_account_id uuid references public.accounts(id) on delete set null,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now()
);
create index recurring_user_idx on public.recurring_transactions (user_id);
create index recurring_next_due_idx on public.recurring_transactions (user_id, next_due_date);

-- FK différée : transactions.recurring_transaction_id -> recurring_transactions.id
alter table public.transactions
    add constraint transactions_recurring_fk
    foreign key (recurring_transaction_id)
    references public.recurring_transactions(id) on delete set null;

-- ============================================================================
-- TEMPLATES
-- ============================================================================
create table public.templates (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name             varchar(255) not null,
    default_amount   numeric(15, 2),
    category_id      uuid references public.categories(id) on delete set null,
    transaction_type text not null check (transaction_type in ('INCOME','EXPENSE','TRANSFER')),
    icon_name        varchar(100),
    color            varchar(20),
    display_order    integer not null default 0,
    usage_count      integer not null default 0,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
create index templates_user_idx on public.templates (user_id);

-- ============================================================================
-- TAGS (normalisés) + TRANSACTION_TAGS (relation N–N)
-- ============================================================================
create table public.tags (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name        varchar(100) not null,
    color       varchar(20),
    usage_count integer not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (user_id, name)  -- un même tag n'existe qu'une fois par utilisateur
);
create index tags_user_idx on public.tags (user_id);

create table public.transaction_tags (
    user_id        uuid not null default auth.uid() references auth.users(id) on delete cascade,
    transaction_id uuid not null references public.transactions(id) on delete cascade,
    tag_id         uuid not null references public.tags(id) on delete cascade,
    primary key (transaction_id, tag_id)
);
create index transaction_tags_user_idx on public.transaction_tags (user_id);
create index transaction_tags_tag_idx on public.transaction_tags (tag_id);

-- ============================================================================
-- TRANSACTION_SPLITS (ventilation d'une transaction sur plusieurs catégories)
-- ============================================================================
create table public.transaction_splits (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null default auth.uid() references auth.users(id) on delete cascade,
    transaction_id uuid not null references public.transactions(id) on delete cascade,
    category_id    uuid references public.categories(id) on delete set null,
    amount         numeric(15, 2) not null,
    notes          text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);
create index transaction_splits_user_idx on public.transaction_splits (user_id);
create index transaction_splits_txn_idx on public.transaction_splits (transaction_id);

-- ============================================================================
-- EXCHANGE_RATES
-- ============================================================================
create table public.exchange_rates (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null default auth.uid() references auth.users(id) on delete cascade,
    from_currency varchar(10) not null,  -- ISO 4217 (EUR, USD, ...)
    to_currency   varchar(10) not null,
    rate          numeric(18, 8) not null,  -- 1 from_currency = rate to_currency
    updated_at    timestamptz not null default now(),
    unique (user_id, from_currency, to_currency)
);
create index exchange_rates_user_idx on public.exchange_rates (user_id);

-- ============================================================================
-- CHALLENGES
-- ============================================================================
create table public.challenges (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null default auth.uid() references auth.users(id) on delete cascade,
    title         varchar(255) not null,
    description   text,
    type          text not null check (type in ('SPEND_LIMIT','SAVE_AMOUNT')),
    target_amount numeric(15, 2) not null,
    category_id   uuid references public.categories(id) on delete set null,
    start_date    date not null,
    end_date      date not null,
    is_completed  boolean not null default false,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index challenges_user_idx on public.challenges (user_id);

-- ============================================================================
-- BUDGETS
-- ============================================================================
create table public.budgets (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null default auth.uid() references auth.users(id) on delete cascade,
    category_id       uuid not null references public.categories(id) on delete cascade,
    period_type       text not null check (period_type in ('WEEKLY','MONTHLY','YEARLY','CUSTOM')),
    budget_limit      numeric(15, 2) not null,
    alert_threshold   real not null default 0.9,
    warning_threshold real not null default 0.7,
    start_date        date,
    end_date          date,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
create index budgets_user_idx on public.budgets (user_id);
create index budgets_category_idx on public.budgets (category_id);

-- ============================================================================
-- Triggers updated_at (sur toutes les tables ayant la colonne)
-- ============================================================================
create trigger trg_accounts_updated               before update on public.accounts               for each row execute function public.set_updated_at();
create trigger trg_categories_updated             before update on public.categories             for each row execute function public.set_updated_at();
create trigger trg_transactions_updated           before update on public.transactions           for each row execute function public.set_updated_at();
create trigger trg_recurring_updated              before update on public.recurring_transactions for each row execute function public.set_updated_at();
create trigger trg_templates_updated              before update on public.templates              for each row execute function public.set_updated_at();
create trigger trg_tags_updated                   before update on public.tags                   for each row execute function public.set_updated_at();
create trigger trg_transaction_splits_updated     before update on public.transaction_splits     for each row execute function public.set_updated_at();
create trigger trg_exchange_rates_updated         before update on public.exchange_rates         for each row execute function public.set_updated_at();
create trigger trg_challenges_updated             before update on public.challenges             for each row execute function public.set_updated_at();
create trigger trg_budgets_updated                before update on public.budgets                for each row execute function public.set_updated_at();
