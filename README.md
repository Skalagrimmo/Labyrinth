# Netcrawler

A cyberpunk roguelike Android game built with Kotlin, Jetpack Compose, Room DB, and OpenGL ES.

Navigate procedurally generated corporate intranets, hack security nodes, engage in turn-based combat, and customize your netrunner with cyberware implants.

## Features

- **7 Netrunner Classes** — Code Slasher, Street Samurai, Techie, Netrunner, Cyber Shield, Script Kiddie, Buffer Overflow
- **Turn-based Combat** — Attack, defend, hack, scan, and use programs against corporate ICE
- **3 Boss Encounters** — Multi-phase bosses with unique AI guarding each zone portal
- **Procedural Levels** — Seed-based maze generation across buildings, collector tunnels, and city sectors
- **Hacking Mini-games** — Pattern-matching breach protocols on terminals and data stores
- **Cyberware Implant System** — Install and upgrade implants across 8 body slots
- **3D First-Person View** — Real-time perspective rendering with weather effects
- **Offline-first** — 100% offline gameplay, no network required
- **Full Save/Load** — Dual-layer persistence via Room DB + SharedPreferences, with portable save export/import

## Documentation

Full project documentation lives in the [`docs/`](docs/) folder:

| Document | Contents |
|----------|----------|
| [Overview](docs/OVERVIEW.md) | What Netcrawler is, features, tech stack |
| [Architecture](docs/ARCHITECTURE.md) | Manager pattern, data flow, code structure |
| [Gameplay](docs/GAMEPLAY.md) | Mechanics, systems, bosses, items, terminals |
| [Codebase](docs/CODEBASE.md) | Source-file reference for every layer |
| [Data & Persistence](docs/DATA-PERSISTENCE.md) | Room schema, saves, export/import format |
| [Modding](docs/MODDING.md) | Add enemies/items/programs via Markdown (no Kotlin) |
| [Contributing](docs/CONTRIBUTING.md) | Build, test, and contribution guidelines |

See also the **[Improvement Plan](IMPROVEMENT_PLAN.md)** and **[Changelog](CHANGELOG.md)**.

## Development & CI

- **Testing:** unit tests live in `app/src/test/java/...` (`./gradlew testDebugUnitTest`).
- **CI:** `.github/workflows/ci.yml` runs unit tests, lint, and debug builds on push/PR.
  A committed Gradle wrapper is required (generate once in Android Studio with
  `gradle wrapper`).

## Building

### Prerequisites

- Android Studio Ladybug (2024.2.1) or later
- JDK 11+
- Android SDK 36

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/Labyrinth-main.git
   cd Labyrinth-main
   ```

2. Open in Android Studio and let Gradle sync.

3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on a connected device or emulator:
   ```bash
   ./gradlew installDebug
   ```

> **Note:** No `google-services.json` or API keys are required. The game is fully offline.

## Project Structure

```
app/src/main/java/com/example/
├── ui/                          # ViewModels and Managers
│   ├── GameViewModel.kt         # Thin coordinator (~500 lines)
│   ├── CombatManager.kt         # Turn-based combat, bosses, loot
│   ├── ExplorationManager.kt    # Movement, weather, level navigation, SVDAG
│   ├── InventoryManager.kt      # Items, equipment, shop, cyberware/clinic
│   ├── PersistenceManager.kt    # Save/load, serialization, export/import
│   ├── CosmeticVaultManager.kt  # Themes, data fragments, buffs
│   ├── ActiveScreen.kt          # Screen navigation enum
│   ├── CombatTurn.kt            # Combat turn state enum
│   └── CombatHackingPatternState.kt  # Combat hacking UI state
├── ui/screens/                  # Compose UI screens
├── ui/components/               # Reusable composables
├── data/                        # Room DB, entities, DAOs, models
│   ├── GameDatabase.kt          # Room database (10 entities)
│   ├── GameModels.kt            # Core data types
│   ├── GameEngine.kt            # Procedural generation + bosses
│   ├── EnemyCombatAIScript.kt   # Enemy & boss AI
│   ├── CombatLootDropSystem.kt  # Loot tables
│   └── ...                      # Registries, floor maps, grid, player
├── data/svdag/                  # Sparse Voxel DAG 3D world system
├── audio/                       # PCM music synthesis + sound pools + haptics
├── gl/                          # OpenGL ES matrix/character renderers
└── assets/shaders/              # GLSL shaders

docs/                           # Full project documentation (see table above)
```

## Architecture

- **UI Layer:** Jetpack Compose screens with Material 3
- **State:** Single `GameUiState` data class in `GameViewModel`, delegating to focused managers
- **Persistence:** Room SQLite database with SharedPreferences fallback
- **Rendering:** Canvas 2D for perspective, OpenGL ES for matrix/character views
- **Audio:** PCM synthesis via AudioTrack (no audio files)

## License

MIT License — see [LICENSE](LICENSE) for details.
