# Netcrawler — Data & Persistence

This document describes how Netcrawler stores game state: the Room (SQLite) schema, the dual-layer save system, the save lifecycle, and the portable save **export/import** feature.

---

## Table of Contents

- [Persistence Layers](#persistence-layers)
- [Room Database](#room-database)
- [The Save Entity](#the-save-entity)
- [Save / Load Flow](#save--load-flow)
- [Export / Import (Portable Saves)](#export--import-portable-saves)
- [Serialization Format](#serialization-format)
- [Level Seeds & Sharing](#level-seeds--sharing)
- [Data Hygiene & Clearing](#data-hygiene--clearing)

---

## Persistence Layers

Netcrawler uses **two independent layers** for durability:

1. **Room (SQLite)** — the primary store for saved games, character profiles, run records, inventory items, floor maps, and grid/player persistence. Managed by `GameDatabase` (a singleton).
2. **SharedPreferences** — a lighter fallback for quick restore. Stored under the shared-prefs file **`netcrawler_save_prefs`** (see `PersistenceManager`).

### Why two layers?
- **Room** gives robust, structured, queryable storage (e.g., leaderboard sorting, profile listing).
- **SharedPreferences** supports fast key/value restore and serves as a snapshot during sessions (the "quick resume" path uses `loadFromSharedPreferences()`).
- The **export/import** feature additionally produces a **portable single-string save** that is independent of both storage layers.

---

## Room Database

Defined in `data/GameDatabase.kt` — version **6**, `exportSchema = false`, with migration `MIGRATION_1_2` registered and `fallbackToDestructiveMigration(true)`.

### Entities (10)

| Entity | Table | Purpose |
|--------|-------|---------|
| `RunRecord` | `run_records` | Completed-run history for the leaderboard (name, class, level, nodes hacked, credits, timestamp, outcome) |
| `CharacterProfileEntity` | `character_profiles` | Player profile (name, class, level, credits, max HP/RAM, created timestamp) |
| `GameSaveProgressEntity` | `game_save_progress` | Full active-save snapshot (see below) |
| `InventoryItemEntity` | `inventory_items` | Inventory items (name, type, quantity, description, timestamp) linked to a `saveSlotId` |
| `FloorMapEntity` | floor maps | Grid maze state for rooms/floors |
| `FloorObstacleEntity` | floor obstacles | Obstacles within a floor map |
| `PlayerMapPositionEntity` | player position | Player's grid position within a floor |
| `GridMapStateEntity` | grid state | Generalized grid state snapshots |
| `GridEntityCoordinateEntity` | entity coordinates | Skill/NPC entity coordinates on the grid |
| `PlayerEntity` | players | Core player attributes |

### DAOs
- `RunRecordDao`, `CharacterProfileDao`, `GameSaveProgressDao`, `InventoryItemDao`
- `FloorMapDao` (in `FloorMapDao.kt`), `PlayerNpcCoordinatesDao`, plus `GridMapStateDao`, `GridEntityCoordinateDao`, `PlayerDao` (referenced by the repositories)

### Repositories
- **`GameRepository`** — main save/profile/inventory CRUD (takes the 4 core DAOs)
- **`FloorMapRepository`** — floor map + obstacle + player position access
- **`GridGameStateRepository`** — grid state + entity coordinate access
- **`PlayerRepository`** — player DAO access

---

## The Save Entity

`GameSaveProgressEntity` (table `game_save_progress`, primary key `saveSlotId`, default `"current_save"`) stores a **complete snapshot** of the run as a mix of typed columns and CSV-serialized strings:

- **Identity/progression:** runnerName, runnerClass, `level`, `characterLevel`, `characterXp`, `xpToNextLevel`
- **Vitals:** `integrity`, `maxIntegrity`, `playerShield`, `playerMaxShield`, `ram`, `maxRam`, `ramRecoveryRate`
- **Resources:** `credits`, `damageBonus`, `defenseBonus`, `totalCreditsEarned`, `nodesHackedCount`
- **Navigation:** `gridX`, `gridY`, `direction`, `currentZone`, `buildingFloor`, `collectorsLevel`, `cityDistrictIndex`, `hasElevatorKeycard`
- **World:** `activeWeather`, `weatherTurnsLeft`, `stepsSinceLastEvent`, `nextEventSteps`, `predictedWeather`, `levelSeed`
- **Inventory (CSV):** `inventoryCsv`, `installedCyberwareCsv`, `installedProgramsCsv`, `installedImplantsCsv`
- **Mazes (CSV):** `mazeData`, `originalMazeData`, `buildingFloorsData`, `buildingExploredData`, `collectorsLevelsData`, `collectorsExploredData`, `cityDistrictsData`, `cityExploredData`, `exploredCellsCsv`
- **Session:** `gameStateName`, `logFeedSerialized`, `lastSavedTimestamp`

Inventory items are additionally mirrored row-by-row into the `inventory_items` table.

---

## Save / Load Flow

Live in `ui/PersistenceManager.kt`, exposed through `GameViewModel`:

- **`saveGame()`** — serializes the current `GameUiState` into a `GameSaveProgressEntity`, writes it via `GameRepository.saveGameProgress`, and writes the same data into SharedPreferences. Called on interactive `save`, and auto-persisted around combat/level transitions.
- **`hasSavedGame()`** — checks whether a save exists (Room or SharedPreferences).
- **`loadGame()`** — reads the save, deserializes into `GameUiState` (mazes, inventory, programs, implants, weather, log feed), restores navigation, and calls the `onRestoreComplete` callback (which refreshes the perspective). Falls back to `loadFromSharedPreferences()` when needed.
- **`resumeGame()`** / **`restartGame()`** / **`returnToStartMenu()`** — session lifecycle navigation.

Because ShuffleCodes / `activeEnemy` are transient, an in-progress combat session is not fully restored mid-turn — the save snapshots the exploration world state and progression.

---

## Export / Import (Portable Saves)

A **shareable, cross-device save** is produced as a single Base64 string, independent of Room/SharedPreferences.

### Format
```
NETCRAWLER_SAVE_v1:<Base64(JSON)>
```

### `exportSave(): String`
Builds a `JSONObject` with `version=1` and all meaningful `GameUiState` fields (stats, nav, weather, seed, inventory array, installed-program IDs, explored cells, all maze/floors/districts, and installed implant slots), then Base64-encodes it (no line wrap) and prefixes the `NETCRAWLER_SAVE_v1:` tag.

### `copyExportToClipboard()`
Runs `exportSave()` and copies the resulting string to the Android clipboard — driven by the terminal **`export`** command.

### `importSave(encoded): Boolean`
- Strips the `NETCRAWLER_SAVE_v1:` prefix
- Base64-decodes back to JSON
- Validates the maze is non-empty (rejects invalid imports)
- Reconstructs `GameUiState`: inventory list, programs (via `getProgramById`), implants (slot:implant CSV), log feed, game state, zones/floors/districts
- Writes the restored state into `_uiState` and persists it

### `importFromClipboard()`
Reads the clipboard, checks for the `NETCRAWLER_SAVE_v1:` prefix, and calls `importSave()`. Driven by the terminal **`import`** command.

---

## Serialization Format

Serialization helpers in `PersistenceManager` encode structured data as compact strings:

- **Maze / floors** — `serializeMaze`/`deserializeMaze` and `serializeFloors`/`serializeExploredMap`: cell symbols and coordinate pairs delimited consistently (e.g., `#` walls, `.` paths, etc.). Rows and cells are joined with delimiters so any `Array<Array<CellType>>` and `Map<Int, ...>` round-trips.
- **Explored cells** — `serializeExploredCells`: `Pair<Int, Int>` sets.
- **Implants** — `slot:implantId` joined by commas (e.g., `BRAIN:apogee_core,ARMS:...`).
- **Programs** — stored by ID and reconstructed via `getProgramById` (which contains the built-in program definitions, including boss rewards).
- **Log feed** — entries split by `$$`, with `logType` split by `||`.

> ⚠️ **Portability caveat:** Experimental/engine-specific fields not covered by the portable JSON (e.g., the SVDAG volume and transient combat records) are not part of the save export. Export is scoped to the roguelike progression + world state.

---

## Level Seeds & Sharing

- Each procedurally generated level stores its seed in `GameUiState.levelSeed` (`Long`).
- The terminal **`seed`** command prints the current seed.
- Because generation is deterministic given a seed, sharing a seed lets two players experience the **same dungeon layout**.
- `GameEngine.generateMaze` seeds its RNG from the wall-clock + layer, so regeneration is reproducible when the saved seed is honored on load/import.

---

## Data Hygiene & Clearing

- **`clearHighScores()`** — clears `run_records`.
- **`deleteSaveProgress(slotId)`** — removes the save row and the slot's inventory items.
- `fallbackToDestructiveMigration(true)` — on a schema version mismatch beyond `MIGRATION_1_2`, the DB rebuilds (destructive) rather than crashing, at the cost of old data.
- Run records accumulate per finished run; the leaderboard reads them newest-first.
