package com.example.data

/**
 * Built-in gameplay content separated from the engine orchestration layer.
 * IDs and values intentionally remain unchanged for save/content compatibility.
 */
object GameContentCatalog {
// Default starting cyberwares list
fun getStoreCyberware(): List<Cyberware> {
    return listOf(
        Cyberware("cpu_oc", "CPU Overclocker", "+2 RAM Recovery Rate", 200, recoveryBonus = 2),
        Cyberware("mem_exp", "RAM Rig Extension", "+4 Max RAM Allocation", 250, ramBonus = 4),
        Cyberware("armor_plt", "Sub-Dermal Firewall", "+30 System Integrity", 180, integrityBonus = 30),
        Cyberware("dmg_mod", "Payload Amplifier", "+5 Attack Damage output", 300, damageBonus = 5),
        Cyberware("def_mod", "Defensive Buffer", "+10% Armor Defense", 220, defenseBonus = 2)
    )
}
 // Starting programs list
fun getStartingPrograms(runnerClass: NetrunnerClass): List<Program> {
    val base = mutableListOf(
        Program("ping", "ping.exe", "Scan enemy process. Deals 10 payload damage.", ramCost = 1, damage = 10),
        Program("firewall", "firewall.sh", "Harden defences. Restore 25 shield points.", ramCost = 2, shield = 25),
        Program("corrode", "acid_corrode.sh", "Inject malware. Deals 12 dmg + Corrodes target (8 DPS x 3 turns).", ramCost = 2, damage = 12, statusEffectToApply = StatusEffectType.POISONED, statusEffectTurns = 3, statusEffectMagnitude = 8),
        Program("stun_pulse", "stun_pulse.exe", "High-voltage surge. Deals 10 dmg + Stuns target for 1 turn.", ramCost = 3, damage = 10, statusEffectToApply = StatusEffectType.STUNNED, statusEffectTurns = 1),
        Program("overclock", "overclock.sys", "Overclock system core. Grants Overclocked buff (+50% attack dmg for 2 turns).", ramCost = 3, statusEffectToApply = StatusEffectType.BUFFED, statusEffectTurns = 2, statusEffectTargetSelf = true),
        Program("glitch", "glitch_payload.bin", "Scramble target sensors. Glitches target (-50% damage output for 2 turns).", ramCost = 2, statusEffectToApply = StatusEffectType.WEAKENED, statusEffectTurns = 2)
    )
    when (runnerClass) {
        NetrunnerClass.NETRUNNER -> {
            base.add(Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true))
            base.add(Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35))
        }
        NetrunnerClass.STREET_SAMURAI -> {
            base.add(Program("katana_strike", "katana_slash.exe", "Lethal blade strike. Deals 40 physical payload damage.", ramCost = 3, damage = 40))
        }
        NetrunnerClass.TECHIE -> {
            base.add(Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40))
            base.add(Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15))
        }
        NetrunnerClass.CODE_SLASHER -> {
            base.add(Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35))
        }
        NetrunnerClass.CYBER_SHIELD -> {
            base.add(Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40))
        }
        NetrunnerClass.BUFFER_OVERFLOW -> {
            base.add(Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true))
        }
        NetrunnerClass.SCRIPT_KIDDIE -> {
            base.add(Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15))
        }
    }
    return base
}


