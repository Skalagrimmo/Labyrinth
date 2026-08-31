# Netcrawler — New Consumables Mod

Additional loot-driven consumables (plan item 3.5). All effects use the core fields
already supported by the inventory system (heal, ram, credits, xp, damage, status), so
no code changes are required — this file is parsed by ContentModParser at startup.

---

## Item: AdrenalineFlicker.exe

category: CONSUMABLE
rarity: UNCOMMON
icon: ⚡
value: 90
damage: 3
desc: A short-lived combat stimulant that overclocks your attack (+3 temporary Damage).

Nasty street-grade stim. Grants +3 Damage until you leave combat.

---

## Item: RegenMatrix.dll

category: CONSUMABLE
rarity: RARE
icon: 🩺
value: 260
heal: 60
status: FORTIFIED:2:true
desc: Regenerative matrix that heals +60 Integrity and fortifies you (-50% damage taken) for 2 turns.

Rebuilds structural integrity and hardens your firewalls.

---

## Item: RAMExpander.pkg

category: CONSUMABLE
rarity: UNCOMMON
icon: 🧠
value: 110
ramrestore: 10
xp: 25
desc: Volatile memory expansion granting +10 RAM and +25 XP on ingestion.

Frees volatile memory and leaves a skill residue (+25 XP).

---

## Item: RuinDust.sh

category: CONSUMABLE
rarity: EPIC
icon: 🌑
value: 320
status: WEAKENED:3:false
desc: Corrosive dust that Glitches (-50% damage) the targeted enemy for 3 turns.

Siphon-tech slurry that disrupts hostile processes.

---

## Item: VirusSynthMaker.bin

category: CONSUMABLE
rarity: RARE
icon: 👾
value: 280
status: POISONED:4:false
desc: Synthesized corrosive payload that Corrodes the enemy for 4 turns of damage-over-time.

A volatile brew that eats away at enemy integrity.
