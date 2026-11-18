# Netcrawler Baseline Audit & Inventory — v0.1

> **Status:** baseline snapshot; read-only source audit  
> **Audit date:** 2026-11-18  
> **Audited source:** [Skalagrimmo/Labyrinth](https://github.com/Skalagrimmo/Labyrinth), `main`, commit `4da46aa31cab5afa601c1a0d91a32c49cd79b47b` (2026-11-12)  
> **Intended successor / migration target:** [Skalagrimmo/Netcrawler](https://github.com/Skalagrimmo/Netcrawler), `main`, commit `54fa2aef160b70e50a140dd2e0f8a43a0957b625` (2026-10-04)  
> **Change scope:** documentation only. No application source, workflow, save data, or build configuration was changed by this audit.

This document records what is present and connected in the source tree at the audited commit. It distinguishes active gameplay code from dormant generators, inspector-only tooling, and architectural prototypes. Recommendations are listed separately from verified implementation facts.

---

## 1. Executive summary

Labyrinth is a substantial, modular Android cyberpunk roguelike with a working first-person, grid-based exploration loop, turn-based combat, hacking, progression, inventory/cyberware, weather, persistence, and an actively integrated stealth simulation.

It is **not yet a connected open-world cyber-city engine**. Several promising systems exist independently—multi-floor grids, matrix levels, 3D/SVDAG environments—but they do not form one persistent, navigable world. The active campaign still uses three zone types and a single current 2D maze at a time.

The correct next step is not a ground-up rewrite. Preserve and stabilize the active game systems, introduce a consistent world/location layer around them, and then connect existing generators and gameplay features to that layer.

### Current readiness snapshot

| Area | Audit status | Notes |
|---|---|---|
| Core RPG / roguelike loop | **Active** | Character creation, exploration, combat, progression, inventory |
| First-person grid navigation | **Active** | Cardinal directions and cell-by-cell movement |
| Stealth / guard simulation | **Active** | Patrols, perception, suspicion, noise, search and alert states |
| Building / Collectors / City generation | **Active** | Three zone generators, each producing a 64×64 map |
| Persistent multi-location world graph | **Missing** | Per-zone caches exist; no global graph of connected places |
| Multi-floor grid generator | **Prototype / inspector path** | Separate 4-floor grid output; not the normal campaign map |
| Matrix level generator | **Unwired prototype** | No active campaign call site found |
| 3D procedural cyber-world generator | **Unwired prototype** | District configuration exists but does not currently alter generation |
| SVDAG | **Mixed** | Inspector tooling plus expensive construction on normal location transitions |
| NPC / quest / dialogue framework | **Not found** | No integrated general civilian/NPC or quest system identified |
| Deterministic map replay | **Incomplete** | Active generation does not reliably store and reuse the actual seed |
| Portable save | **Partial** | Main campaign progression/maps included; several runtime/world systems excluded |
| Netcrawler repository migration | **Blocked** | Scheduled mirror workflow currently fails while pushing workflow files |

---

## 2. Repository and migration state

### 2.1 Labyrinth is the active source repository

At audit time, `Labyrinth/main` points to:

- Commit: `4da46aa31cab5afa601c1a0d91a32c49cd79b47b`
- Commit date: 2026-11-12
- Repository: [github.com/Skalagrimmo/Labyrinth](https://github.com/Skalagrimmo/Labyrinth)

The audited source tree contains 23 Gradle modules: eight functional core libraries, the design-system module, thirteen feature modules, and the app shell.

### 2.2 Netcrawler is currently an import scaffold

At audit time, `Netcrawler/main` points to:

- Commit: `54fa2aef160b70e50a140dd2e0f8a43a0957b625`
- Commit date: 2026-10-04
- Repository: [github.com/Skalagrimmo/Netcrawler](https://github.com/Skalagrimmo/Netcrawler)

The repository currently contains only the short README and `.github/workflows/import-from-labyrinth.yml`. Its workflow is configured to run every five minutes, clone Labyrinth, and force-push the Labyrinth history to `Netcrawler/main`.

The latest observed run at audit time was [run #216](https://github.com/Skalagrimmo/Netcrawler/actions/runs/44221505216), started 2026-11-18 02:12 UTC. It completed with failure: the push was rejected because the GitHub Actions token has `contents: write` but does not have the `workflows` permission required when the mirrored source includes workflow-file changes.

**Migration implication:** treat Labyrinth as the source of truth until the import workflow is deliberately repaired or replaced. Do not build independent Netcrawler-only changes on the assumption that they are safe from the scheduled force-push. Once the mirror succeeds, it is designed to replace the target branch with the source history.

The audit document is therefore recorded in Labyrinth's `docs/` directory. It can travel with the source during a later migration without creating a second, competing copy.

---

## 3. Existing architecture and active runtime path

### 3.1 Module architecture

The project is already separated into domain and feature modules. The key active modules include:

- `:core:model`: shared domain models and cell/zone types.
- `:core:engine`: generation, combat rules, game logic, and stealth simulation.
- `:core:game`: shared game state and the stealth session controller.
- `:core:render`: perspective-rendering bridge and SVDAG structures/builders.
- `:core:platform`: Android persistence and platform adapters.
- `:feature:gamevm`: `GameViewModel`, the session coordinator.
- `:feature:exploration`: `ExplorationManager`, movement and level navigation.
- `:feature:combat`, `:feature:inventory`, `:feature:skilltree`, `:feature:cosmetic`: active gameplay managers.
- `:feature:exploration-ui`: first-person canvas and minimap.
- `:feature:svdag-inspector`: SVDAG inspection interface.
- `:feature:terminal` / `:feature:menus`: command and menu UI.

The current MVVM / manager-based split is worth preserving. A new world layer should sit beneath the existing managers rather than move all gameplay into a new monolithic controller.

### 3.2 Normal campaign creation and location loading

The active starting path is:

1. `GameViewModel.createCharacter()` initializes the run.
2. It calls `ExplorationManager.loadOrCreateLevel(Zone.BUILDING, 1, 1, 1)`.
3. When no cached floor exists, `EnvironmentGenerator.generateBuildingFloor(1)` creates the first map.
4. `ExplorationManager` restores or caches maze and explored-cell state for the current location.
5. Movement and interactions update `GameUiState`; combat is delegated to the combat manager.

The main campaign state has one current maze/player position, with maps cached for previously visited floors, Collector levels, and City districts. This supports returning to generated places, but it is not the same as having a global world graph that describes all cities, districts, routes, vertical connections, and location identities.

### 3.3 Current presentation

The active exploration presentation is a **cell-based first-person view**, with cardinal orientation and step movement. The player moves forward/backward and turns left/right; this is not a free-roaming 3D camera.

- `FirstPersonPerspectiveCanvas` draws a shallow perspective view from the current map and player orientation, sampling a few cells ahead.
- `RenderMiniMap` presents a local top-down map view.
- The ASCII perspective renderer is a separate textual representation.
- The SVDAG inspector is a volumetric inspection tool, not the normal city-map authoring or campaign-navigation interface.

Consequently, a future larger world does not require discarding the current presentation. A world/location layer can first supply compatible 2D maps to the active renderer while additional renderers are developed independently.

---

## 4. Generator inventory

The distinction between “exists in the codebase” and “drives normal gameplay” is important.

| Generator / system | Output and scope | Current connection |
|---|---|---|
| `EnvironmentGenerator.generateBuildingFloor` | Building floor maps, 64×64; rooms/corridors, thematic chambers, secrets, terminals and bypasses | **Active campaign** |
| `EnvironmentGenerator.generateCollectorTunnels` | Collector maps, 64×64; wandering tunnels, connected nodes, data stores, viruses and caches | **Active campaign** |
| `EnvironmentGenerator.generateCitySector` | City sector template, 64×64; avenues, central Grand Hall block, building blocks, caches and portal | **Active campaign**, but only as an isolated sector template |
| `MazeGenerator` / `GameEngine.generateMaze` | Generic room-and-corridor generator with loops, special rooms and reachability checks | **Dormant path**; its manager facade is only used by a private `generateNewLevel()` path with no active caller found |
| `ProceduralMultiFloorLevelGenerator` | Defaults to four 14×14 floors, connectors, nodes, caches, spawn/exit and reachability report | **Inspector/prototype path**; not the normal campaign map |
| `ProceduralMatrixLevelGenerator` | Standalone matrix level with security bands, entry/data/ICE/loot/exit cells | **Unwired prototype**; no active campaign call site found |
| `ProceduralCyberWorldGenerator` | Multi-layer cyber-world / open-world sector structures and waypoint-style data | **Unwired prototype** for normal gameplay |
| `SvdagWorldBuilder.generateCyberspaceMegaSector` | SVDAG mega-sector generation for inspection | **Inspector path** |
| `SvdagWorldBuilder.buildSvdagFrom2DLevel` | Converts the loaded 2D level to an SVDAG representation | Also invoked on normal location transitions, despite the active game view remaining 2D |

### 4.1 Active EnvironmentGenerator

The three live zone generators all produce 64×64 maps:

- **Building:** up to four campaign floors; rooms and corridors, special chambers, hidden rooms, hackable terminals, terminal doors and alternative vents. The keycard progression is tied to Building floor 2.
- **Collectors:** two campaign levels in the current boss progression; wandering conduit/tunnel sections are connected into a traversable map. The exit type depends on the level.
- **City:** one generated district/sector template with a road/avenue pattern, central landmark block and building blocks. It is not a full city made of several graph-connected neighborhoods.

### 4.2 Multi-floor grid prototype

`ProceduralMultiFloorLevelGenerator` creates a separate multi-floor grid structure, with vertical connectors and a reachability pass. It is useful prior work and should not be thrown away.

However:
- the default four 14×14 floors are not the maps used by the normal campaign loop;
- the terminal/inspector UI can request this generated structure;
- the private manager generation path is not an active route into normal exploration;
- the declared `CyberDistrictTheme` value is not applied to the floor geometry in the generator body;
- the reachability result has a logic weakness: `isFullyReachable = isFullyConnected || unreachableNames.isEmpty()` can report success when the overall connectivity/exit-coverage check failed but the separate unreachable-name list is empty.

### 4.3 Matrix and 3D cyber-world prototypes

The matrix generator and multi-layer cyber-world generator demonstrate useful directions, but they must be treated as prototypes, not as features already connected to the campaign.

In particular, the `CyberEnvironmentDistrict` configuration values (corporate core, neon undercity, industrial subgrid, cyber-grid nexus) are not currently read by the world-generation body in a way that changes the generated geometry. The four names therefore do not yet represent four implemented, distinct city environments.

---

## 5. Existing world progression and navigation limits

The campaign has three zones:

1. **BUILDING** — the opening tower, floors 1–4.
2. **COLLECTORS** — the underground/sub-grid area, currently two levels in the boss sequence.
3. **CITY** — the Metro Core, entered after the Collectors boss.

Boss transitions connect the campaign zones:

- Firewall Sentinel: Building → Collectors.
- Daemon Overlord: Collectors → City.
- Black ICE Colossus: final City boss.

Building stairs/elevator and Collector/City portal progression exist. City inter-district navigation is not yet implemented as a normal route: the City stair handlers explicitly reject direct vertical navigation and refer to local portals, but a general portal/transport graph connecting multiple districts is not present.

The integer `level` field is also used as a compact level identity. The current encoding can collide: Collectors level 2 and City district 0 both map to numeric level 6. Systems that key runtime state by this integer alone risk treating different locations as the same location. A stable compound location ID is needed.

---

## 6. Stealth and interactive systems already worth preserving

### 6.1 Stealth simulation is live

The stealth subsystem is not merely a design stub. It is connected through `StealthSessionController`, `StealthSimulation`, and `StealthWorld`, and is coordinated with the game/world clock.

The active system includes:

- stationary guards derived from `VIRUS_NODE` cells;
- a limited number of mobile patrols with waypoint routes;
- pathfinding, line of sight, vision cones and hearing;
- noise events from movement, scans and interactions;
- suspicion, investigation, searching and alert propagation;
- stealth/crouch behavior, duct/vent concealment, cover and decoys;
- disabling or jamming ICE units;
- combat triggered when detection escalates.

Guard states include patrolling, investigating, searching, alerted and disabled. Combat pauses or changes the exploration-stealth update flow, with de-escalation after combat.

This is a substantial reusable feature. The future world model should give it stable location/entity IDs rather than replacing it.

### 6.2 CellType currently carries several responsibilities

The active maze is an array of `CellType`. The enum mixes multiple concepts:

- base geometry (wall/path);
- special terrain (hall, dome, balcony, slope, vent);
- interactive objects (terminal, cache, portal, elevator);
- gates/blockers (secret wall, terminal door);
- hostile nodes (virus node).

This is adequate for the current small grid model but will become restrictive for layered city environments containing terrain, buildings, doors, props, loot, NPCs, patrols and dynamic events in the same coordinate space.

A small `underlyingCellTypes` map is used in memory for terrain restoration around some moving/temporary entities, but it is not a general, persistent overlay/entity system.

### 6.3 Generic NPC / quest / dialogue layer not found

The audit found enemy/loot content models and active stealth guards, but no integrated general civilian NPC, quest-state, dialogue, schedule, faction-relation or city-service simulation framework. Legacy Room entity schemas mention generic entity categories, but the relevant data path is not an active entity/DAO system in the current game database.

This should be treated as missing infrastructure, not as a feature that only needs to be switched on.

---

## 7. Confirmed issues and architectural risks

Priorities below are relative to the next phase of world expansion.

### P0 — resolve before building more world content

#### P0.1 Movement collision rules disagree with generated blockers

`ExplorationManager.isValidMove(x, y)` currently checks map bounds and rejects `CellType.WALL`, but does not apply the full blocker set.

The generator and stealth-world adapter treat `SECRET_WALL` and `TERMINAL_DOOR` as movement blockers. The interaction code also presents terminal doors as locked pending a nearby terminal hack. This creates inconsistent rules: the movement path can permit entry into cells that other systems regard as blocked.

**Required correction:** define one shared movement/collision policy for all cell layers and use it in player movement, enemy/guard pathfinding, world adapters and reachability validation.

#### P0.2 Active map generation does not reliably persist its actual seed

The active character-creation path calls the zone generator using its default wall-clock seed. It does not reliably write that exact seed into `GameUiState.levelSeed`. The state default is zero, so the terminal `seed` command can report a value that did not generate the current map.

A separate private/dormant `generateNewLevel()` path records a timestamp but does not pass that seed through the active zone generator path; it is not the solution for normal gameplay.

**Required correction:** allocate and persist a seed for every location before generation, pass it explicitly to the generator, and restore the same seed when loading/regenerating the location. Do not use one global run seed as a substitute for per-location identity.

### P1 — resolve as part of the World Layer foundation

#### P1.1 Replace numeric level-only identity

Use a compound stable identity such as `zone + district + location + floor`, or a generated persistent ID. This prevents different zones from sharing the same numeric level key and gives the world graph a durable reference for saves, guards, loot and routes.

#### P1.2 Make SVDAG construction lazy or opt-in

Normal 2D location transitions currently invoke `buildSvdagFrom2DLevel`. That path constructs a volumetric representation and runs a raycast benchmark (the builder's 2D conversion path defaults to 300 benchmark iterations), even though ordinary exploration is rendered by the 2D first-person canvas.

This is avoidable work on lower-end Android devices.

**Required correction:** make SVDAG generation lazy and request-driven by the inspector/FPE or a feature that actually needs it. Measure generation separately on target devices before making it part of the default path.

#### P1.3 Introduce a common map contract and layered entities

Existing generators return different shapes and have different responsibilities. Before connecting them into one world, define a shared result contract that includes:
- stable location identity and location type;
- dimensions and base terrain;
- explicit spawn and exits/connections;
- collision and traversal metadata;
- deterministic generation seed/version;
- object/entity placements;
- validation results and generator diagnostics.

Keep base terrain separate from interactive objects, props, doors, NPCs, guards and loot. A cell can host several layers without turning the `CellType` enum into a catalogue of every possible world object.

### P2 — correct while integrating prototypes

- Fix `MultiFloorReachabilityReport` so its success flag cannot be true when floor/exit coverage failed.
- Apply `CyberDistrictTheme` to generated floor geometry, or remove the unused parameter until it has behavior.
- Make `CyberEnvironmentDistrict` configuration materially influence generated 3D environments before describing the four variants as implemented districts.
- Define actual City district/transport/portal connections instead of relying on integer map caches.
- Reconcile documentation with runtime behavior. For example, current documentation describes seed-based reproducible maps, while the active generation path does not yet meet that promise.

---

## 8. Persistence inventory and gaps

The active Room save path stores the campaign state through `GameSaveProgressEntity` and the persistence manager. Main campaign data includes the current maze, saved Building/Collectors/City maps, explored state, player coordinates and progression/inventory-related state. Portable export/import also includes the principal campaign maps and `levelSeed` field.

The current save/export model does **not** provide a complete persistent world snapshot. The audit did not find persistence for:

- the global city/district/location graph (not yet implemented);
- SVDAG world volumes/overlays;
- `currentMultiFloorLevel` prototype state;
- runtime stealth guard state and patrol progress;
- a general world-entity/NPC/quest/dialogue state;
- an individual seed/version record for every generated location.

The Room database is version 6. It retains several legacy map/entity schema classes, but the active database DAO set is limited to run records, character profiles, save progress and inventory. The presence of legacy entity classes should not be mistaken for a functioning general-purpose world-entity database.

**Persistence direction:** save stable world/location IDs, generator ID/version/seed, changed terrain or object state, entity state, and traversal/discovery state. Static content should be regenerable from versioned definitions; runtime changes should be saved as compact deltas where practical.

---

## 9. Recommended Netcrawler World Layer

The recommended direction is an incremental layer around existing systems, not a replacement of the combat, inventory, stealth or rendering code.

### 9.1 World hierarchy and graph

Use a durable hierarchy such as:

`World → City → District → Location → Floor`

A location is a graph node; routes, stairs, elevators, portals, transit lines and other transitions are explicit edges. Vertical and underground connections are not special cases hidden inside a map generator.

Each node should have a stable ID independent of its displayed name or numeric floor index.

### 9.2 Shared location/map result

Normalize all generator outputs into a common location result. The current Building/Collectors/City generators should be adapted first. The multi-floor and 3D prototypes can be connected later through adapters.

### 9.3 Layered world representation

Separate:
1. terrain / walkable geometry;
2. static structures and props;
3. interactive objects and doors;
4. actors (guards, enemies, NPCs);
5. loot and resources;
6. dynamic effects / temporary events.

This lets the current 2D FPP renderer consume a simple projection of the same location data while the minimap, inspector and future rendering modes use richer layers.

### 9.4 Determinism and persistence

Every generated location receives:
- stable location ID;
- generator ID and version;
- explicit seed;
- generated map/result;
- persistent state delta.

This is required for save restoration, reproducible bugs, sharing seeds, content updates and world continuity.

### 9.5 Preserve existing systems through adapters

The existing stealth system should query the new location model through an adapter. The combat manager should still be triggered through its current boundary/callback. The first-person renderer and minimap should initially receive adapted map data without needing to understand the entire world graph.

---

## 10. Suggested implementation order

1. **Stabilize current rules:** unify movement blockers and correct active seed assignment/persistence.
2. **Introduce stable location identity:** stop using the numeric level alone as a world key.
3. **Define the common map contract:** adapt the three active zone generators without changing their gameplay content.
4. **Add the location graph:** connect current floors/zones first; then introduce City districts and explicit transit/vertical links.
5. **Separate terrain and entities:** migrate special objects and guards incrementally from overloaded cell types.
6. **Extend persistence:** save location metadata, seeds and world-state deltas; retain compatibility with existing campaign saves where feasible.
7. **Connect prototypes selectively:** multi-floor grid first; matrix and 3D/SVDAG systems only after their generator contracts and performance are measured.
8. **Expand to the four cyber-city identities:** only after district themes/configuration actually affect layouts, entities, traversal and environmental behavior.

### Minimum acceptance gates

A change to the World Layer should not be considered complete until:

- the player, guards and pathfinding agree on what blocks movement;
- a location can be regenerated from its stored generator/version/seed;
- returning to a location restores both its map and its changed state;
- two locations with the same floor number remain distinct;
- every declared exit/portal/stair/transit edge has a valid destination;
- a generated location passes spawn-to-exit and floor-transition reachability tests;
- normal exploration does not build SVDAG data unless requested;
- existing combat, hacking, inventory, stealth and save/load flows continue to work.

---

## 11. Source files reviewed

The following files were used to trace the active paths and prototype boundaries:

- `core/engine/src/main/kotlin/com/example/data/EnvironmentGenerator.kt`
- `core/engine/src/main/kotlin/com/example/data/MazeGenerator.kt`
- `core/engine/src/main/kotlin/com/example/data/ProceduralMultiFloorLevelGenerator.kt`
- `core/engine/src/main/kotlin/com/example/data/ProceduralMatrixLevelGenerator.kt`
- `core/render/src/main/kotlin/com/example/data/svdag/ProceduralCyberWorldGenerator.kt`
- `core/render/src/main/kotlin/com/example/data/svdag/SvdagWorldBuilder.kt`
- `feature/exploration/src/main/kotlin/com/example/ui/ExplorationManager.kt`
- `core/game/src/main/kotlin/com/example/ui/StealthSessionController.kt`
- `core/engine/src/main/kotlin/com/example/stealth/StealthSimulation.kt`
- `core/engine/src/main/kotlin/com/example/stealth/StealthWorld.kt`
- `feature/exploration-ui/src/main/java/com/example/ui/screens/FirstPersonPerspectiveCanvas.kt`
- `feature/exploration-ui/src/main/java/com/example/ui/screens/RenderMiniMap.kt`
- `core/platform/src/main/java/com/example/ui/PortableSaveStateMapper.kt`
- `core/platform/src/main/java/com/example/data/GameDatabase.kt`
- `Netcrawler/.github/workflows/import-from-labyrinth.yml`

---

## 12. Audit limits and baseline rule

This is a **static source audit** of the exact repository commits listed at the top. It records call-site connectivity, data structures and code-path mismatches found by source inspection.

The following were **not** performed as part of this audit:
- Gradle build or CI execution;
- Android installation or runtime playthrough;
- device-side CPU/RAM profiling;
- save migration or backward-compatibility test;
- automated movement/reachability regression suite.

Therefore, runtime symptoms and performance impact should be confirmed by targeted tests before changing implementation. The P0 items are code-path inconsistencies established by inspection; P1/P2 entries are architectural risks or integration gaps, not claims that every possible runtime failure has already been reproduced.

**Baseline rule:** use this document as the v0.1 inventory. When implementation begins, update the relevant status and evidence rather than silently treating a prototype as production-ready. Keep future audit snapshots versioned so architectural progress and regressions remain traceable.
