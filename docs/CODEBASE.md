# Netcrawler — Codebase Reference

A complete source-file reference organized by package and layer. All paths are relative to `app/src/main/java/com/example/`.

---

## Table of Contents

- [Entry Point & Resources](#entry-point--resources)
- [UI Layer (`ui/`)](#ui-layer-ui)
  - [Managers](#managers)
  - [Shared Types](#shared-types)
  - [Screens](#screens)
  - [Components](#components)
  - [Theme](#theme)
  - [Legacy ViewModels](#legacy-viewmodels)
- [Data Layer (`data/`)](#data-layer-data)
- [SVDAG (`data/svdag/`)](#svdag-data-svdag)
- [Audio (`audio/`)](#audio-audio)
- [OpenGL (`gl/`)](#opengl-gl)
- [Tests](#tests)

---

## Entry Point & Resources

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Activity hosting Compose; sets `NetcrawlerTheme`, creates `GameViewModel`, renders `TerminalScreen` |
| `AndroidManifest.xml` | Permissions (`MODIFY_AUDIO_SETTINGS`), launcher activity, network security config, icon |
| `assets/shaders/*.glsl` | OpenGL shaders (matrix vertex, digital-rain fragment) |
| `res/` | Icons (`app_icon_netcrawler.jpg`, mipmaps), colors, strings, themes, backup/data-extraction/network rules |
| `gradle/libs.versions.toml` | Gradle Version Catalog (all dependency versions) |
| `app/build.gradle.kts` | Module build configuration, signing, SDK, dependencies |
| `build.gradle.kts`, `settings.gradle.kts` | Root build & plugin management |

---

## UI Layer (`ui/`)

### Managers (the core game logic)

| File | Responsibility |
|------|----------------|
| `GameViewModel.kt` (~500) | **Coordinator.** Owns `MutableStateFlow<GameUiState>`, constructs all managers, delegates public API, hacking mini-game, XP/leveling, shared callbacks |
| `CombatManager.kt` (~620) | Turn-based combat: attack/defend/hack/scan/programs/items, enemy AI invocation, status effects, bosses, victory/defeat, loot |
| `ExplorationManager.kt` (~1,440) | Movement, turns, weather events, enemy spawns, level load/create, elevators/stairs, portals (incl. boss triggers), map scan, SVDAG orchestration, terminal commands |
| `InventoryManager.kt` (~650) | Items/consumables, equipment slot management, shops, cyberware, cybernetics clinic, implants, scavenging |
| `PersistenceManager.kt` (~800) | Save/load, serialization, game lifecycle, leaderboard, high scores, run records, save export/import, clipboard |
| `CosmeticVaultManager.kt` (~170) | Data fragments, cosmetic themes, prompt styles, performance buffs, Data Vault navigation |

### Shared Types

| File | Purpose |
|------|---------|
| `ActiveScreen.kt` | `ActiveScreen` navigation enum (START_MENU, CHARACTER_CREATION, EXPLORATION, COMBAT, HACKING_MINIGAME, UPGRADE_STORE, LEADERBOARD, GAME_OVER, CYBERWARE_CLINIC, SVDAG_WORLD_BUILDER, DATA_FRAGMENTS_VAULT) |
| `CombatTurn.kt` | `CombatTurn` enum (PLAYER, ENEMY, ANIMATING) |
| `CombatHackingPatternState.kt` | Data class for the in-combat breach-protocol minigame state |

### Screens

| File | Purpose |
|------|---------|
| `screens/TerminalScreen.kt` | The main game screen — hosts the terminal, HUD, perspective view, and routes across activity |
| `screens/StartMenuView.kt` | Start menu (new run / resume / leaderboard) |
| `screens/CharacterCreationView.kt` | Character + class + implant + stat allocation |
| `screens/ExplorationView.kt` | Exploration HUD and 3D perspective + context bar |
| `screens/CombatScreen.kt` *(note: combat UI largely inlined in TerminalScreen/ExplorationView)* | Combat screen composables |
| `screens/HackingMinigableView.kt` | Hacking mini-game screen |
| `screens/UpgradeStoreView.kt` | Shop/upgrade console |
| `screens/LeaderboardView.kt` | Run history / leaderboard |
| `screens/GameOverView.kt` | Game over / victory screen |
| `screens/CyberneticsClinicView.kt` | Cyberware clinic |
| `screens/DataVaultScreen.kt` | Data fragment cosmetic vault |
| `screens/CyberwareInventoryOverlay.kt` | Equip/unequip implant overlay |
| `screens/SvdagWorldInspectorScreen.kt` | SVDAG 3D world inspector |
| `screens/MultiFloorLevelInspectorOverlay.kt` | Multi-floor level inspector |
| `screens/MatrixHackingTerminalScreen.kt` | Matrix hacking terminal variant |
| `screens/CyberHackingMinigameSuite.kt` | Suite of hacking minigame variants |
| `screens/PatternMatchingHackingMiniGame.kt` | Pattern-matching hacking minigame |
| `screens/FirstPersonPerspectiveCanvas.kt` | Canvas-based first-person renderer |
| `screens/RenderMiniMap.kt` | Minimap composable |
| `screens/BottomNavigation.kt` | Bottom navigation bar |
| `screens/SharedWidgets.kt` | Shared UI widgets |
| `screens/GlitchOverlays.kt` | CRT/glitch overlay effects |
| `screens/ProgressBarRetro.kt` | Retro progress bar |
| `screens/TerminalHeader.kt` | Terminal header HUD |

### Components

| File | Purpose |
|------|---------|
| `components/CombatHackingMinigameView.kt` | In-combat hack minigame UI |
| `components/HackingPuzzleMiniGameView.kt` | Pre-combat hacking puzzle UI |
| `components/CyberVitalStatusHud.kt` | Health/shield/RAM/credits HUD |
| `components/CyberNotification.kt` | Notification/alert toast |
| `components/FlickeringCrtScanlineTerminalOverlay.kt` | CRT scanline overlay |
| `components/RoomFloorGridRenderer.kt` | Grid floor renderer |
| `components/VisualTurnIndicator.kt` | Combat turn indicator |

### Theme

| File | Purpose |
|------|---------|
| `theme/Color.kt` | Color palette |
| `theme/Theme.kt` | `NetcrawlerTheme` compose theme |
| `theme/Type.kt` | Typography |

### Legacy ViewModels

| File | Purpose |
|------|---------|
| `GameTurnViewModel.kt` (~900) | Legacy turn/viewmodel logic (not the active path) |
| `HackingViewModel.kt` (~480) | Legacy hacking logic |
| `InventoryViewModel.kt` (~65) | Legacy inventory VM |
| `CharacterProfileViewModel.kt` (~75) | Legacy character profile VM |
| `GameProgressViewModel.kt` (~50) | Legacy progress VM |

---

## Data Layer (`data/`)

| File | Purpose |
|------|---------|
| `GameModels.kt` | Core types: `NetrunnerClass`, `Cyberware`, `StatusEffectType`, `ActiveStatusEffect`, `Program`, `Direction`, `CellType`, `Zone`, `CyberWeather`, `Enemy`, `BossType`, `LogMessage/LogType`, `RunRecord`, `CharacterProfileEntity`, `GameSaveProgressEntity`, `InventoryItemEntity`, `GameState`, `TurnPhase`, `CombatActionType`, `TurnActionRecord` |
| `GameEngine.kt` (~1,680) | Procedural maze generation, 3D perspective raycast, enemy/boss spawning, hacking puzzles, starting program kits, weather |
| `GameItemRegistry.kt` (~320) | Static `GameItem` catalog (consumables, equipment) |
| `ItemModels.kt` | `GameItem`, `EquipmentSlot`, `InventoryCategory`, `InventorySortOption`, `ItemRarity` |
| `CyberwareImplantRegistry.kt` | Implant catalog + starter implants |
| `ImplantModels.kt` | `CyberwareImplant`, `ImplantBodySlot`, `ImplantAbility` |
| `CombatLootDropSystem.kt` | Loot table + drop generation (incl. boss loot) |
| `EnemyCombatAIScript.kt` (~350) | Enemy + boss AI decision logic |
| `TurnBasedCombatEngine.kt` (~400) | Pure-Kotlin immutable combat state machine (reference engine) |
| `ProceduralMatrixLevelGenerator.kt` | Alternative matrix level generator |
| `ProceduralMultiFloorLevelGenerator.kt` | Multi-floor level generator (+ `MultiFloorGridLevel` models) |
| `FloorMapDao.kt`, `FloorMapEntities.kt`, `FloorMapRepository.kt` | Room persistence for floor maps + obstacle navigator |
| `GridGameStateEntities.kt`, `GridGameStateRepository.kt` | Grid state + entity coordinate persistence |
| `PlayerEntity.kt`, `PlayerNpcCoordinatesDao.kt`, `PlayerRepository.kt` | Player + NPC coordinates persistence |
| `GameDatabase.kt` | Room database (10 entities), DAOs, `GameRepository`, migrations |

---

## SVDAG (`data/svdag/`)

| File | Purpose |
|------|---------|
| `SparseVoxelDag.kt` | SVDAG 3D voxel world structures, `VoxelType`, `SvdagStats`, context types |
| `ProceduralCyberWorldGenerator.kt` | Procedural 3D world generation |
| `SvdagWorldBuilder.kt` | World building & modification, ICE entities, player hide status |
| `SvdagScannerService.kt` | Scanner logic, `SvdagScanSummary`, `SvdagRippleState` |
| `SvdagIcePathfinder.kt` | ICE patrol AI pathfinding, `IceAlertLevel`, `IceEntity` |

---

## Audio (`audio/`)

| File | Purpose |
|------|---------|
| `CyberSoundEffectsManager.kt` | Real-time procedural BGM synthesis (`AudioTrack` PCM), music modes, volume, mute |
| `CyberSoundPoolManager.kt` | `SoundPool`-based point-in-time sound effects |
| `CyberVibrationManager.kt` | Haptic feedback management |

---

## OpenGL (`gl/`)

| File | Purpose |
|------|---------|
| `CyberMatrixGLView.kt` / `CyberMatrixRenderer.kt` | OpenGL digital-rain / matrix renderer |
| `CyberCharacterGLView.kt` / `CyberCharacterRenderer.kt` | OpenGL 3D character renderer |

---

## Tests

| Location | Purpose |
|----------|---------|
| `app/src/test/java/com/example/` | `ExampleUnitTest.kt`, `ExampleRobolectricTest.kt`, `GreetingScreenshotTest.kt` |
| `app/src/test/screenshots/greeting.png` | Screenshot test output |
| `app/src/androidTest/java/com/example/` | `ExampleInstrumentedTest.kt` (device/instrumented) |

Test framework: JUnit 4, Robolectric, Roborazzi (screenshot capture), Espresso, Compose UI test.
