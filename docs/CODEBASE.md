
# Netcrawler — Codebase Reference

A source-file reference organized by Gradle module. Netcrawler is a multi-module build: 8 core libraries, a design-system library, 13 feature libraries, and the app shell. Packages are preserved across modules (`com.example.ui` and `com.example.data` are split packages resolved through the dependency graph), so files keep their package when they move and call sites stay unchanged.

## Module Map

| Module | Package(s) | Contents |
|--------|------------|----------|
| `:app` | `com.example` | `MainActivity` — the only app source file |
| `:core:model` | `com.example.core.model` | Domain models shared by every layer |
| `:core:content` | `com.example.data` | Static content catalogs + mod loading |
| `:core:engine` | `com.example.data`, `com.example.stealth` | Pure game logic: engine, combat rules, generators, stealth simulation |
| `:core:persistence` | `com.example.data` | Pure-Kotlin save codecs + loot system |
| `:core:render` | `com.example.data(.svdag)` | Perspective renderers + SVDAG world rules |
| `:core:game` | `com.example.ui` | Shared UI-state contracts + stealth session controller |
| `:core:audio` | `com.example.audio` | The `GameAudioPlayer` port |
| `:core:platform` | `com.example.data`, `com.example.ui`, `com.example.audio` | Android persistence stack + audio/vibration adapters |
| `:core:designsystem` | `com.example.ui.theme`, `com.example.ui.components` | Compose theme + CRT overlay |
| `:feature:skilltree` | `com.example.ui` | Skill tree manager |
| `:feature:combat` | `com.example.ui` | Combat manager |
| `:feature:exploration` | `com.example.ui` | Exploration manager |
| `:feature:cosmetic` | `com.example.ui` | Cosmetic vault manager |
| `:feature:inventory` | `com.example.ui` | Inventory manager |
| `:feature:hacking` | `com.example.ui(.screens/.components)` | Hacking minigame screens + `HackingViewModel` |
| `:feature:cybergl` | `com.example.gl` | OpenGL character renderer |
| `:feature:widgets` | `com.example.ui(.components/.screens)` | Shared HUD widgets |
| `:feature:exploration-ui` | `com.example.ui.screens` | First-person canvas + minimap |
| `:feature:svdag-inspector` | `com.example.ui.screens` | SVDAG world inspector screens |
| `:feature:menus` | `com.example.ui(.screens)` | Menu screens (start, creation, settings, game over, leaderboard, clinic, overlays) |
| `:feature:terminal` | `com.example.ui(.screens)` | Terminal router + upgrade store |
| `:feature:gamevm` | `com.example.ui` | `GameViewModel` — the coordinator |

## App (`:app`)

| Path | Purpose |
|------|---------|
| `java/com/example/MainActivity.kt` | Activity hosting Compose; sets `NetcrawlerTheme`, creates `GameViewModel`, renders `TerminalScreen` |
| `AndroidManifest.xml` | Permissions, launcher activity, network security config |
| `assets/shaders/*.glsl` | OpenGL shaders |
| `res/` | Icons, colors, strings, themes, backup/data-extraction/network rules |

## Core Libraries

### `:core:model` — `com.example.core.model`

| File | Purpose |
|------|---------|
| `DomainModels.kt` | Core domain types: `NetrunnerClass`, `Cyberware`, `StatusEffectType`, `Program`, `Direction`, `CellType`, `Zone`, `CyberWeather`, `Enemy`, `BossType`, `LogMessage`/`LogType`, `DigitalMutation` |

### `:core:content` — `com.example.data`

| File | Purpose |
|------|---------|
| `GameItemRegistry.kt` | Static `GameItem` catalog + mod-item merge |
| `CyberwareImplantRegistry.kt` | Implant catalog + starter implants |
| `ImplantModels.kt` | `CyberwareImplant`, `ImplantBodySlot`, `ImplantAbility` |
| `ItemModels.kt` | `GameItem`, `EquipmentSlot`, `InventoryCategory`, `InventorySortOption`, `ItemRarity` |
| `GameContentCatalog.kt` | Built-in content catalog |
| `EnemyArchetype.kt` | Enemy archetypes |
| `DataFragmentModels.kt` | Data fragment models (cosmetic vault) |
| `ContentModParser.kt` | Markdown parser for mod-defined content |
| `ContentRegistry.kt` | Mod block to model conversion + registry merge |

### `:core:engine` — `com.example.data` + `com.example.stealth`

