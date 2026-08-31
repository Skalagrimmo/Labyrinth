# Netcrawler — Gameplay Guide

A deep dive into how Netcrawler actually plays: character creation, combat, hacking, level progression, bosses, items, and the command interface.

---

## Table of Contents

- [Character Creation](#character-creation)
- [Core Stats](#core-stats)
- [Movement & Exploration](#movement--exploration)
- [Cell Types](#cell-types)
- [The Terminal Command Interface](#the-terminal-command-interface)
- [Combat](#combat)
- [Status Effects](#status-effects)
- [Programs (RAM Scripts)](#programs-ram-scripts)
- [Hacking Mini-Game](#hacking-mini-game)
- [Items & Equipment](#items--equipment)
- [Cyberware & Implants](#cyberware--implants)
- [Zones, Floors & Progression](#zones-floors--progression)
- [Bosses](#bosses)
- [Enemies](#enemies)
- [Weather](#weather)
- [Leveling & XP](#leveling--xp)
- [Data Fragments & Cosmetics](#data-fragments--cosmetics)

---

## Character Creation

When you start a new run you configure:

- **Runner name** — press the **`[ SURGE_ALIAS ]`** button to generate a random cyberpunk alias, or tap one of the three generated suggestions. `data/NameGenerator.kt` compounds prefix/suffix word pairs (e.g. `Ghost Codec`, `Sable Vector`) with an optional title suffix (~25%: `The Quiet`, `Prime`, etc.)
- **Netrunner class** — 7 classes with distinct passives and starting kits (see [Overview](OVERVIEW.md#key-features))
- **Starting implant** — an initial cyberware implant occupying one body slot
- **Stat allocation** — spend points into *HP, RAM, Reflexes (damage), Armor (defense),* and *Funds (credits)*
- **Starter kit** — `STANDARD`, `HACKER`, `COMBAT`, or `SCAVENGER`, granting different starting utility items

The starting values are computed by combining class base stats + chosen implant bonuses + allocated points (see `GameViewModel.createCharacter`).

## Onboarding Tutorial

Brand-new players start with a **5-step guided tutorial** (`ui/TutorialOverlay.kt`) that appears over exploration:

1. **Welcome** — HUD overview (integrity, RAM, credits)
2. **Movement** — swipe to turn/advance
3. **Interaction** — `hack <row> <col>` terminals
4. **Combat** — turn-based `attack` / `defend` / `use <item>`
5. **Cyberware** — `clinic` visits and implant slots

Advance with `[ NEXT ]`, skip with `[ SKIP ]`, or dismiss. You can also re-run it anytime via the terminal: `tutorial next` / `tutorial skip`. Once completed, it's marked `tutorial_seen` and won't reappear.

---

## Core Stats

| Stat | Meaning |
|------|---------|
| **Integrity (HP)** | Your health; reaching 0 ends the run |
| **Shield** | Temporary barrier that absorbs damage before HP |
| **RAM** | Resource spent to run programs; regenerates each combat round at `ramRecoveryRate` |
| **Max RAM** | Increases via class, implants, and level-ups |
| **Credits (MB)** | Currency for shops, implants, and utilities |
| **Damage Bonus** | Flat addition to every attack/program |
| **Defense Bonus** | Flat subtraction from incoming enemy damage |
| **Level / XP** | Progression; level-ups raise HP, shield, damage, and RAM |
| **Data Fragments** | Meta-currency for the cosmetic vault |

---

## Movement & Exploration

- The world is a **grid-based maze**. You occupy `(gridX, gridY)` facing a `Direction` (N/E/S/W).
- You can `moveForward`, `moveBackward`, `turnLeft`, `turnRight`.
- Moving onto special cells triggers events: combat, hacking, loot, zone transitions, elevators, stairs.
- Each step can trigger **weather** events and **random encounters**.
- A **map scan** reveals explored cells and nearby points of interest.

Coordinates and the maze are persisted so ascending/descending returns to previously explored layouts.

---

## Cell Types

Defined in `GameModels.kt` as the `CellType` enum:

| Symbol | Cell | Behavior |
|--------|------|----------|
| `#` | WALL | Blocked |
| `.` | PATH | Walkable |
| `D` | DATA_STORE | Hackable for credits/item |
| `P` | ENCRYPTED_PORTAL | Boss gate → next zone |
| `V` | VIRUS_NODE | Triggers combat |
| `S` | SAFE_ZONE | Access point / rest |
| `C` | SECRET_CACHE | Rich hack, guaranteed keycard on building floor 2 |
| `H` | GRAND_HALL | Pillared hall terrain |
| `O` | DOME_CHAMBER | Round vault terrain |
| `T` | VENT_TUNNEL | Narrow conduit |
| `B` | ELEVATED_BALCONY | +25% attack damage vantage |
| `U`/`N` | STAIRS_UP/DOWN | Floor navigation |
| `L` | GRAVITY_SLOPE | -30% incoming damage |
| `E` | ECHO | Spectral echo data |
| `X` | ELEVATOR | Requires keycard, zone floor travel |
| `W` | SECRET_WALL | Illusory wall |
| `K` | HACKABLE_TERMINAL | Unlocks terminal doors sector-wide |
| `G` | TERMINAL_DOOR | Blocked until terminal hacked |
| `M` | SCAN_CACHE | Quantum stealth cache |
| `Q` | ALTERNATIVE_VENT | Sub-conduit vent |

---

## The Terminal Command Interface

The entire game is driven by a text terminal. Enter commands in the input line (case-insensitive, whitespace-split). Type `help` to see available commands.

### Navigation
`forward`/`w` · `backward`/`s` · `left`/`a` · `right`/`d` · `interact`/`e`

### Combat
`attack` · `defend` · `hack` · `scan` · `flee` · plus program execution

### Inventory
`inventory` · `use <item>` · `equip <item>` · `unequip <slot>`

### System
`status`/`stats` · `save` · `load` · `menu` · `shop` · `clear`

### Hacking
`hack <row> <col>` (during a breach protocol)

### Sharing
`export` (copy save to clipboard) · `import` (paste save) · `seed` (show/share level seed)

Terminal behavior (theme colors, prompt string) is customizable through the Data Fragment Vault.

---

## Combat

Combat is **turn-based** and driven by `CombatManager`. The flow is:

1. **`triggerCombat(x, y)`** — spawns an enemy via `GameEngine.spawnEnemy(level)`, resets combat state, shows a banner, then enables player input.
2. **Player turn** — pick one action.
3. **Resolution** — the action is computed, `TurnActionRecord`s are recorded to the history, and damage/flash/popup animations play.
4. **Enemy turn** — `executeEnemyCombatTurn()` runs enemy AI (normal enemies use `evaluateAction`; bosses use `evaluateBossAction`).
5. **`processTurnMaintenance()`** — increments round, regenerates RAM, clears temp defenses, returns to player input.
6. Victory → `handleCombatVictory()` (loot + XP); defeat → `handleGameOver()`.

### Player Actions

- **Strike** — melee. Damage = weapon base + `(level*2 + damageBonus)`, modified by class passives, *Overclocked*/crit multipliers, balcony bonus, enemy armor, and *Fortified*.
- **Defend** — fortify, restore shield, set `defenseBonus` for the round.
- **Hack** — opens a timed symbol-matching **breach protocol** (`CombatHackingPatternState`) with a pool of symbols, a target pattern, and 3 attempts. Success deals `potentialDamage`; failure triggers a counter.
- **Scan** — reveals enemy stats; if the enemy was preparing an attack, it becomes *Stunned*.
- **Run Program** — spend RAM to deal damage/heal/shield/apply status (see [Programs](#programs-ram-scripts)).
- **Use Item** — consume a utility item; then the enemy turn proceeds.
- **Flee** — attempt to escape (defeat without loot on failure).
- **Pass/End Turn** — skip to the enemy turn.

### Combat Animations & UI

- `combatFlashEnemy` / `combatFlashPlayer` — hit flashes
- `enemyDamagePopup` / `playerDamagePopup` — floating damage numbers
- `showCombatBanner` — "SYSTEM OVERLOAD INTRUSION", "BOSS ENCOUNTER", "VICTORY", "DEFEAT"
- `combatScreenShake`, `enemyAttackCharge` for telegraphing
- `showShieldEffect` for barrier visuals

---

## Status Effects

Defined in `GameModels.kt` as `StatusEffectType`:

| Effect | Icon | Type | Effect |
|--------|------|------|--------|
| **Stunned** | ⚡ | Debuff | Cannot act for the turn duration |
| **Corroded** (Poisoned) | 🧪 | Debuff | Takes digital damage over time each turn |
| **Overclocked** (Buffed) | 🔥 | Buff | +50% amplified attack damage |
| **Glitched** (Weakened) | 🌀 | Debuff | -50% reduced attack damage |
| **Fortified** | 🛡️ | Buff | Reduces all incoming damage by 50% |

Effects are tracked with `turnsRemaining` and decremented in `executeEnemyCombatTurn` / `processPlayerTurnStatusEffects`.

---

## Programs (RAM Scripts)

Programs are installed scripts with a **RAM cost**, optional **cooldown**, and effects. Defined by the `Program` data class:

- `damage`, `shield`, `heal`
- `piercesDefense` (bypasses armor)
- `statusEffectToApply`, `statusEffectTurns`, `statusEffectTargetSelf`, `statusEffectMagnitude`

Base programs per class come from `GameEngine.getStartingPrograms()`. Bosses grant unique legendary programs as rewards:

| Program | Effect |
|---------|--------|
| **SentinelFirewallBreaker.exe** | 50 piercing damage, ignores armor |
| **DaemonSlayer.sys** | 60 damage + 20 RAM restore |
| **ColossusBlade.exe** | 75 damage + stun 2 turns |

RAM costs and cooldowns (`programCooldowns`) gate program usage.

---

## Hacking Mini-Game

The breach protocol is a **pattern-matching grid puzzle** (see `GameViewModel.hackCell`):

1. Start on the top row (row 0).
2. Move **alternating horizontal/vertical** — each move must change the opposite axis from the previous.
3. Build a buffer of selected symbols trying to contain a **target sequence** as a subsequence.
4. Solve within the **buffer limit** to succeed; exceeding it fails.

**Success** → credits (+`100 + level*50`, double for secret caches), random item, experience, and `dataFragments`. **Failure** → feedback damage (`15 + level*5`).

Hacking terminals also unlock all `TERMINAL_DOOR` gates in the sector.

---

## Items & Equipment

Items are defined in `GameItemRegistry.kt` with `category`, `rarity`, and effects:

### Consumables (examples)
- **NanoMed.sys** — +40 HP
- **RAMBoost.exe** — +6 RAM
- **Decryptor.pkg** — +150 credits
- **ChipsetMod.pkg** — +1 permanent damage
- **AntiShield.bin** — +2 permanent damage
- **GibsonForecast.sys** — predict next weather
- **AntiVirus.sys** — purge all debuffs
- **EMPGrenade.bin**, **NanoShield.pkg**, **FirewallBuffer.pkg** — tactical utilities

### Equipment Slots
- **Weapon** (e.g., "Sparksteel Dagger", class-specific cyber-blades)
- **Armor** (e.g., "Basic Firewall Mesh")
- **Utility**

Items are **equippable** or **consumable**. You can sort (`CATEGORY`/`RARITY`/etc.) and filter by category.

### Shops
The **Upgrade Store** (`UPGRADE_STORE` screen) sells consumables and **cyberware**. `purchaseConsumable`, `purchaseCyberware` handle transactions.

---

## Cyberware & Implants

Defined in `CyberwareImplantRegistry.kt` and `ImplantModels.kt`:

- Implants occupy one of **8 body slots** (`ImplantBodySlot`).
- Each implant grants a combination of `integrityBonus`, `ramBonus`, `recoveryBonus`, `damageBonus`, `defenseBonus`, and possibly a **passive ability** (`ImplantAbility`).
- Manage via the **Cybernetics Clinic** (`CYBERWARE_CLINIC` screen) and the **Cyberware Inventory Overlay** (equip/unequip/stored pools).

Classic cyberware stat items (`Cyberware`) are also purchasable and installed.

---

## Zones, Floors & Progression

There are three zones (`Zone` enum), each with persistent per-floor layouts:

| Zone | Levels | Entry |
|------|--------|-------|
| **BUILDING** ("Meat Space (Corp Tower)") | Floors 1–4 | Run start (floor 1) |
| **COLLECTORS** ("Cyber Space (Sub-Grid Collectors)") | Levels 1+ | Defeat the **Firewall Sentinel** at the Building floor-4 portal |
| **CITY** ("Cyber Space (The Metro Core)") | Districts 0+ | Defeat the **Daemon Overlord** at the Collectors portal |

Navigation helpers: `ascendStairs`, `descendStairs`, `interactWithElevator` (elevator requires a **keycard** found on building floor 2), and **encrypted portals** (`ENCRYPTED_PORTAL`) that trigger boss fights.

Levels are generated by `GameEngine.generateMaze` with a **seed** stored in `levelSeed`. The `seed` command shares the seed for deterministic replays.

---

## Bosses

Three bosses, each with unique **multi-phase AI** (`EnemyCombatAIScript.evaluateBossAction`) and defeat→zone-transition logic:

### Firewall Sentinel (Building → Collectors)
- **Phase 1:** Standard attacks + shield regen every 3 turns; "System Lockdown" stuns you for a turn.
- **Phase 2 (≤50% HP):** Restores 40% of max shield, all attacks reflect 50% damage.
- **Loot:** `SentinelFirewallBreaker.exe`

### Daemon Overlord (Collectors → City)
- **Phase 1:** Attacks + RAM-drain every 4 turns (drains buffer).
- **Phase 2 (≤60% HP):** Summons a lesser daemon, healing itself 20% of max HP.
- **Phase 3 (≤30% HP):** "Neural Devour" — high damage, RAM-heavy; also uses Shadow Step.
- **Loot:** `DaemonSlayer.sys`

### Black ICE Colossus (City, final boss)
- **Phase 1:** Heavy attacks.
- **Phase 2 (≤65% HP):** "Adaptive Plating" — +50% shield and 25% incoming-damage reduction.
- **Phase 3 (≤35% HP):** "Neural Storm" — massive AoE; "Phase Shift" every 3 turns; "Core Reboot" heals at low integrity.
- **Defeat →** *CORE GRID TAKEOVER: SUCCESSFUL NETRUN* (game over victory).
- **Loot:** `ColossusBlade.exe`

Boss stats scale with `level` (see `GameEngine.spawnBoss`).

---

## Enemies

Beyond bosses, random encounters spawn from a **catalog of 17 enemy archetypes** in `GameEngine.spawnEnemy`. Each archetype has a unique combat profile — tank, glass-cannon, shield-heavy, debuffer, DOT (corrosion) — set by **stat multipliers**, optional **starting status effects**, and a unique **ASCII portrait**.

Enemies are gated by **tier** (minimum depth). As you descend through floors/levels, tougher archetypes unlock and are weighted to appear more often, so danger scales with progression while variety is preserved.

Stat scaling per archetype: `HP = (40 + layer*15) × hpMult`, `Shield = (15 + layer*10) × shieldMult`, `Damage = (8 + layer*4) × dmgMult`, `Armor = layer + armorBonus`, `Bounty = (50 + layer*25) × bountyMult`.

### Tier 1 — Corporate Lobby Daemons (Building 1–2)

| Enemy | Profile |
|-------|---------|
| **Worm.exe** | Fast-replicating worm; high damage but low bounty |
| **Spyware.dll** | Siphons RAM; starts *Glitched* (weakens you) |
| **Trojan.Horse** | Disguised payload; high damage, +2 armor |
| **ScriptKiddie.Bot** | Unpredictable; low HP, higher bounty |

### Tier 2 — Building Core / Reactor (Building 3–4)

| Enemy | Profile |
|-------|---------|
| **LogicBomb.sh** | Rigged to detonate; starts *Corroded* (DOT) |
| **Ransomware.crypt** | Armor-tough encryption shell (+3 armor), high HP & bounty |
| **Rootkit.sys** | Heavy shield, starts *Glitched* |
| **Firewall Guardian** | Defensive sentry; huge shield, high HP, low damage |

### Tier 3 — Collector Sub-Grid (Collectors)

| Enemy | Profile |
|-------|---------|
| **ZombieBot.bin** | Thrall daemon, relentless; starts *Overclocked* (+50% dmg) |
| **VampirePacket.sys** | Drains RAM; high damage |
| **Adware Construct** | Bloatware; high HP & damage, higher bounty |
| **Scav-Killer.exe** | Hardened hunter; very high damage, +2 armor |

### Tier 4 — City Metro Districts (City)

| Enemy | Profile |
|-------|---------|
| **Cryptolocker.Baron** | Elite ransomware general; high HP, +4 armor, big bounty |
| **IceWyrm.sys** | Serpentine ICE; high all stats, starts *Corroded* (long DOT) |
| **Overlord Lieutenant** | Daemon sub-commandant; starts *Overclocked* |
| **Synthwraith.exe** | Dead-netrunner fragment; extreme damage, starts *Glitched* + *Corroded* |
| **BlackICE Berserker** | Rage unit; +80% damage but -40% shield, starts *Overclocked* |

Starting status effects are honored by the combat engine (POISONED ticks DOT each enemy turn; BUFFED/WEAKENED modify enemy damage output) — see [Status Effects](#status-effects).

---

## Weather

`CyberWeather` events affect the maze/perspective:

| Weather | Effect |
|---------|--------|
| **Clear Bandwidth** | No effect |
| **Gibsonian Data Storm** | Scrambles vectors, blocks line-of-sight |
| **Frozen Sector** | Sluggish movement |
| **Overheated Sub-Grid** | Faster movement, systems overheat |
| **Memory Fragmentation** | Doors/firewall codes shift |
| **Spectral Echoes** | Phantom echo data manifests |

Weather persists for a turn count (`weatherTurnsLeft`). `GibsonForecast.sys` predicts the next event.

---

## Leveling & XP

`GameViewModel.addExperience`:

- Gain XP from combat, hacking, and loot.
- When XP ≥ `xpToNextLevel`, you level up: `+15` Max HP, `+10` Max Shield, `+2` Damage, and occasional RAM gains.
- `xpToNextLevel` formula: `100 + (level-1)*75`.
- Each level also grants **+1 Skill Point**, spendable in the skill tree (see below).

---

## Skill Tree

Type `skilltree` (or `skills`) in the terminal to view the tree and your unallocated points. Spend points with:

```
skill learn <HACKING|COMBAT|ENGINEERING> <node#>
```

- **3 branches** — Hacking, Combat, Engineering — each a linear chain of nodes.
- **Prerequisites** — you must unlock a branch's earlier nodes before later ones.
- Learn a node to apply a **permanent stat bonus** (Max RAM, RAM recovery, damage, defense, Max Integrity) or a one-time **credit grant** to your live run.
- `skill points` shows unallocated points; `skill reset` clears all learned skills (stats already applied remain).
- Skill points and learned nodes are saved with your game (SharedPreferences + save export/import).

---

## Data Fragments & Cosmetics

Collect **Data Fragments** from hacks, secret caches, and boss/ICE kills. Spend them in the **Data Fragments Vault** (`DATA_FRAGMENTS_VAULT` screen / `vault` command) via `CosmeticVaultManager`:

- **5 themes** (Matrix Emerald, Amber Retro, Quantum Frost, Obsidian Stealth, etc.)
- **5 prompt styles**
- **5 performance buffs** (RAM overclock, stealth mask, hack overtime, thermal shield, credit siphon)

Buffs toggle active/inactive and affect gameplay (e.g., `CREDIT_SIPHON` = +25% credit yield).
