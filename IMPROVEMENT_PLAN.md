# Netcrawler — Improvement Plan

## Overview
This document outlines prioritized improvements for Netcrawler, a cyberpunk roguelike Android game built with Kotlin, Jetpack Compose, Room DB, OpenGL ES, and Gemini AI.

---

## Phase 1: Foundation & Stability (Weeks 1–2)

### 1.1 Complete Firebase Integration
- **Status:** `google-services.json` is missing; `googleServices.missing.passthrough=true` hides the error
- **Action:** Generate `google-services.json` from Firebase Console, enable Analytics + Crashlytics
- **Impact:** Crash reporting, user analytics, crash-free rate monitoring
- **Effort:** 2–4 hours

### 1.2 Add Release Signing Config
- **Status:** No release keystore or signing block in `build.gradle.kts`
- **Action:** Create upload keystore, configure `signingConfigs` for release builds, set up Play Console app listing
- **Impact:** Required for Play Store deployment
- **Effort:** 1–2 hours

### 1.3 Database Migration Strategy
- **Status:** ✅ **DONE (configuration + documented strategy)** — Room schema export enabled so future migrations can be authored/tested
- **Action:**
  - (completed) Set `exportSchema = true` on `GameDatabase` and added `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` in `app/build.gradle.kts` so Room writes schema JSON to `app/schemas/` for `MigrationTestHelper`.
  - (completed) Documented the migration workflow + create-a-fresh-DB fallback + legacy-gap behavior in `docs/DATA-PERSISTENCE.md`.
  - (deferred) Author `Migration(2,3)…(5,6)` objects — blocked: no historical schema snapshots exist for versions 2→6 (they weren't exported). Once `app/schemas/` is populated by a build, add explicit migrations + a `MigrationTestHelper` unit test. Until then, legacy installs rely on the existing `fallbackToDestructiveMigration(true)`.
- **Impact:** Prevents data loss on app updates for future schema changes
- **Effort:** (config done; migrations need schema snapshots + a working build)

### 1.4 Remove Dead Code
- **Status:** ~1,427 lines of dead composables removed during split (verified)
- **Action:** Confirm no references remain, run `./gradlew lint` to catch any stragglers
- **Impact:** Reduced APK size, cleaner codebase
- **Effort:** 1 hour

---

## Phase 2: User Experience (Weeks 2–4)

### 2.1 Onboarding / Tutorial Flow
- **Status:** ✅ **DONE** — 5-step guided tutorial for new players
- **Action:** (completed) `ui/TutorialOverlay.kt` — non-blocking dialog with 5 steps (movement/swipe, interaction/hack, combat, cyberware clinic) + ASCII illustrations + `[ NEXT ]`/`[ SKIP ]`/tap-to-dismiss; auto-activates in `GameViewModel.createCharacter` for new players; `tutorial` terminal command (`tutorial next`, `tutorial skip`); `tutorial_seen` persisted via SharedPreferences + JSON export/import
- **Impact:** Improved first-session retention, reduced confusion
- **Effort:** (completed)

### 2.2 Accessibility Features
- **Status:** Game relies heavily on color (green/cyan/pink) for state communication
- **Action:**
  - Add colorblind mode (patterns/shapes instead of color-only indicators)
  - Adjustable text size (currently hardcoded 7–12sp)
  - Content descriptions on all interactive elements for TalkBack
  - Reduced motion toggle (disable CRT flicker, scanlines, animations)
- **Impact:** Broader audience, Play Store accessibility requirements
- **Effort:** 1–2 weeks

### 2.3 Localization (i18n)
- **Status:** All UI strings are hardcoded in English
- **Action:** Extract all strings to `strings.xml`, use `stringResource()` throughout Compose code. Prioritize: Japanese, Spanish, Portuguese, German
- **Impact:** International market reach
- **Effort:** 2–3 weeks (extraction + translation)

### 2.4 Improved Character Creation
- **Status:** ✅ **DONE (partial)** — random cyberpunk name generator added
- **Action:**
  - (completed) Random character name generator (`data/NameGenerator.kt`) + `[ SURGE_ALIAS ]` button and 3 suggestion rows in `CharacterCreationView` + `NameGeneratorTest.kt`
  - Add class-specific starting abilities/descriptions
  - Show stat impact preview (e.g., "HP will be 170 instead of 100")
  - Animated 3D preview with rotation controls
- **Impact:** More engaging first impression
- **Effort:** 1 week

---

## Phase 3: Gameplay Depth (Weeks 4–8)

### 3.1 Enemy Variety & Boss Fights
- **Status:** ✅ **DONE** — 17 enemy archetypes (4 tiers) + 3 multi-phase bosses added in `GameEngine.kt`
- **Action:** (completed) Enemy archetype catalog with stat multipliers + starting status effects; bosses with `bossPhase`/`turnCounter` AI in `EnemyCombatAIScript.kt`
- **Impact:** Core gameplay depth, replayability
- **Effort:** (completed)

### 3.2 Skill Tree / Progression System
- **Status:** ✅ **DONE** — skill tree with 3 branches (Hacking, Combat, Engineering)
- **Action:** (completed) `data/SkillTreeModels.kt` (data-driven branches + node chains + `combinedEffects`), `ui/SkillTreeManager.kt` (terminal: `skilltree`, `skill learn <BRANCH> <#>`, `skill points`, `skill reset`), 1 skill point per level awarded in `GameViewModel.addExperience`, stats applied to live state on learn, persisted via SharedPreferences + JSON export/import, `SkillTreeTest.kt`
- **Impact:** Long-term progression goals, build diversity
- **Effort:** (completed)

### 3.3 Expanded Procedural Audio
- **Status:** Already has `AudioTrack` PCM synthesis and `SoundPool` SFX — unique differentiator
- **Action:**
  - Dynamic combat music that responds to HP/turn state
  - Environmental audio layers (wind, electronic hum, distant sirens)
  - Distinct sound signatures per enemy type
  - CRT power-on/power-off sound effect
- **Impact:** Immersion, unique selling point vs other roguelikes
- **Effort:** 1–2 weeks

### 3.4 Daily/Weekly Challenge Runs
- **Status:** Seed-based procedural generation already supports deterministic layouts
- **Action:**
  - Generate a daily seed from date (e.g., `date.hashCode()`)
  - Show global leaderboard for daily runs
  - Add "Daily Challenge" button on start menu
  - Display personal best streak
- **Impact:** Competitive retention, social sharing
- **Effort:** 1 week

### 3.5 Consumable & Item Crafting
- **Status:** ✅ **DONE (partial)** — 5 new content-driven consumables via mod system
- **Action:**
  - (completed) New consumables via `assets/mods/new_items.md` (AdrenalineFlicker.exe, RegenMatrix.dll, RAMExpander.pkg, RuinDust.sh, VirusSynthMaker.bin) + `sample_mod.md` (OverclockSerum.exe, ModularPlating.pkg, KillSwitch.bin program) — parsed by `data/ContentModParser.kt`, merged by `ContentRegistry.kt`
  - Add a simple crafting system (combine 2 items at a terminal)
  - Add item rarity tiers (Common, Uncommon, Rare, Legendary)
- **Impact:** Loot-driven motivation, inventory management depth
- **Effort:** 1–2 weeks

---

## Phase 4: Social & Competitive (Weeks 8–12)

### 4.1 Cloud Leaderboard
- **Status:** Local `RunRecord` in Room only
- **Action:**
  - Firebase Realtime Database or Firestore for global leaderboard
  - Google Sign-In for anonymous authentication
  - Filter by: class, floor reached, total kills
  - Weekly/monthly reset with top-10 highlight
- **Impact:** Competition, replayability, community
- **Effort:** 1–2 weeks

### 4.2 Cloud Saves
- **Status:** All saves are local
- **Action:**
  - Upload `GameSaveProgressEntity` to Firestore on save
  - Download on app install (detect existing save)
  - Conflict resolution (latest timestamp wins)
- **Impact:** Multi-device play, backup/restore
- **Effort:** 1 week

### 4.3 Sharing & Social Features
- **Status:** No sharing functionality
- **Action:**
  - Share run summary as image (game over screen)
  - Share daily challenge score
  - "Share your build" for character configurations
- **Impact:** Organic growth, word-of-mouth
- **Effort:** 3–4 days

---

## Phase 5: Architecture & Performance (Ongoing)

### 5.1 Gradle Module Split
- **Status:** Single `app` module with all code
- **Action:**
  ```
  :core:engine        — Pure Kotlin game logic (no Android deps)
  :core:data          — Room entities, DAOs, repositories
  :core:ui            — Shared composables, theme, colors
  :feature:combat     — Combat screen, turn engine
  :feature:hacking    — Hacking minigames
  :feature:exploration — First-person view, minimap, navigation
  :feature:cyberware  — Implant system, clinic
  :app                — MainActivity, DI, integration
  ```
- **Impact:** Faster builds, independent testing, easier onboarding for contributors
- **Effort:** 1–2 weeks

### 5.2 Dependency Injection (Hilt)
- **Status:** Singletons via `getInstance()` everywhere
- **Action:** Add Hilt, convert `GameViewModel`, `CyberSoundEffectsManager`, `CyberSoundPoolManager`, `CyberVibrationManager`, `GameDatabase` to `@Inject` constructor injection
- **Impact:** Testability, lifecycle safety, cleaner code
- **Effort:** 1 week

### 5.3 Performance Profiling
- **Status:** `FirstPersonPerspectiveCanvas` (1265 lines) does heavy Canvas drawing every frame
- **Action:**
  - Profile with Android GPU Inspector
  - Cache static grid/background elements
  - Consider moving 3D wireframe to dedicated OpenGL renderer
  - Lazy-load minimap tiles instead of redrawing entire map
  - Reduce recomposition scope with `derivedStateOf` where possible
- **Impact:** Smoother 60fps on low-end devices
- **Effort:** 1–2 weeks

### 5.4 Testing
- **Status:** 🟡 **In progress** — added host-JVM unit tests for `ContentModParser` and `GameEngine`
- **Action:**
  - Unit tests for `GameEngine`, `TurnBasedCombatEngine`, `SparseVoxelDag` (started: `ContentModParserTest`, `GameEngineTest` in `app/src/test`)
  - More: `TurnBasedCombatEngine`, `CombatLootDropSystem`, enemy AI
  - ViewModel tests with `kotlinx-coroutines-test`
  - Compose UI tests for critical paths (start game → combat → victory)
  - Screenshot tests for all major screens
- **Impact:** Regression prevention, confident refactoring
- **Effort:** 2–3 weeks

### 5.5 CI/CD Pipeline
- **Status:** 🟡 **In progress** — `.github/workflows/ci.yml` added (unit tests → lint → assembleDebug)
- **Action:**
  - GitHub Actions workflow: lint → unit tests → build debug APK → instrumented tests ✅ (workflow added)
  - **Prerequisite:** commit a Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) — generate in Android Studio (`gradle wrapper`), since none is currently tracked
  - Automated release builds on tag push
  - Play Store internal track auto-deploy