| File | Purpose |
|------|---------|
| `GameEngine.kt` | Procedural maze generation, 3D perspective raycast, enemy/boss spawning, hacking puzzles, starting kits, weather |
| `MazeGenerator.kt` | Maze generation core |
| `EnvironmentGenerator.kt` | Environment generation |
| `ProceduralMatrixLevelGenerator.kt` | Alternative matrix level generator |
| `ProceduralMultiFloorLevelGenerator.kt` | Multi-floor level generator + `MultiFloorGridLevel` models |
| `RealTimeGameSession.kt` | Real-time session facade (stealth world ticks) |
| `NameGenerator.kt` | Cyberpunk alias/name generator |
| `SkillTreeModels.kt` | Data-driven skill tree models |
| `HackingPuzzle.kt` / `HackingPuzzleGenerator.kt` | Breach-protocol puzzle models + generation |
| `TurnBasedCombatEngine.kt` | Pure-Kotlin immutable combat state machine (reference engine) |
| `EnemyCombatAIScript.kt` | Enemy + boss AI decision logic |
| `EnemyFactory.kt` / `BossFactory.kt` | Enemy/boss construction |
| `CombatDomainModels.kt` | `GameState`, `CombatActionType`, `TurnPhase`, `TurnActionRecord` and other combat contracts |
| `CombatEngineContracts.kt` / `CombatEngineModels.kt` | Reference-engine contracts/models |
| `CombatRoundScheduler.kt` / `CombatRoundClock.kt` | KOTOR-style round scheduling + clock |
| `Combat*Rules.kt` (14 files) | Pure combat rules: damage, defend, enemy turn, flee, item, program, scan, strike, stun, turn maintenance, utility, victory |
| `Exploration*Rules.kt` (4 files) | Movement, scan, threat scan, visibility rules |
| `stealth/StealthSimulation.kt` | ICE perception/suspicion simulation: view cones, noise pulses, patrols, alert relay |
| `stealth/LabyrinthStealthSession.kt` | Labyrinth adapter: guards, patrol routes, movement blockers, A*-based ICE movement |
| `stealth/StealthWorld.kt` | Stealth world contracts |
| `stealth/WeatherPerception.kt` | Weather to perception multipliers |

### `:core:persistence` — `com.example.data`

| File | Purpose |
|------|---------|
| `SaveDataCodec.kt` | Pure-Kotlin save data codecs |
| `PortableSaveEnvelope.kt` | Portable save envelope |
| `InventoryItemRecorder.kt` | `InventoryItemRecorder` fun interface |
| `CombatLootDropSystem.kt` | Loot tables + drop generation |

### `:core:render` — `com.example.data` + `data.svdag`

| File | Purpose |
|------|---------|
| `AsciiPerspectiveRenderer.kt` | ASCII perspective rendering |
| `GameEngineRenderBridge.kt` | `GameEngine.render3DPerspective` extension bridge for feature modules |
| `svdag/SparseVoxelDag.kt` | SVDAG voxel structures, `VoxelType`, stats |
| `svdag/ProceduralCyberWorldGenerator.kt` | Procedural 3D world generation |
| `svdag/SvdagWorldBuilder.kt` | World building, ICE entities, player hide status |
| `svdag/SvdagScannerService.kt` | Scanner logic, summaries, ripples |
| `svdag/SvdagIcePathfinder.kt` | ICE patrol pathfinding, alert levels |
| `svdag/FpeProbabilityField.kt` / `FpeRenderMode.kt` / `FpeWorldRules.kt` | First-person probability field world rules |

### `:core:game` — `com.example.ui`

| File | Purpose |
|------|---------|
| `GameUiState.kt` | The single-source-of-truth state class |
| `ActiveScreen.kt` | Navigation enum |
| `CombatTurn.kt` | `PLAYER`/`ENEMY`/`ANIMATING` |
| `CombatOpening.kt` | `STANDARD`/`AMBUSHED`/`CAUGHT` combat openings |
| `CombatHackingPatternState.kt` | In-combat breach protocol state |
| `QueuedCombatAction.kt` | Queued combat action contracts |
| `WorldClockTicker.kt` | Real-time tick source |
| `CombatClockController.kt` | Combat clock controller |
| `StealthSessionController.kt` | ICE stealth session controller: weather, patrols, breach acoustics, spike/disable, vent navigation, field acoustics |

### `:core:audio` — `com.example.audio`

| File | Purpose |
|------|---------|
| `GameAudioPlayer.kt` | The audio port (pure JVM) |

### `:core:platform` — Android persistence + adapters

| File | Purpose |
|------|---------|
| `data/GameDatabase.kt` | Room database v6: the 4 save DAOs + `@Database` (10 entities) + `MIGRATION_1_2` |
| `data/SaveRepository.kt` | `SaveRepository` interface + `GameRepository` implementation (takes the 4 save DAOs) |
| `data/PlayerEntity.kt`, `GridGameStateEntities.kt`, `FloorMapEntities.kt` | Entity declarations: save-stack entities + the 6 legacy schema-stability entities (no DAOs for the legacy ones) |
| `data/PortableSavePayload.kt` | Portable save payload + JSON codec |
| `data/LegacySaveStorage.kt` | Legacy save storage contracts |
| `data/ContentModLoader.kt` | Mod content loader |
| `data/GameModels.kt` | Type aliases for the split-package migration |
| `ui/PersistenceManager.kt` | Save/load, serialization, lifecycle, leaderboard, export/import |
| `ui/LegacySaveStateCodec.kt` | SharedPreferences contract |
| `ui/PortableSaveStateMapper.kt` | State to portable payload mapping |
| `audio/CyberGameAudioPlayer.kt` | `GameAudioPlayer` adapter |
| `audio/CyberSoundEffectsManager.kt` | PCM BGM synthesis (AudioTrack) |
| `audio/CyberSoundPoolManager.kt` | SoundPool SFX |
| `audio/CyberVibrationManager.kt` | Haptics |

