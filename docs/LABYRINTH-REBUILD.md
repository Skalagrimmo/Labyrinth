# Labyrinth Rebuild Baseline

This branch is the controlled rebuild of the existing Android game into **Labyrinth**.

## Baseline

- Source branch: `main`
- Baseline commit: `475488a837ecaada38805b99f0bbaf0c11676ebd`
- Android: minSdk 24, targetSdk 36, compileSdk 36.1
- Kotlin: 2.2.10
- AGP: 9.1.1
- Compose + Material 3
- Room 2.7.0 / schema v6
- KSP 2.3.5
- Coroutines 1.10.2
- OpenGL ES + Compose Canvas render paths
- Gradle wrapper and GitHub Actions CI are present

## Preserve during rebuild

1. Deterministic procedural maze / multi-floor generation.
2. First-person exploration and minimap.
3. Turn-based combat and enemy AI.
4. Offline-first operation.
5. Save/import/export compatibility where practical; schema v6 is the migration baseline.
6. Markdown-driven content/mod parsing.
7. SVDAG/FPE experiments behind clean interfaces rather than mixed into UI state.
8. Low-spec Android support (minSdk 24).

## Architectural target

The current repository is a single large `:app` module with several oversized coordinator/UI files. Rebuild toward explicit boundaries:

- `:core:model` — pure Kotlin domain state and IDs.
- `:core:engine` — deterministic exploration, generation, combat rules.
- `:core:persistence` — Room + save codecs and migrations.
- `:core:content` — registries and Markdown content loading.
- `:core:render` — renderer contracts and shared scene models.
- `:feature:exploration`
- `:feature:combat`
- `:feature:hacking`
- `:feature:inventory`
- `:app` — Android composition root.

Migration will be incremental: extract pure logic first, add characterization tests, then replace legacy coordinators. The branch must remain buildable at migration checkpoints.

## First rewrite milestones

1. Characterization tests for save/import, seeded generation, movement and combat.
2. Extract domain models from Android/UI dependencies.
3. Split GameEngine generation/combat responsibilities.
4. Introduce a single explicit game-session state machine.
5. Move persistence behind interfaces and lock schema-v6 compatibility tests.
6. Replace monolithic Terminal/Exploration UI with feature screens consuming immutable state.
7. Consolidate Canvas/OpenGL/SVDAG render inputs behind renderer-neutral scene data.
8. Rename remaining Netcrawler-facing project metadata to Labyrinth only after compatibility-critical identifiers are audited.

## Guardrails

Do not change `applicationId`, Room identity/schema, save keys, serialized field names, or deterministic seed behavior casually. Any such change needs a compatibility test or migration.
