
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

- **Testing:** every module carries its own unit tests (JVM `test` / `testDebugUnitTest`); CI runs unit tests, lint and debug builds on push/PR.
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
app/                            # MainActivity (the only app source file) + resources
core/
├── model/                      # Domain models shared by every layer
├── content/                    # Item/implant catalogs, enemy archetypes, mod loading
├── engine/                     # Pure game logic: engine, combat rules, generators, stealth simulation
├── persistence/                # Pure-Kotlin save data codecs
├── render/                     # Perspective renderers + SVDAG world rules
├── game/                       # Shared UI-state contracts (GameUiState, stealth session controller, combat clocks)
├── audio/                      # GameAudioPlayer port (pure JVM)
├── platform/                   # Android persistence stack (Room, repositories, PersistenceManager) + audio/vibration adapters
└── designsystem/               # Compose theme + CRT overlay
feature/
├── skilltree/  combat/  exploration/  cosmetic/  inventory/   # Domain managers
├── hacking/                    # Hacking minigame screens + HackingViewModel
├── cybergl/                    # OpenGL character renderer
├── widgets/                    # Shared HUD widgets
├── exploration-ui/             # First-person canvas + minimap
├── svdag-inspector/            # SVDAG world inspector screens
├── terminal/                   # TerminalScreen router, exploration shell, menu screens
└── gamevm/                     # GameViewModel - the coordinator
docs/                           # Full project documentation (see table above)
```

## Architecture

- **UI Layer:** Jetpack Compose screens with Material 3
- **State:** Single `GameUiState` data class (`:core:game`); `GameViewModel` (`:feature:gamevm`) delegates to focused managers across feature modules
- **Persistence:** Room SQLite database with SharedPreferences fallback
- **Rendering:** Canvas 2D for perspective, OpenGL ES for matrix/character views
- **Audio:** PCM synthesis via AudioTrack (no audio files)

## License

MIT License — see [LICENSE](LICENSE) for details.
