# Netcrawler — Architecture

This document explains how the Netcrawler codebase is organized and how data flows through it.

---

## Layers at a Glance

Netcrawler follows a layered **MVVM** (Model-View-ViewModel) architecture. The key idea is a **thin coordinator `GameViewModel`** that holds a single source of truth (`GameUiState`) and delegates all game logic to **specialized manager classes**.

```
┌─────────────────────────────────────────────────────────────┐
│                     UI LAYER (Compose)                       │
│   MainActivity → TerminalScreen → ...12+ screens            │
│   ui/screens/*, ui/components/*                             │
└───────────────────────────┬─────────────────────────────────┘
                            │ observes StateFlow<GameUiState>
┌───────────────────────────▼─────────────────────────────────┐
│              COORDINATOR: GameViewModel (~500 lines)         │
│   Owns: MutableStateFlow<GameUiState> (single source of      │
│   truth) + delegates each domain to a dedicated manager      │
└───────┬──────────┬──────────┬──────────┬─────────────────────┘
        │          │          │          │
   ┌────▼───┐ ┌────▼────┐ ┌───▼────┐ ┌───▼────────────┐
   │ Combat │ │Explore  │ │Invntry │ │  Persistence   │
   │ Manager│ │Manager  │ │Manager │ │    Manager     │
   └────────┘ └─────────┘ └────────┘ └────────────────┘
        └──────────┬───────────────────────────┘
                   │      all call into
┌──────────────────▼──────────────────────────────────────────┐
│                       DATA LAYER                             │
│   GameEngine (pure logic) · EnemyCombatAIScript              │
│   TurnBasedCombatEngine · GameItemRegistry · Registries      │
│   Room: GameDatabase + 10 entities + DAOs + repositories     │
│   SVDAG: SparseVoxelDag, WorldBuilder, Scanner, Pathfinder   │
└─────────────────────────────────────────────────────────────┘
```

---

## The Single Source of Truth: `GameUiState`

`GameViewModel.GameUiState` is a large immutable `data class` (~160 fields) defined inside `GameViewModel.kt`. It holds **everything** about the current game session, including:

- **Player state** — name, class, HP, shield, RAM, credits, XP, attributes
- **Position & navigation** — grid coordinates, direction, level, zone
- **Level data** — the current `maze`, plus persisted maps for every floor/level/district
- **Combat state** — active enemy, turn, phase, action histories, status effects, banner/flash/popup animation state
- **Inventory & equipment** — items, programs, cyberware, implants, shops
- **Cosmetics** — unlocked themes, prompt styles, active buffs, data fragments
- **SVDAG world state** — voxel DAG, stats, ICE entities, player position, scanners

Because it's a single immutable object updated via `_uiState.update { it.copy(...) }`, the entire UI recomposes consistently from one source.

> **Note:** Because the state type lives *inside* `GameViewModel`, every manager constructor references it as `MutableStateFlow<GameViewModel.GameUiState>`, and screens refer to the type through the ViewModel.

---

## The Manager Pattern

The ViewModel was historically a ~4,750-line "god file." It was split into **six focused managers**, each owning one domain, while the ViewModel itself became a thin coordinator (~500 lines) that:

1. Constructs the managers in its `init`/property initializers
2. Holds the shared `MutableStateFlow<GameUiState>`
3. Exposes public functions that simply delegate to the correct manager
4. Provides a handful of shared helper callbacks passed *into* the managers

### Managers

| Manager | File | Responsibility |
|---------|------|----------------|
| **CombatManager** | `ui/CombatManager.kt` (~620) | Turn-based combat, attack/defend/hack/scan, programs, status effects, bosses, loot |
| **ExplorationManager** | `ui/ExplorationManager.kt` (~1,440) | Movement, turn, weather, enemy spawns, level loading/navigation, SVDAG, terminal commands |
| **InventoryManager** | `ui/InventoryManager.kt` (~650) | Items, equipment, shops, cyberware/clinic, implants, importing/equipping |
| **PersistenceManager** | `ui/PersistenceManager.kt` (~800) | Save/load, serialization, game lifecycle, leaderboard, save export/import |
| **CosmeticVaultManager** | `ui/CosmeticVaultManager.kt` (~170) | Data fragments, themes, prompt styles, performance buffs |
| **GameViewModel** | `ui/GameViewModel.kt` (~500) | Coordinator: state + delegation + hacking mini-game + shared callbacks |

### How Managers Communicate

Managers **do not call each other directly** (avoids circular dependencies). Instead, `GameViewModel` wires them together using **constructor callbacks**. For example:

- `ExplorationManager` receives `onTriggerCombat: () -> Unit` so moving onto a virus node can start combat without calling `CombatManager` directly.
- `CombatManager` receives `onBossZoneTransition: (Zone, Int) -> Unit` so defeating a boss loads the next zone.
- `InventoryManager` receives `onCombatAction: () -> Unit` and `onApplyStatusEffectToPlayer/Enemy` so using an item in combat advances the enemy turn and applies status effects.

