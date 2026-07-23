# Supabase — Backend BudgetManager

Socle de la synchro multi-appareils et multi-utilisateur (voir le plan global dans le CLAUDE.md racine). Chaque utilisateur est **100 % isolé** : il ne voit que ses propres données, garanti par la Row-Level Security (RLS) de Postgres.

## Contenu

| Fichier | Rôle |
|---|---|
| `migrations/20260722000001_init_schema.sql` | Tables, clés étrangères, index, triggers `updated_at` |
| `migrations/20260722000002_rls_policies.sql` | Activation RLS + politiques « propriétaire uniquement » |
| `migrations/20260722000003_default_categories.sql` | Trigger qui crée les catégories par défaut à l'inscription |

## Choix d'architecture

- **Clés primaires `uuid`** (`gen_random_uuid()`) plutôt que des entiers auto-incrémentés : sûr pour la synchro multi-appareils et un futur mode hors-ligne (génération d'ID côté client sans collision).
- **`user_id` sur chaque table**, avec `default auth.uid()` : rempli automatiquement depuis le JWT à l'insertion, donc le client n'a pas à le préciser.
- **Enums en `text` + `CHECK`** : souple (ajout de valeurs sans `ALTER TYPE`) et auto-documenté.
- **`updated_at` auto-maintenu** par trigger : prépare la synchro incrémentale (« donne-moi ce qui a changé depuis X »).
- **Tags normalisés uniquement** : l'ancienne colonne CSV `transactions.tags` du SQLite local n'est pas reprise ; la migration de données lira le CSV pour peupler `tags` / `transaction_tags`.

## Prérequis (à faire par toi)

1. Créer un compte sur https://supabase.com (offre gratuite suffisante).
2. Créer un nouveau projet (choisir une région proche, ex. *West EU (Paris)*).
3. Noter, dans **Project Settings → API** :
   - **Project URL** (ex. `https://xxxx.supabase.co`)
   - **anon public key** → utilisée par les clients (Desktop, PWA). Publique par design, protégée par la RLS.
   - **service_role key** → ⚠️ SECRÈTE. Uniquement pour les migrations/backend. **Ne jamais** la mettre dans le client ni la committer.

## Appliquer les migrations

### Option A — Éditeur SQL (le plus simple pour démarrer)

Dans le dashboard Supabase → **SQL Editor** → exécuter les 3 fichiers **dans l'ordre** :
`20260722000001` → `20260722000002` → `20260722000003`.

### Option B — Supabase CLI (recommandé à terme, versionné)

```bash
# Installer la CLI : https://supabase.com/docs/guides/cli
supabase login
supabase link --project-ref <ref-du-projet>   # le ref est dans l'URL du projet
supabase db push                               # applique migrations/*.sql
```

## Vérifier que la RLS fonctionne

Après avoir créé un utilisateur (via l'app ou l'onglet **Authentication**), dans le SQL Editor :

```sql
-- Doit renvoyer 10 (7 dépenses + 3 revenus) pour l'utilisateur fraîchement créé
select count(*) from public.categories;

-- Toutes les tables doivent avoir rowsecurity = true
select tablename, rowsecurity from pg_tables where schemaname = 'public';
```

## Sécurité — règles à ne jamais enfreindre

- La **service_role key** contourne la RLS : backend/migrations uniquement, jamais côté client, jamais commitée.
- Les clients utilisent **exclusivement la anon key** + un utilisateur authentifié.
- Les clés d'API IA (Gemini/Claude) resteront **côté serveur** (Edge Functions, Phase 4) — aujourd'hui la clé Gemini est exposée côté client, ce sera corrigé.

## Suite

- **Phase 2** : brancher le Desktop via `supabase-kt` (auth + synchro) et migrer les données locales existantes (`~/.budgetmanager/budget_manager.db`) — un script de migration remappera les ID entiers SQLite vers les UUID Postgres.
- **Phase 3** : PWA de saisie rapide.
- **Phase 4** : Edge Functions pour l'IA (analyse de profil, budget annuel, saisonnalité).
