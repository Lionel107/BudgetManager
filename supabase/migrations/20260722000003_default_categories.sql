-- ============================================================================
-- BudgetManager — Catégories par défaut à l'inscription
--
-- Réplique CategoryRepository.createDefaultCategories() du client Desktop :
-- à chaque nouvel utilisateur (INSERT dans auth.users), on insère les mêmes
-- 7 catégories de dépense + 3 de revenu. Ainsi la PWA comme le Desktop
-- démarrent avec le même jeu de catégories, sans logique côté client.
-- ============================================================================

create or replace function public.seed_default_categories()
returns trigger
language plpgsql
security definer            -- s'exécute avec les droits du propriétaire (contourne la RLS)
set search_path = public
as $$
begin
    -- Dépenses
    insert into public.categories (user_id, name, category_type, color, icon_name, is_default, display_order) values
        (new.id, 'Alimentation', 'EXPENSE', '#4CAF50', 'restaurant',       true, 0),
        (new.id, 'Transport',    'EXPENSE', '#2196F3', 'directions_car',   true, 1),
        (new.id, 'Logement',     'EXPENSE', '#FF9800', 'home',             true, 2),
        (new.id, 'Loisirs',      'EXPENSE', '#9C27B0', 'sports_esports',   true, 3),
        (new.id, 'Santé',        'EXPENSE', '#F44336', 'local_hospital',   true, 4),
        (new.id, 'Shopping',     'EXPENSE', '#E91E63', 'shopping_bag',     true, 5),
        (new.id, 'Éducation',    'EXPENSE', '#00BCD4', 'school',           true, 6);
    -- Revenus (display_order continue après les 7 dépenses)
    insert into public.categories (user_id, name, category_type, color, icon_name, is_default, display_order) values
        (new.id, 'Salaire',        'INCOME', '#4CAF50', 'account_balance', true, 7),
        (new.id, 'Freelance',      'INCOME', '#FF9800', 'work',            true, 8),
        (new.id, 'Investissements','INCOME', '#2196F3', 'trending_up',     true, 9);
    return new;
end;
$$;

-- Déclencheur sur la création d'un compte utilisateur Supabase
create trigger trg_seed_default_categories
    after insert on auth.users
    for each row execute function public.seed_default_categories();