This keeps every manager independently testable and loosely coupled.

---

## Data Flow: A Concrete Example (Combat)

1. Player steps onto a `VIRUS_NODE`.
2. `ExplorationManager.interact()` calls its `onTriggerCombat(x, y)` callback.
3. `GameViewModel` forwards this to `combatManager.triggerCombat(x, y)`.
4. `CombatManager` spawns an enemy via `GameEngine.spawnEnemy(level)`, writes combat state into `_uiState`, logs, and schedules the input window.
5. The UI observes `uiState` and renders the combat screen.
6. The player presses **Strike** → `GameViewModel.combatAttack()` → `combatManager.combatAttack()`.
7. `CombatManager` computes damage (using class passives, crit, balcony, status effects), mutates the enemy object, records a `TurnActionRecord`, updates `_uiState`, then launches the enemy turn via `executeEnemyCombatTurn()`.
8. On victory, `handleCombatVictory()` drops loot and, for bosses, calls `onBossZoneTransition` → `explorationManager.loadOrCreateLevel(...)`.

Every step flows through the shared `_uiState`, so the log feed, HP bar, and animations all update in sync.

---

## Cross-Domain Types

A few types used by many managers were promoted from being nested inside `GameViewModel` to **top-level files** under `ui/`:

- `ui/ActiveScreen.kt` — the `ActiveScreen` navigation enum
- `ui/CombatTurn.kt` — the `CombatTurn` enum (`PLAYER`, `ENEMY`, `ANIMATING`)
- `ui/CombatHackingPatternState.kt` — the combat breach-protocol state data class

The core data types (`Enemy`, `Program`, `CellType`, `Zone`, `GameState`, `TurnPhase`, `NetrunnerClass`, etc.) live in `data/GameModels.kt`.

---

## The Data Layer

### Pure Logic (no Android dependencies)

- **`GameEngine`** — procedural maze generation, 3D perspective raycasting, enemy spawning, boss spawning, hacking puzzle generation, starting program kits, weather. The largest and most central logic object.
- **`TurnBasedCombatEngine`** — an alternate, self-contained immutable combat state machine (`CombatEngineState`, `PlayerCombatAction`, `EnemyActionType`). *Note: the live game uses `CombatManager`; this engine is a reference/pure-Kotlin implementation for tests and future use.*
- **`EnemyCombatAIScript`** — decision logic for standard enemies and the three bosses (`evaluateBossAction`).
- **`GameItemRegistry`**, **`CyberwareImplantRegistry`** — static item & implant catalogs.
- **`SparseVoxelDag`** and friends — SVDAG 3D world structures.

### Persistence (Room)

- **`GameDatabase`** — a singleton Room database with **10 entities** and version **6**.
- **Entities** — `RunRecord`, `CharacterProfileEntity`, `GameSaveProgressEntity`, `InventoryItemEntity`, `FloorMapEntity`, `FloorObstacleEntity`, `PlayerMapPositionEntity`, `GridMapStateEntity`, `GridEntityCoordinateEntity`, `PlayerEntity`.
- **DAOs** — `RunRecordDao`, `CharacterProfileDao`, `GameSaveProgressDao`, `InventoryItemDao`, `FloorMapDao`, `PlayerNpcCoordinatesDao`, plus others.
- **Repositories** — `GameRepository` (main save/profile/inventory), `FloorMapRepository`, `GridGameStateRepository`, `PlayerRepository`.
- Migration `MIGRATION_1_2` is registered; `fallbackToDestructiveMigration(true)` handles newer schemas.

---

## Rendering & Audio

- **OpenGL ES** — `gl/CyberMatrixGLView` / `CyberMatrixRenderer` (digital-rain matrix) and `gl/CyberCharacterGLView` / `CyberCharacterRenderer` (3D character). Shaders live in `assets/shaders/*.glsl`.
- **Canvas 2D** — `FirstPersonPerspectiveCanvas` for the first-person corridor, `RenderMiniMap` for the map.
- **Audio** — `audio/CyberSoundEffectsManager` (PCM audio-track music), `audio/CyberSoundPoolManager` (SFX), `audio/CyberVibrationManager` (haptics).

---

## Legacy / Not-Wired Code

Several ViewModels exist that performed domain logic before the manager refactor and are **no longer the main path**:

- `ui/GameTurnViewModel.kt` (~900 lines)
- `ui/HackingViewModel.kt` (~480 lines)
- `ui/InventoryViewModel.kt`, `ui/CharacterProfileViewModel.kt`, `ui/GameProgressViewModel.kt`

These are kept for reference and potential reuse but are not consumed by `MainActivity`'s active flow (which uses `GameViewModel`). Use them with caution.
