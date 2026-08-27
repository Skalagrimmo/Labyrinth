# Netcrawler Sample Mod

This sample demonstrates the markdown content format used by `ContentModParser`.
Every content item is a `## Kind: Name` heading followed by `key: value` lines.
Descriptions may be written as plain markdown (emphasis/backticks are stripped).
Enemy ASCII portraits go in a fenced ``` block.

---

## Enemy: HoneyBadger.exe

tier: 4
hp: 1.5
shield: 1.2
damage: 1.7
armor: 4
bounty: 1.6
status: POISONED:3, BUFFED:2
desc: A vicious grind-daemon that chews through firewall plating and corrodes anything it touches.

Portrait:

```
  /\/\/\/\
 ( (0)(0) )
  \  \\  /
   \_||_/
```

HoneyBadger.exe is relentless, stacking corrosion on targets each turn it remains alive.

---

## Enemy: GhostPacket.sys

tier: 2
shield: 0.5
damage: 1.4
armor: 2
status: WEAKENED:2
desc: A spectral data fragment that debuffs (glitches) whatever it engages.

Phantom of a lost netrunner — starts *Glitched* itself but hits surprisingly hard.

---

## Item: OverclockSerum.exe

category: CONSUMABLE
rarity: EPIC
icon: 🔥
value: 400
status: BUFFED:4:true
desc: Street-lab booster pushing your core to dangerous overclock (+50% damage for 4 turns).

Apply a potent *Overclocked* buff to yourself.

---

## Item: ModularPlating.pkg

category: EQUIPMENT
rarity: RARE
icon: 🛡️
slot: ARMOR
value: 650
integrity: 30
def: 5
desc: Modular composite armor plating offering superior native defense.

Hardened packet-filtering mesh granting +30 Max Integrity and +5 Defense.

---

## Program: KillSwitch.bin

ram: 4
damage: 40
cooldown: 2
pierce: true
desc: Emergency root-level kill switch dealing 40 raw piercing payload damage.

Force-terminate a hostile process, bypassing armor entirely.