val enemyArchetypes = listOf(
    // ---------- TIER 1 : Corporate Lobby Daemons (Building floors 1-2) ----------
    EnemyArchetype(
        name = "Worm.exe",
        description = "A fast-replicating data worm gnawing through transmission channels. Weak but persistent.",
        asciiArt = "  ~o~~~~~o~~\n (  o  _  o )\n  ~~o~~~~~o~",
        minTier = 1, dmgMult = 1.15f, bountyMult = 0.9f
    ),
    EnemyArchetype(
        name = "Spyware.dll",
        description = "Stealthy surveillance daemon that siphons RAM and exposes system weaknesses.",
        asciiArt = "  /-------\\\n < (o) (o) >\n  \\_  ^  _/",
        minTier = 1, shieldMult = 0.8f, dmgMult = 1.1f,
        statusEffects = listOf(StatusEffectType.WEAKENED to 2)
    ),
    EnemyArchetype(
        name = "Trojan.Horse",
        description = "A disguised intrusion script that appears harmless until deep inside the network.",
        asciiArt = "  ,_____\n /_ _ _ \\\n |o|   |o|\n |_______|",
        minTier = 1, dmgMult = 1.25f, armorBonus = 2
    ),
    EnemyArchetype(
        name = "ScriptKiddie.Bot",
        description = "An amateur automated script, clumsy but erratic and unpredictable.",
        asciiArt = "  (>_<)\n  (o o)\n  (_|_)",
        minTier = 1, hpMult = 0.8f, dmgMult = 1.0f, bountyMult = 1.1f
    ),
     // ---------- TIER 2 : Building Core / Reactor ----------
    EnemyArchetype(
        name = "LogicBomb.sh",
        description = "A dormant payload rigged to detonate with devastating cascading corruption.",
        asciiArt = "   _\\|/_\n  ( o_o )\n  (_____) ",
        minTier = 2, dmgMult = 1.4f,
        statusEffects = listOf(StatusEffectType.POISONED to 3)
    ),
    EnemyArchetype(
        name = "Ransomware.crypt",
        description = "Locks your core files and demands credits. Armor-tough encryption shell.",
        asciiArt = "  [Locked]\n  [ O_O  ]\n  [=====_]",
        minTier = 2, armorBonus = 3, hpMult = 1.2f, bountyMult = 1.2f
    ),
    EnemyArchetype(
        name = "Rootkit.sys",
        description = "Hides deep in the operating system and erodes your defenses from within.",
        asciiArt = "   /\\_/\\\n  ( >.< )\n   =(I)=",
        minTier = 2, shieldMult = 1.3f, dmgMult = 1.15f,
        statusEffects = listOf(StatusEffectType.WEAKENED to 1)
    ),
    EnemyArchetype(
        name = "Firewall Guardian",
        description = "A sentry program reinforced with heavy subdermal-plated shielding.",
        asciiArt = "  [GUARD]\n  |=o o=|\n  |_____|",
        minTier = 2, shieldMult = 1.6f, hpMult = 1.3f, armorBonus = 2, dmgMult = 0.9f
    ),
     // ---------- TIER 3 : Collector Sub-Grid ----------
    EnemyArchetype(
        name = "ZombieBot.bin",
        description = "A thrall daemon under external control, relentless but mentally fragmented.",
        asciiArt = "  [Z][Z]\n  ( x_x )\n  (_|_|_)",
        minTier = 3, hpMult = 1.4f, dmgMult = 1.2f,
        statusEffects = listOf(StatusEffectType.BUFFED to 2)
    ),
    EnemyArchetype(
        name = "VampirePacket.sys",
        description = "Drains your RAM reserves to fuel its own corrupted data stream.",
        asciiArt = "  \\\\____//\n   ( o_o )\n  __|___|__",
        minTier = 3, hpMult = 0.9f, dmgMult = 1.25f, shieldMult = 1.2f
    ),
    EnemyArchetype(
        name = "Adware Construct",
        description = "A bloated, loud daemon that hammers you with overwhelming corrupt spam.",
        asciiArt = "  [AD!]\n  ( O_o )\n  |>_<|",
        minTier = 3, hpMult = 1.3f, dmgMult = 1.35f, bountyMult = 1.3f
    ),
    EnemyArchetype(
        name = "Scav-Killer.exe",
        description = "A hardened hunter that preys on weaker black-ice, relentless and fast.",
        asciiArt = "   ,___,\n  < o o >\n   \\_|_/",
        minTier = 3, dmgMult = 1.5f, armorBonus = 2, hpMult = 1.1f
    ),
     // ---------- TIER 4 : City Metro Districts ----------
    EnemyArchetype(
        name = "Cryptolocker.Baron",
        description = "An elite ransomware general that enslaves your files for massive ransoms.",
        asciiArt = " [BARON]\n [  ]|[ ]\n |_____|",
        minTier = 4, hpMult = 1.6f, armorBonus = 4, bountyMult = 1.5f
    ),
    EnemyArchetype(
        name = "IceWyrm.sys",
        description = "A colossal serpentine ICE construct that coils and strikes with venomous payloads.",
        asciiArt = " ~~~~~~\n<((( )))>\n ~~~^~~~",
        minTier = 4, hpMult = 1.5f, dmgMult = 1.4f, shieldMult = 1.2f,
        statusEffects = listOf(StatusEffectType.POISONED to 4)
    ),
    EnemyArchetype(
        name = "Overlord Lieutenant",
        description = "A Daemon-Overlord sub-commandant that buffs any black-ice around it.",
        asciiArt = " [LIEUT]\n  ( O,O )\n  |_____|",
        minTier = 4, hpMult = 1.7f, shieldMult = 1.5f, dmgMult = 1.3f,
        statusEffects = listOf(StatusEffectType.BUFFED to 3)
    ),
    EnemyArchetype(
        name = "Synthwraith.exe",
        description = "A fragment of a long-dead netrunner, glitching between dimensions and stacking curses.",
        asciiArt = "  ( ~ ~ )\n  <  o  >\n   /|_|\\",
        minTier = 4, hpMult = 0.8f, dmgMult = 1.6f, shieldMult = 1.4f,
        statusEffects = listOf(StatusEffectType.WEAKENED to 3, StatusEffectType.POISONED to 2)
    ),
    EnemyArchetype(
        name = "BlackICE Berserker",
        description = "A rage-maddened ICE unit that forgoes defense for pure, overwhelming offense.",
        asciiArt = "  [ RAGE ]\n  ( >_< )\n  ((:=))",
        minTier = 4, dmgMult = 1.8f, hpMult = 1.2f, shieldMult = 0.6f,
        statusEffects = listOf(StatusEffectType.BUFFED to 2)
    )
)


}
