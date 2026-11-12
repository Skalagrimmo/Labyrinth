

# Netcrawler — Architecture

This document explains how the Netcrawler codebase is organized and how data flows through it.

---

## Module Layout

The codebase is a multi-module Gradle build — 8 core libraries, a design-system library, 13 feature libraries, and an app shell (see [CODEBASE.md](CODEBASE.md) for the file reference):

- **Core (8):** `:core:model` (domain types), `:core:content` (item/implant catalogs, mod loading), `:core:engine` (pure game logic: engine, combat rules, generators, stealth simulation), `:core:persistence` (pure-Kotlin save codecs), `:core:render` (perspective renderers + SVDAG rules), `:core:game` (shared UI-state contracts: `GameUiState`, combat clocks, `StealthSessionController`), `:core:audio` (`GameAudioPlayer` port), `:core:platform` (Android persistence stack + audio/vibration adapters)
- **Design system:** `:core:designsystem` (Compose theme + CRT overlay)
- **Feature (13):** `skilltree` / `combat` / `exploration` / `cosmetic` / `inventory` (domain managers), `hacking` (minigame screens), `cybergl` (character GL renderer), `widgets` (shared HUD), `exploration-ui` (FPP canvas + minimap), `svdag-inspector` (world inspector), `menus` (menu screens), `terminal` (router + upgrade store), `gamevm` (the `GameViewModel` coordinator)
- **App:** `:app` — `MainActivity` only

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

`GameUiState` is a large immutable `data class` (~160 fields) defined in `:core:game` (`GameUiState.kt`, package `com.example.ui`). It holds **everything** about the current game session, including:

- **Player state** — name, class, HP, shield, RAM, credits, XP, attributes
- **Position & navigation** — grid coordinates, direction, level, zone
- **Level data** — the current `maze`, plus persisted maps for every floor/level/district
- **Combat state** — active enemy, turn, phase, action histories, status effects, banner/flash/popup animation state
- **Inventory & equipment** — items, programs, cyberware, implants, shops
- **Cosmetics** — unlocked themes, prompt styles, active buffs, data fragments
- **SVDAG world state** — voxel DAG, stats, ICE entities, player position, scanners

Because it's a single immutable object updated via `_uiState.update { it.copy(...) }`, the entire UI recomposes consistently from one source.

> **Note:** `GameUiState` is a top-level type in `:core:game`; every manager constructor references it as `MutableStateFlow<GameUiState>`, and screens resolve it through the module dependency graph.

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
| **CombatManager** | `CombatManager.kt` in `:feature:combat` | Turn-based combat, attack/defend/hack/scan, programs, status effects, bosses, loot |
| **ExplorationManager** | `ExplorationManager.kt` in `:feature:exploration` | Movement, turn, weather, enemy spawns, level loading/navigation, SVDAG, terminal commands |
| **InventoryManager** | `InventoryManager.kt` in `:feature:inventory` | Items, equipment, shops, cyberware/clinic, implants, importing/equipping |
| **PersistenceManager** | `PersistenceManager.kt` in `:core:platform` | Save/load, serialization, game lifecycle, leaderboard, save export/import |
| **CosmeticVaultManager** | `CosmeticVaultManager.kt` in `:feature:cosmetic` | Data fragments, themes, prompt styles, performance buffs |
| **SkillTreeManager** | `SkillTreeManager.kt` in `:feature:skilltree` | Skill-point allocation: node learn/reset, stat application |
| **GameViewModel** | `GameViewModel.kt` in `:feature:gamevm` | Coordinator: state + delegation + hacking mini-game + shared callbacks |

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

A few types used by many managers were promoted from being nested inside `GameViewModel` to **top-level files in `:core:game` (package `com.example.ui`)**:

- `ActiveScreen.kt` — the `ActiveScreen` navigation enum
- `CombatTurn.kt` — the `CombatTurn` enum (`PLAYER`, `ENEMY`, `ANIMATING`)
- `CombatHackingPatternState.kt` — the combat breach-protocol state data class

The core data types (`Enemy`, `Program`, `CellType`, `Zone`, `NetrunnerClass`, etc.) live in `:core:model` (`DomainModels.kt`); combat contracts (`GameState`, `TurnPhase`, `CombatActionType`, `TurnActionRecord`) live in `:core:engine`.

---

## The Data Layer

### Pure Logic (no Android dependencies)

- **`GameEngine`** — procedural maze generation, 3D perspective raycasting, enemy spawning, boss spawning, hacking puzzle generation, starting program kits, weather. The largest and most central logic object.
- **`TurnBasedCombatEngine`** — an alternate, self-contained immutable combat state machine (`CombatEngineState`, `PlayerCombatAction`, `EnemyActionType`). *Note: the live game uses `CombatManager`; this engine is a reference/pure-Kotlin implementation for tests and future use.*
- **`EnemyCombatAIScript`** — decision logic for standard enemies and the three bosses (`evaluateBossAction`).
- **`GameItemRegistry`**, **`CyberwareImplantRegistry`** — static item & implant catalogs.
- **`SparseVoxelDag`** and friends — SVDAG 3D world structures.

### Persistence (Room)

- **`GameDatabase`** — a singleton Room database with **10 entities** and version **6** (in `:core:platform`, package `com.example.data`).
- **Entities** — the 4 save-stack entities (`RunRecord`, `CharacterProfileEntity`, `GameSaveProgressEntity`, `InventoryItemEntity`) plus 6 legacy entities (`FloorMapEntity`, `FloorObstacleEntity`, `PlayerMapPositionEntity`, `GridMapStateEntity`, `GridEntityCoordinateEntity`, `PlayerEntity`) kept for schema-v6 stability without DAOs.
- **DAOs** — exactly four, all in `GameDatabase.kt`: `RunRecordDao`, `CharacterProfileDao`, `GameSaveProgressDao`, `InventoryItemDao`.
- **Repositories** — `SaveRepository` (interface, consumed by `PersistenceManager`) + `GameRepository` (implementation, takes the 4 DAOs), in `SaveRepository.kt`.
- Migration `MIGRATION_1_2` is registered; `fallbackToDestructiveMigration(true)` handles newer schemas.

---

## Rendering & Audio

- **OpenGL ES** — `CyberCharacterGLView` / `CyberCharacterRenderer` (3D character, in `:feature:cybergl`). Shaders live in `app/assets/shaders/*.glsl`. (The former CyberMatrix digital-rain renderer was removed as dead code.)
- **Canvas 2D** — `FirstPersonPerspectiveCanvas` for the first-person corridor and `RenderMiniMap` for the map (in `:feature:exploration-ui`); the render bridge extension lives in `:core:render`.
- **Audio** — `CyberSoundEffectsManager` (PCM audio-track music), `CyberSoundPoolManager` (SFX), `CyberVibrationManager` (haptics) in `:core:platform`; the `GameAudioPlayer` port is `:core:audio`.

---

## Legacy / Not-Wired Code

The rebuild removed the dead code as it was identified:

- The legacy turn stack (`GameTurnViewModel`, `TurnStateMachine`) — removed.
- The alternative grid-persistence stack (7 DAOs, 3 repositories, `FloorObstacle` mapping) — removed; the entities stay in `@Database` for schema-v6 stability only.
- The Room floor-grid renderer and the CyberMatrix GL renderer — removed.
- The orphaned ViewModels (`InventoryViewModel`, `CharacterProfileViewModel`, `GameProgressViewModel`) — removed.

`HackingViewModel` survives in `:feature:hacking` next to the hacking minigame screens. The active flow is `MainActivity` → `GameViewModel` (`:feature:gamevm`) → managers.
