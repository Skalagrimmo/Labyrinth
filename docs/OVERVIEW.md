# Netcrawler — Project Overview

**Netcrawler** is a fully offline, first-person-perspective **cyberpunk roguelike** Android game. You play a *netrunner* jacked into corporate intranets, hacking terminals, fighting sentient firewall programs ("ICE"), collecting loot, and customizing your runner with cyberware implants as you descend through procedurally generated cyberspace.

This document gives a high-level introduction to the game. For in-depth technical details, see the other documents in this `docs/` folder.

---

## Table of Contents

- [The Pitch](#the-pitch)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [What It Looks Like](#what-it-looks-like)
- [The Player Experience](#the-player-experience)
- [Documentation Index](#documentation-index)

---

## The Pitch

Corporate mainframes are protected by layers of *Intrusion Countermeasures Electronics* (ICE). As a rogue netrunner, your job is to break in, harvest classified data, and reach the **Metro Core** — the heart of the city's cyberspace grid — across three connected zones:

1. **Meat Space (Corp Tower)** — the physical building, floors 1–4
2. **Cyber Space (Sub-Grid Collectors)** — underground data collectors
3. **Cyber Space (The Metro Core)** — the final city grid

Every run is procedurally generated, so no two descents are alike.

---

## Key Features

### 7 Netrunner Classes
Each class has a unique passive, base stats, and a signature starting weapon:

| Class | Passive | Base HP | Base RAM |
|-------|---------|---------|----------|
| **Netrunner** | +50% RAM recovery, +25% hack success | 90 | 22 |
| **Street Samurai** | Starts with 30% shield, +25% crit, 2.0x crit dmg | 160 | 8 |
| **Techie** | Starts with 300 credits, +5 defense | 120 | 14 |
| **Code Slasher** | Crits deal 1.5x on damaged enemies | 100 | 12 |
| **Cyber Shield** | Starts combat with a 30% shield | 150 | 8 |
| **Buffer Overflow** | Spends 2x RAM to run programs twice | 80 | 24 |
| **Script Kiddie** | Starts with 3 premium utilities + 250 credits | 110 | 10 |

### Turn-Based Combat
Combat is **entirely turn-based** (no real-time action). Each turn you can:

- **Strike** — a melee attack scaled by level, damage bonus, and weapon
- **Defend** — fortify and restore shield
- **Hack** — run a timed symbol-matching "breach protocol" for massive damage
- **Scan** — expose enemy stats and stun if it was about to attack
- **Run Programs** — execute RAM-cost scripts (damage, shields, heals, status effects)
- **Use Items** — consume inventory utilities (heals, RAM, credits, buffs)
- **Flee** — attempt to escape the encounter

A full combat **action log** and single-command **terminal interface** accompany every battle:
```
[STRIKE] You strike the Arasaka ICE-Sentinel for 18 damage!
[CALC]: Base:12 + Stats:6 = Raw:18
[ENEMY] Arasaka ICE-Sentinel fires a Trojan injection stream (12 damage).
```

### 3 Destructible Bosses
Three unique, multi-phase bosses guard the portals between zones:

| Boss | Zone Portal | Mechanics |
|------|-------------|-----------|
| **Firewall Sentinel** | Building Floor 4 | Shield regen, System Lockdown stun, phase shift at 50% HP |
| **Daemon Overlord** | Collectors Level 2 | Summons minions, RAM drain, neural devour at 30% HP |
| **Black ICE Colossus** | City final gate | Adaptive plating (damage reduction), neural storm, phase shift |

Each drops a unique **legendary program** on victory.

### Procedural Levels
- **Seed-based maze generation** (`GameEngine.generateMaze`) with diverse architectural "dungeon blocks": grand halls, dome vaults, elevated balconies, vent tunnels, gravity slopes, and staircase hubs
- Multiple cell types: data stores, secret caches, terminals, elevators, portals, virus nodes, and more
- **Three zones** with persistent per-floor/level/district layout maps
- Levels can be **regenerated from a shared seed** (the `seed` terminal command prints the current seed)

### Hacking Mini-Games
A **pattern-matching breach protocol**: select a sequence of symbols across the grid, alternating horizontal/vertical movement, to match a hidden target sequence within a buffer limit. Success extracts credits, data fragments, and items — failure deals feedback damage.

### Cyberware & Implants
- Install **cyberware implants** across **8 body slots** (brain, eyes, arms, legs, nervous system, etc.)
- Implants grant integrity, RAM, recovery, damage, defense, and unique **passive abilities**
- Manage equipped, stored, and installed implants at the clinic / inventory overlay

### Cosmetic Data Fragment Vault
Collect **Data Fragments** from hacks and caches, then spend them to unlock:

- **5 terminal color themes** (Neon Cyberpunk, Matrix Emerald, Retro Amber, Quantum Frost, Obsidian Stealth)
- **5 terminal prompt styles** (Runner, Root Mainframe, Ghost Node, Quantum Core, Black-ICE Breaker)
- **5 performance buffs** (Hyper-RAM, Signal Dampening, Clock-Cycle Extender, Thermal Shield, Data Siphon)

### First-Person Perspective & SVDAG World
- A **3D first-person raycast view** rendered via `render3DPerspective` with weather effects (Gibsonian Data Storm, Cold Spot, Hot Node, Memory Fragmentation, Spectral Echoes)
- An experimental **Sparse Voxel DAG (SVDAG)** 3D cyberspace world builder with voxel terrain, ICE patrol units with pathfinding AI, scanning, and Level-of-Detail rendering
- **OpenGL ES** matrix-rain and character renderers

### Fully Procedural Audio
- All **music is synthesized in real time** via `AudioTrack` PCM synthesis (no audio files)
- Three BGM modes that adapt to gameplay: Exploration drone (85 BPM), Combat synthwave (130 BPM), Hacking data stream (110 BPM)
- Hundreds of dynamic sound effects via a `SoundPool` manager and haptic **vibration** feedback

### Offline-First & Save System
- **100% offline** — no network, no API keys, no `google-services.json` required
- **Dual-layer persistence**: Room SQLite database + SharedPreferences
- **Save/export/import**: export a full save as Base64-encoded JSON (`NETCRAWLER_SAVE_v1:...`), copy to clipboard, and import/restore anywhere

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.2.10 |
| **UI** | Jetpack Compose (Material 3, Compose BOM 2024.09.00) |
| **Architecture** | MVVM with a coordinator `GameViewModel` delegating to focused managers |
| **Persistence** | Room (SQLite) 2.7.0, KSP codegen |
| **Concurrency** | Kotlin Coroutines 1.10.2 |
| **Rendering** | Canvas 2D (perspective) + OpenGL ES (matrix/character) |
| **Audio** | `AudioTrack` (PCM synthesis) + `SoundPool` |
| **Build** | Android Gradle Plugin 9.1.1, Gradle Version Catalog |
| **Testing** | JUnit 4, Robolectric, Roborazzi (screenshot), Espresso |
| **Min / Target SDK** | 24 / 36 |
| **JDK** | 11 |

> ⚠️ **Note:** Several dependencies (Firebase, Retrofit, OkHttp, Moshi, CameraX, Play Services Location, Coil, DataStore, Navigation) and legacy ViewModels (HackingViewModel, GameTurnViewModel, InventoryViewModel, etc.) remain declared in the codebase but are **not wired into the main game flow**. They are either commented out, legacy scaffolding, or available for future use.

---

## What It Looks Like

The entire game is presented as a **cyber-terminal interface**. The main gameplay screen is a CRT-styled terminal with:

- A top **status HUD** (integrity, shield, RAM, credits)
- A **3D perspective window** of the current corridor
- A **mini-map** of the explored area
- A **command-line input** and scrolling **log feed**
- CRT **scanline flicker**, glitch overlays, and color themes

Players navigate with simple commands (`forward`, `turn left`, `interact`) as well as on-screen buttons/gestures.

---

## The Player Experience

1. **Start Menu** → create or resume a runner profile
2. **Character Creation** → pick a class, starting implant, star... allocate points
3. **Explore** the procedurally generated building, hacking terminals, scavenging loot, fighting ICE
4. **Descend** through floors/levels toward the next zone's portal
5. **Defeat bosses** to unlock each subsequent zone
6. **Reach the Metro Core** → defeat the Black ICE Colossus → *CORE GRID TAKEOVER: SUCCESSFUL NETRUN*

Failures and victories are recorded in a **run history / leaderboard**, with XP and level-ups granting more HP, RAM, and damage across runs.

---

## Documentation Index

| Document | Contents |
|----------|----------|
| [OVERVIEW.md](OVERVIEW.md) | You are here |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Code architecture, manager pattern, state flow |
| [GAMEPLAY.md](GAMEPLAY.md) | Detailed mechanics: combat, hacking, levels, bosses, items |
| [CODEBASE.md](CODEBASE.md) | Source-file reference for every layer |
| [DATA-PERSISTENCE.md](DATA-PERSISTENCE.md) | Room schema, save/load, export/import formats |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Building, testing, coding conventions |
