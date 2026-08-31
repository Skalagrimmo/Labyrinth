# Changelog

All notable changes to Netcrawler are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- **17 enemy archetypes** across 4 tiers (Building → City) with unique stat profiles,
  starting status effects, and ASCII portraits; depth-weighted spawner in `GameEngine`.
- **3 multi-phase bosses** (Firewall Sentinel, Daemon Overlord, Black ICE Colossus) with
  per-phase AI, boss loot, and auto zone transitions on victory.
- **Markdown modding system** (`ContentModParser` + `ContentRegistry`): add enemies,
  items, and programs via `.md` files in `assets/mods/` — no Kotlin needed. See
  `docs/MODDING.md`.
- **Skill tree / progression** (`SkillTreeModels` + `SkillTreeManager`): 3 branches
  (Hacking, Combat, Engineering) with prerequisite-linked nodes granting permanent
  stat bonuses / one-time credits; spend via `skill learn <BRANCH> <#>`; +1 skill
  point per level; persisted across saves.
- **Random runner-name generator** (`NameGenerator` + `[ SURGE_ALIAS ]` button and
  suggestions on character creation).
- **Onboarding tutorial** (`TutorialOverlay`): 5-step guided hints for new players
  (movement, hacking, combat, cyberware) with `tutorial next` / `tutorial skip`
  terminal commands and persisted `tutorial_seen` flag.
- **Unit tests** for `ContentModParser`, `GameEngine`, `NameGenerator`, and `SkillTree`.
- **Gradle wrapper** committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) pinned to Gradle
  9.3.1 (satisfies AGP 9.1.1; also unblocks the CI workflow).
- **Database migration strategy** (item 1.3): enabled Room `exportSchema = true` +
  `room.schemaLocation` KSP arg (`app/schemas/`) and documented the migration workflow so
  future schema changes can be authored + tested with `MigrationTestHelper`.
- **Fixed pre-existing build breaker** in `data/EnemyCombatAIScript.kt`: the boss-AI block
  (`evaluateBossAction`/sentinel/overlord/colossus) was stray top-level code with a
  duplicate `object EnemyCombatAIScript`; merged into a single object so the file compiles.
- **Fixed** `SkillTreeModels.combinedEffects` (accumulated `+=` on `val` data-class fields → local vars).
- **GitHub Actions CI** workflow (unit tests → lint → assemble debug APK). *Requires a
  committed Gradle wrapper to run.*
- Project documentation (`docs/`): Overview, Architecture, Gameplay, Codebase,
  Data & Persistence, Contributing, Modding.

### Changed
- Refactored `GameViewModel` from ~4,752 lines down to ~520 by extracting focused
  managers (`CombatManager`, `ExplorationManager`, `InventoryManager`,
  `PersistenceManager`, `CosmeticVaultManager`) and shared type files.
- Removed real-time combat; turn-based combat is now the only combat mode.
- Updated `.gitignore` (`*.jks`, `*.apk`, `*.aab`, `*.hprof`, `google-services.json`).
- Removed dead dependencies (Firebase, Retrofit, OkHttp, Moshi, logging-interceptor).

## [0.1.0] - Initial release

- 7 netrunner classes, procedural maze generation, hacking mini-games, cyberware
  implants, turn-based combat, save/load with portable export/import.
