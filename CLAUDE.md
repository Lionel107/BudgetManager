# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application
./gradlew run

# Build native distributions (.exe, .msi) — Windows only, targets Exe + Msi
./gradlew packageExe
./gradlew packageMsi

# Clean build
./gradlew clean build
```

There are no tests in this project (no `src/test` directory and no test dependencies). `./gradlew test` will pass trivially.

App version is `2.0.0` (see `build.gradle.kts`). The MSI installer uses a fixed `upgradeUuid`, so bumping `packageVersion` upgrades existing installs in place.

## Tech Stack

- **Language**: Kotlin 1.9.23 (JVM toolchain Java 21)
- **UI**: Compose for Desktop 1.6.1 with Material 3 and custom neumorphic styling
- **Database**: SQLite via Jetbrains Exposed ORM 0.46.0 (stored at `~/.budgetmanager/budget_manager.db`)
- **Async**: Kotlin Coroutines 1.8.0 with Flow
- **DI**: Koin 3.5.3
- **Build**: Gradle 8.9 with Kotlin DSL

## Architecture

Three-layer architecture under `src/main/kotlin/com/budgetmanager/`:

- **`data/`** — `database/` (Exposed tables + `DatabaseManager` + `Migrations`), `repository/` (Flow-based CRUD), `preferences/` (Java Preferences API)
- **`domain/model/`** — Data classes (`DomainModels.kt`) and enums (`Enums.kt`) shared across layers
- **`presentation/`** — Compose screens, navigation, theme, and reusable components
- **`di/`** — Koin module wiring (`AppModule.kt`)
- **`util/`** — Date/currency formatting (French locale), CSV/HTML export, CSV import, backup, advice/badge engines, Gemini AI integration, logging

## Key Patterns

**Repository + Flow**: Each repository exposes `Flow<List<T>>` driven by a private `_refreshTrigger` MutableStateFlow. Database writes call `refresh()`/`refreshData()` to push updates to collectors. All DB operations run on `Dispatchers.IO` and call Exposed's `transaction { }` directly (no DAO layer).

**Screen-level state, no ViewModel**: Screens collect repository Flows directly via Koin's `get()`. Some screens use dedicated state classes (e.g. `HomeScreenState`) that hold their own `CoroutineScope` — see the leak caveat below.

**Navigation**: `Screen` enum in `NavigationState.kt` defines all screens (HOME, ACCOUNTS, TRANSACTIONS, ADD_TRANSACTION, BUDGETS, ANALYTICS, RECURRING, ADD_RECURRING, TRANSFER, CATEGORIES, TEMPLATES, CHALLENGES, BADGES, EXCHANGE_RATES, EXPORT, IMPORT, SETTINGS). `AppLayout.kt` routes on `NavigationState.currentScreen`; `Sidebar.kt` is the nav UI. `NavigationState` also carries edit/context params (`editTransactionId`, `editRecurringId`, `fromTemplateId`, `openAddAccountDialog`).

## Database Migrations (`data/database/Migrations.kt`)

The project **has a versioned migration system** — do not rely on `SchemaUtils.create()` alone for schema changes. `DatabaseManager.init()` calls `Migrations.runAll()`, which records applied versions in a `schema_version` table and runs only the missing ones (idempotent, safe on every startup).

**To evolve the schema:**
1. Bump `Migrations.CURRENT_VERSION`.
2. Add a `when` branch in `applyMigration()` and a `migrationVN_*()` function.
3. New tables: `SchemaUtils.create(NewTable)`. New columns: raw `ALTER TABLE ... ADD COLUMN`, guarded by the `columnExists()` helper (SQLite has no `ADD COLUMN IF NOT EXISTS`).

History (V1–V8): initial schema, category soft-delete, normalized tags (migrating a legacy CSV `tags` column), transaction splits, investment fields, recurring-transfer destination, challenges, exchange rates.

## Startup Flow (Main.kt)

1. Global uncaught-exception handler → writes stacktrace to `~/Desktop/budgetmanager_error.log` + `AppLogger`
2. Koin DI init → `DatabaseManager.init()` runs migrations
3. Default categories inserted if first run
4. Daily backup (`BackupService.runDailyBackup()`, at most once/day → `~/.budgetmanager/backups/`)
5. Pending recurring transactions processed, then a background scope re-processes every 30 min (handles the app left open across midnight)
6. A second background scope populates notifications from `AdviceEngine` on startup and hourly
7. Theme/density/font-scale preferences loaded (incl. optional auto-evening dark mode)
8. Compose window launched (1280×800)

## Theming

There are **four full themes**: `LightNeumorphicColors`, `DarkNeumorphicColors`, `BlueNeumorphicColors`, `RoseNeumorphicColors` (Kawaii) in `theme/Color.kt`, each a `NeumorphicColors` data class. The active theme is provided via the `LocalNeumorphicColors` CompositionLocal; read colors through `AppColors.current` (or the legacy `Neumorphic*` accessor vals, which resolve to the current theme). `ThemeModeState` selects the theme; `Main.kt` can auto-switch to dark in the evening.

When adding a theme-aware color, add it to the `NeumorphicColors` data class **and** to all four theme instances — a value defined in only one theme breaks the others.

## Localization

Hardcoded in French (`Locale.FRANCE`), EUR default. All UI strings, date formats, and currency are French/EUR.

## Adding Features

**New screen**: Add entry to `Screen` enum → create screen composable → add route in `AppLayout.kt` → add sidebar item in `Sidebar.kt`.

**New database entity**: Define table in `Tables.kt` → add a **migration** (see above; do NOT just edit `DatabaseManager`) → create repository → register as singleton in `AppModule.kt`.

## Export / Import

- Export (`PdfExportService`, `ExportScreen`): only **CSV and HTML** are implemented (`enum ExportFileFormat { CSV, HTML }`). The separate `domain.model.ExportFormat` enum still declares `CSV, PDF, DOCX`, but PDF/DOCX are **not implemented** — they would need extra libs (OpenPDF/PDFBox, Apache POI).
- Import (`ImportService`, `ImportScreen`): CSV only.

## Conseils concrets pour ce projet

### Fuite mémoire sur les CoroutineScope des écrans (toujours d'actualité)
`HomeScreenState` crée un `CoroutineScope(Dispatchers.Main + SupervisorJob())` manuellement et dépend d'un `DisposableEffect { onDispose { state.dispose() } }` pour l'annuler. Un nouvel écran qui suit ce pattern sans câbler `dispose()` fera fuir le scope. Envisager un `ScreenState` de base à auto-cancel ou des ViewModels Compose avec `viewModelScope`.

### État UI non partagé entre écrans
Chaque écran recrée son state à chaque navigation (`remember { HomeScreenState() }`), donc revenir sur un écran relance toutes ses requêtes DB. Remonter les states dans `NavigationState` ou un singleton Koin éviterait les rechargements.

### Fichiers de crash JVM à la racine
La racine du projet contient de nombreux `hs_err_pid*.log` / `replay_pid*.log` (dumps de crash JVM) et un fichier `nul` parasite. Ce sont des artefacts, pas du code — ne pas s'y fier ni les commiter.

### Conseil de style UI
Les composants custom (`NeumorphicCard`, `NeumorphicButton`, etc.) sont dans `components/CommonComponents.kt`. Toujours les préférer aux composants Material 3 bruts pour la cohérence visuelle. Couleurs sémantiques : vert `IncomeColor` (revenus), rouge `ExpenseColor` (dépenses), bleu `TransferColor` (transferts) — toutes issues du thème courant.

### Déjà corrigé (ne pas ré-signaler)
- **Migrations de schéma** : système versionné en place (`Migrations.kt`).
- **Dark mode** : quatre thèmes complets existent.
- **Transferts / soldes** : `updateBalance()` utilise `UPDATE ... SET balance = balance + ?` (atomique) ; `transferBetweenAccounts()` fait tout dans une seule `transaction {}`.
- **Suppression de compte** : soft-delete (`isActive = false`), plus de transactions orphelines.
- **Table `Templates`** : désormais utilisée (`TemplateRepository`, `TemplateScreen`, création de transaction depuis un modèle).
