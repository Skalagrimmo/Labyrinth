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
- **Unit tests** for `ContentModParser` and `GameEngine` content generation.
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