- **Impact:** Automated quality gates, faster iteration
- **Effort:** 2–3 days

---

## Phase 6: Growth & Monetization (Weeks 12+)

### 6.1 Cosmetics Monetization
- **Status:** `CosmeticTheme` and `TerminalPromptStyle` systems exist
- **Action:**
  - Add 8–10 premium terminal themes (retro amber, blood red, ice blue, matrix green, etc.)
  - Add custom CRT scanline patterns
  - Price: $0.99–$1.99 per theme, or $4.99 for all
  - Free themes unlock via achievements
- **Impact:** Revenue, player expression
- **Effort:** 1 week (content) + store integration

### 6.2 Mod Support / Custom Content
- **Status:** ✅ **Partially DONE (enemies/items/programs via Markdown)** — see `docs/MODDING.md`
- **Action implemented:** `ContentModParser` (reusable Markdown parser) + `ContentRegistry` load `.md` files from `assets/mods/` at startup, covering enemies, items, and programs. No recompilation needed.
- **Action remaining:** in-app runtime loader from `Documents/Netcrawler/mods/`, community mod sharing
- **Impact:** Community engagement, indefinite content lifespan
- **Effort:** (foundation done; runtime loading 1–2 weeks)

### 6.3 Open Source the Engine
- **Status:** Game engine algorithms (SVDAG, procedural generation, combat engine) are interesting and reusable
- **Action:**
  - Extract `:core:engine` and `:core:data` into a standalone library
  - Publish on GitHub with MIT license
  - Keep game assets and UI proprietary
  - Write documentation for the engine
