# Netcrawler — Modding Guide

Add new **enemies, items, and programs** without touching any Kotlin code: drop a `.md`
file into `app/src/main/assets/mods/` and it is parsed and registered at startup.

The system is powered by two files:

- `data/ContentModParser.kt` — the reusable, dependency-free Markdown parser.
- `data/ContentRegistry.kt` — converts parsed blocks into the game's model types and
  merges them into the live registries (`GameEngine`, `GameItemRegistry`,
  `PersistenceManager.getProgramById`).

## How it works

1. Every content item is a `## Kind: Name` Markdown heading.
2. Follow it with `key: value` lines (one per line) for your stats.
3. Write a short description as plain prose, or use a `desc:` field.
4. For enemies, put ASCII art in a fenced code block (``` ``` ```).

At launch, `MainActivity` calls `ContentModLoader.loadFolderMods(context)`, which reads
every `*.md` under `assets/mods/`, runs them through the parser, and registers the
content. No recompilation is needed — just re-add the file and rebuild the APK.

## Supported content types

### Enemies

```
## Enemy: HoneyBadger.exe
tier: 4
hp: 1.5
shield: 1.2
damage: 1.7
armor: 4
bounty: 1.6
status: POISONED:3, BUFFED:2
desc: A vicious grind-daemon that chews through firewall plating.

Portrait:

```
  /\/\/\/\
 ( (0)(0) )
  \  \\  /
   \_||_/
```
```

| Field | Meaning | Default |
|-------|---------|---------|
| `tier` | Minimum depth/layer before this enemy can spawn | `1` |
| `hp` | Integrity multiplier | `1.0` |
| `shield` | Shield multiplier | `1.0` |
| `damage` | Attack-damage multiplier | `1.0` |
| `armor` | Flat armor bonus | `0` |
| `bounty` | Credit-bounty multiplier | `1.0` |
| `status` | Comma-separated starting status effects, `TYPE:turns` | none |
| `desc` | Flavor/description | `"A mod-registered..."` |
| *fence* | ASCII portrait | generic |

`status` types: `STUNNED`, `POISONED`, `BUFFED`, `WEAKENED`, `FORTIFIED`.

### Items

```
## Item: OverclockSerum.exe
category: CONSUMABLE
rarity: EPIC
icon: 🔥
value: 400
status: BUFFED:4:true
desc: Street-lab booster pushing your core to dangerous overclock.
```

| Field | Meaning | Default |
|-------|---------|---------|
| `category` | `CONSUMABLE` / `EQUIPMENT` / `KEY_ITEM` / `RESOURCE` | `RESOURCE` |
| `rarity` | `COMMON` / `UNCOMMON` / `RARE` / `EPIC` / `LEGENDARY` | `COMMON` |
| `icon` | Emoji/glyph | `📦` |
| `value` | Credit value | `50` |
| `slot` | `WEAPON` / `ARMOR` / `CYBERWARE` / `UTILITY` (for equipment) | none |
| `dmg`, `def`, `ram`, `integrity` | Equippable stat bonuses | `0` |
| `heal` | HP restored on use | `0` |
| `ramrestore` | RAM restored on use | `0` |
| `credits` | Credits granted on use | `0` |
| `xp` | XP granted on use | `0` |
| `status` | `TYPE:turns:self(true/false)` consumable status effect | none |
| `desc` | Description | `"Custom mod item."` |

### Programs

```
## Program: KillSwitch.bin
ram: 4
damage: 40
cooldown: 2
pierce: true
desc: Emergency root-level kill switch dealing 40 raw piercing damage.
```

| Field | Meaning | Default |
|-------|---------|---------|
| `ram` | RAM cost | `1` |
| `damage` | Payload damage | `0` |
| `shield` | Shield restore | `0` |
| `heal` | Integrity restore | `0` |
| `cooldown` | Cooldown turns | `0` |
| `pierce` | `true` to bypass armor | `false` |
| `status` | `TYPE:turns:self` status effect | none |
| `magnitude` | Status-effect magnitude | `0` |
| `desc` | Description | `"Custom mod program."` |

## Loading and merging

Mod content is **merged on top of** the built-in content:

- Enemy archetypes are appended to `GameEngine`'s catalog and respected by the
  depth-weighted spawner.
- Items are appended to `GameItemRegistry` (lookups and exploration drops include them).
- Programs are registered and resolved by `PersistenceManager.getProgramById`.

A mod never needs to redefine a base entry to extend the game.

## Notes & limitations

- This is the foundation of the mod system: it currently covers **enemies, items, and
  programs**. Terminals/dialogue, weather, and cosmetic themes are not yet markdown-driven.
- Content is static data only — it cannot add brand-new *behavior* (that still needs Kotlin).
- Because there is no Gradle wrapper on this dev machine, compilation must be verified in
  Android Studio (`./gradlew assembleDebug`).
- To clear loaded mods during a session, remove the `.md` files and rebuild — the sample
  `assets/mods/sample_mod.md` is harmless but can be deleted.

See `assets/mods/sample_mod.md` for a fully worked example of every supported type.