### `:core:designsystem`

| File | Purpose |
|------|---------|
| `ui/theme/Color.kt` / `Theme.kt` / `Type.kt` | `NetcrawlerTheme` Compose theme |
| `ui/components/FlickeringCrtScanlineTerminalOverlay.kt` | CRT scanline overlay |

## Feature Libraries

| Module | File(s) | Purpose |
|--------|---------|---------|
| `:feature:skilltree` | `SkillTreeManager.kt` | Skill-point allocation, node learn/reset, stat application |
| `:feature:combat` | `CombatManager.kt` | Turn-based combat: openings, status effects, bosses, loot, hacking hooks |
| `:feature:exploration` | `ExplorationManager.kt` | Movement, weather, level navigation, SVDAG orchestration, terminal commands |
| `:feature:cosmetic` | `CosmeticVaultManager.kt` | Data fragments, themes, prompt styles, performance buffs |
| `:feature:inventory` | `InventoryManager.kt` | Items, equipment, shops, cyberware/clinic, implants, scavenging |
| `:feature:hacking` | `HackingViewModel.kt`; screens: `CyberHackingMinigameSuite`, `HackingMinigableView`, `MatrixHackingTerminalScreen`, `PatternMatchingHackingMiniGame`; components: `CombatHackingMinigameView`, `HackingPuzzleMiniGameView` | Hacking minigames |
| `:feature:cybergl` | `gl/CyberCharacterGLView.kt`, `gl/CyberCharacterRenderer.kt` | OpenGL 3D character renderer |
| `:feature:widgets` | components: `CombatRoundClockHud`, `CyberNotification`, `CyberVitalStatusHud`, `VisualTurnIndicator`; screens: `GlitchOverlays`, `ProgressBarRetro`, `SharedWidgets` | Shared HUD widgets |
| `:feature:exploration-ui` | `FirstPersonPerspectiveCanvas.kt`, `RenderMiniMap.kt` | First-person corridor renderer + minimap |
| `:feature:svdag-inspector` | `SvdagWorldInspectorScreen.kt`, `MultiFloorLevelInspectorOverlay.kt` | SVDAG world inspector |
| `:feature:menus` | `TutorialOverlay.kt`; screens: `StartMenuView`, `CharacterCreationView`, `GameOverView`, `LeaderboardView`, `SettingsView`, `CyberneticsClinicView`, `CyberwareInventoryOverlay`, `DataVaultScreen`, `TerminalHeader`, `BottomNavigation` | Menu screens (start, creation, settings, game over, leaderboard, clinic, data vault, overlays) |
| `:feature:terminal` | screens: `TerminalScreen` (router), `UpgradeStoreView` | Terminal router + upgrade store |
| `:feature:gamevm` | `GameViewModel.kt` | The coordinator: owns `MutableStateFlow<GameUiState>`, wires managers via callbacks, hacking mini-game entry, XP/leveling |

## Tests

Unit tests live next to their module (JVM `test` task for pure modules, `testDebugUnitTest` for Android library modules):

| Location | Suites |
|----------|--------|
| `:core:engine` | `StealthSimulationTest`, `WeatherPerceptionTest`, `LabyrinthStealthSessionTest`, `LabyrinthStealthSessionWeatherTest`; combat rules suite (`Combat*RulesTest`, `CombatRoundSchedulerTest`, `CombatRoundClockTest`); exploration rules suite; `EnemyFactoryTest`, `RealTimeGameSessionTest`, `TurnBasedCombatEngineTest`, `NameGeneratorTest`, `SkillTreeTest`, domain/contracts/models tests |
| `:core:game` | `StealthSessionControllerTest`, `CombatClockControllerTest`, `WorldClockTickerTest` |
| `:core:content` | `ContentModParserTest` |
| `:core:persistence` | `CombatLootDropSystemTest`, `PortableSaveEnvelopeTest`, `SaveDataCodecTest` |
| `:core:platform` | `PortableSaveStateMapperTest`, `LegacySaveStateCodecTest`, `LegacySaveStorageTest`, `PortableSaveJsonCodecTest` (Robolectric), `SchemaV6CompatibilityTest` |
| `:feature:combat` | `CombatManagerTest` |
| `:feature:exploration` | `ExplorationManagerTest` |
| `:feature:inventory` | `InventoryManagerTest` |
| `:app` | `GameEngineTest` (facade), `ExampleUnitTest`, `ExampleRobolectricTest`, `GreetingScreenshotTest` |

Test framework: JUnit 4, Robolectric, Roborazzi (screenshot capture), Espresso, Compose UI test.

## Build & CI

| Path | Purpose |
|------|---------|
| `.github/workflows/ci.yml` | `testDebugUnitTest` + per-module JVM `test` tasks, `lintDebug`, `assembleDebug` |
| `settings.gradle.kts` | Module registry (23 modules) |
| `gradle/libs.versions.toml` | Gradle version catalog |
| `core/platform/schemas/` | Exported Room schema JSON (schema v6) |
