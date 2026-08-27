# Netcrawler — Contributing

Guidelines for building, testing, and contributing to Netcrawler.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Building the Project](#building-the-project)
- [Running Tests](#running-tests)
- [Project Layout](#project-layout)
- [Coding Conventions](#coding-conventions)
- [Working on the Codebase](#working-on-the-codebase)
- [Adding Content](#adding-content)
- [Releasing / Signing](#releasing--signing)
- [Reporting Issues](#reporting-issues)

---

## Prerequisites

- **Android Studio** Ladybug (2024.2.1) or later (with Kotlin 2.2 and Gradle support)
- **JDK 11+**
- **Android SDK Platform 36** (`compileSdk 36`)
- A device/emulator running **API 24+** (minSdk)

> **No API keys or `google-services.json` are required.** The game is fully offline. The build uses the Secrets Gradle plugin (`.env`/`.env.example`) but the game does not consume any secrets at runtime.

---

## Building the Project

### Option A — Android Studio (recommended)

1. `File → Open →` select the repo root (`Labyrinth-main`).
2. Let Gradle sync complete (this downloads AGP 9.1.1, Kotlin 2.2.10, Room 2.7.0, Compose BOM, etc.).
3. `Build → Make Project` or `Run ▶` on an emulator/device.

### Option B — Command line

Requires the Gradle wrapper (or a local Gradle 9.x compatible with AGP 9.1.1):

```bash
./gradlew assembleDebug     # build debug APK
./gradlew installDebug      # install on connected device
./gradlew assembleRelease   # release APK (needs signing config)
```

> **Note:** The repo currently does **not** ship a `gradle/wrapper/gradle-wrapper.jar`. If it's missing, generate it from Android Studio, or open the project in Android Studio and use its bundled Gradle.

---

## Running Tests

```bash
./gradlew testDebugUnitTest      # local JVM unit tests (JUnit4, Robolectric, Roborazzi)
./gradlew connectedDebugAndroidTest  # instrumented tests on a device/emulator
./gradlew lint                   # static analysis
```

Test sources:
- `app/src/test/java/com/example/` — local unit tests
- `app/src/test/screenshots/` — Roborazzi screenshot output
- `app/src/androidTest/java/com/example/` — instrumented tests

---

## Project Layout

```
app/src/main/java/com/example/
├── ui/                # ViewModels + 6 managers + shared types + screens + components + theme
├── data/              # Room DB, entities, DAOs, repositories + pure game logic models/engine
├── data/svdag/        # Sparse Voxel DAG 3D world system
├── audio/             # PCM music synthesis, SoundPool SFX, haptics
├── gl/                # OpenGL ES renderers
├── assets/shaders/    # GLSL shaders
MainActivity.kt        # Entry point
```

See [ARCHITECTURE.md](ARCHITECTURE.md) and [CODEBASE.md](CODEBASE.md) for the full map.

---

## Coding Conventions

Follow the existing style:

- **Kotlin** standard style; 2-space indentation; named arguments for clarity.
- **Single source of truth** — mutate the shared `GameViewModel.GameUiState` via `_uiState.update { it.copy(...) }`. Never hold duplicate game state in screens.
- **Managers over god-classes** — put new gameplay logic in the appropriate manager (`CombatManager`, `ExplorationManager`, `InventoryManager`, `PersistenceManager`, `CosmeticVaultManager`) and expose it through `GameViewModel` delegation methods.
- **Cross-manager calls go through `GameViewModel` callbacks** — do not construct manager→manager dependencies.
- **Compose** — use Material 3 components; keep screens reading from `uiState`.
- **No hardcoded security** — do not commit secrets or real keystores. Use the existing env-driven signing config.

---

## Working on the Codebase

The core loop is **delegate through `GameViewModel`**:

1. Find the feature's home manager (combat/exploration/inventory/persistence/cosmetics).
2. Add a public function to that manager.
3. Add a thin public wrapper on `GameViewModel` (e.g., `fun foo() = xManager.foo()`).
4. Wire any required cross-manager callback in the manager constructor block inside `GameViewModel`.

### Example: adding a new combat action
- Implement the logic in `CombatManager.combatXxx()`.
- Add `fun combatXxx() = combatManager.combatXxx()` in `GameViewModel`.
- Surface it in the combat UI (`TerminalScreen`/`ExplorationView`).

---

## Adding Content

### New enemies / bosses
- Spawn definitions live in `GameEngine.kt` (`spawnEnemy`, `spawnBoss`).
- AI goes in `data/EnemyCombatAIScript.kt` (`evaluateAction`, `evaluateBossAction` + per-boss functions).
- Loot tables are in `data/CombatLootDropSystem.kt` (`LOOT_DATABASE`, `BOSS_LOOT`).

### New items
- Add a `GameItem` to `data/GameItemRegistry.kt`.
- Handle `useInventoryItem` behavior in `InventoryManager.kt`.

### New programs
- Add the base definition to `getProgramById` in `PersistenceManager.kt` (so saves/imports restore it).
- Consider the RAM/regen and status-effect hooks in `CombatManager.executeCombatProgram`.

### New cosmetic themes / buffs / prompts
- Add to `data/DataFragmentModels.kt` (enums `CosmeticTheme`, `TerminalPromptStyle`, `PerformanceBuff`).
- The `CosmeticVaultManager` handles unlock/equip logic automatically.

### New cell types
- Extend `CellType` in `data/GameModels.kt` and `VoxelType` in `data/svdag/SparseVoxelDag.kt` relevant to generation.
- Handle the cell in `ExplorationManager.interact()` / movement logic and the perspective/minimap renderers.

---

## Releasing / Signing

Signing is configured in `app/build.gradle.kts`:

- **Release** reads environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) with `keyAlias = "upload"`; falls back to `${rootDir}/my-upload-key.jks`.
- **Debug** uses `${rootDir}/debug.keystore` (`androiddebugkey` / password `android`).

To produce a signed release you must provide a keystore + env vars. Do **not** commit keystores.

---

## Reporting Issues

Open an issue with:

- Android version + device/emulator
- Steps to reproduce
- Expected vs actual behavior
- Any logcat output (especially crash stack traces)

Known gaps / future work are catalogued in the root [`IMPROVEMENT_PLAN.md`](../IMPROVEMENT_PLAN.md) (Firebase, onboarding, skill tree, localization, modularization, CI, etc.).