- **Impact:** Community contributions, brand awareness, portfolio piece
- **Effort:** 1–2 weeks

### 6.4 Play Store Optimization
- **Action:**
  - A/B test screenshots and feature graphic
  - Optimize listing with keywords: "cyberpunk roguelike", "hacking game", "terminal game"
  - Add promo video (30–60s gameplay montage)
  - Respond to all reviews within 24 hours
- **Impact:** Organic install growth
- **Effort:** Ongoing

---

## Priority Matrix

| Priority | Item | Impact | Effort |
|----------|------|--------|--------|
| P0 | Firebase integration | High | Low |
| P0 | Release signing | High | Low |
| P0 | DB migrations | High | Medium |
| P1 | Onboarding tutorial | High | Medium |
| P1 | Enemy variety | High | Medium |
| P1 | Skill tree | High | Medium |
| P2 | Cloud leaderboard | Medium | Medium |
| P2 | Accessibility | Medium | Medium |
| P2 | Localization | Medium | High |
| P3 | Daily challenges | Medium | Low |
| P3 | Procedural audio expansion | Medium | Medium |
| P3 | Gradle modularization | Medium | High |
| P3 | Hilt DI | Medium | Medium |
| P4 | Performance profiling | Medium | Medium |
| P4 | Testing suite | Medium | High |
| P4 | CI/CD | Medium | Low |
| P5 | Cosmetics monetization | Medium | Low |
| P5 | Mod support | High | High |
| P5 | Open source engine | Medium | Medium |

---

## Estimated Timeline

| Phase | Weeks | Focus |
|-------|-------|-------|
| Phase 1 | 1–2 | Stability & deployment readiness |
| Phase 2 | 2–4 | UX, onboarding, accessibility |
| Phase 3 | 4–8 | Gameplay depth, progression, audio |
| Phase 4 | 8–12 | Social features, competition |
| Phase 5 | Ongoing | Architecture, performance, testing |
| Phase 6 | 12+ | Growth, monetization, community |
