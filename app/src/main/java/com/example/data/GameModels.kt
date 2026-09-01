package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NetrunnerClass(
    val title: String,
    val description: String,
    val baseIntegrity: Int,
    val baseRam: Int,
    val passiveDesc: String
) {
    NETRUNNER(
        "Netrunner",
        "Infiltration & Matrix Specialist with high-frequency RAM pools, fast overclock recovery, and instant firewall bypasses.",
        90, 22,
        "Matrix Mastery: +50% RAM recovery rate & +25% Hacking Success Rate"
    ),
    STREET_SAMURAI(
        "Street Samurai",
        "Heavy frontline mercenary equipped with subdermal plating, reflex boosters, and deadly melee critical strikes.",
        160, 8,
        "Chrome & Reflexes: Starts with 30% Shield, +25% Crit Rate & 2.0x Crit Damage"
    ),
    TECHIE(
        "Techie",
        "Master hardware engineer and scavenger specializing in military-grade utilities, automated drones, and heavy starter funding.",
        120, 14,
        "Hardware Scavenger: Starts with 300 credits, bonus utility items, and +5 Defense"
    ),
    CODE_SLASHER(
        "Code Slasher",
        "An aggressive infiltrator utilizing optimized scripts to slice through firewalls and files.",
        100, 12,
        "Overclock: Critical hits deal 1.5x damage on damaged enemies."
    ),
    CYBER_SHIELD(
        "Cyber Shield",
        "A defensive specialist focused on robust system hardening and packet containment.",
        150, 8,
        "Sentinel Protocol: Starts combat with a 30% Integrity shield."
    ),
    BUFFER_OVERFLOW(
        "Buffer Overflow",
        "A volatile runner who sacrifices hardware stability for massive volatile memory pools.",
        80, 24,
        "Exploit Matrix: Spends 2x RAM to execute programs twice."
    ),
    SCRIPT_KIDDIE(
        "Script Kiddie",
        "A lucky generalist who scavenges custom tools and starts with an unpredictable kit.",
        110, 10,
        "Scavenger: Starts with 3 premium utilities and 250 extra credits."
    );

    companion object {
        val PRIMARY_ARCHETYPES = listOf(NETRUNNER, STREET_SAMURAI, TECHIE)
        val VALUES = values()
    }
}

data class Cyberware(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val integrityBonus: Int = 0,
    val ramBonus: Int = 0,
    val recoveryBonus: Int = 0,
    val damageBonus: Int = 0,
    val defenseBonus: Int = 0
)

enum class StatusEffectType(
    val displayName: String,
    val icon: String,
    val isDebuff: Boolean,
    val colorHex: Long,
    val description: String
) {
    STUNNED("Stunned", "⚡", true, 0xFFF59E0B, "Unit cannot act for the turn duration"),
    POISONED("Corroded", "🧪", true, 0xFF10B981, "Takes digital damage over time each turn"),
    BUFFED("Overclocked", "🔥", false, 0xFF3B82F6, "Deals +50% amplified attack damage"),
    WEAKENED("Glitched", "🌀", true, 0xFFEC4899, "Deals -50% reduced attack damage"),
    FORTIFIED("Fortified", "🛡️", false, 0xFF06B6D4, "Reduces all incoming damage by 50%")
}

data class ActiveStatusEffect(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: StatusEffectType,
    val turnsRemaining: Int,
    val magnitude: Int = 0,
    val sourceName: String = ""
)

data class Program(
    val id: String,
    val name: String,
    val description: String,
    val ramCost: Int,
    val cooldownTurns: Int = 0,
    val damage: Int = 0,
    val shield: Int = 0,
    val heal: Int = 0,
    val piercesDefense: Boolean = false,
    val statusEffectToApply: StatusEffectType? = null,
    val statusEffectTurns: Int = 0,
    val statusEffectTargetSelf: Boolean = false,
    val statusEffectMagnitude: Int = 0
)

enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    fun turnLeft(): Direction = VALUES[(ordinal + 3) % 4]
    fun turnRight(): Direction = VALUES[(ordinal + 1) % 4]
    fun turnAround(): Direction = VALUES[(ordinal + 2) % 4]

    companion object {
        val VALUES = values()
    }
}

enum class CellType(val symbol: Char, val displayName: String) {
    WALL('#', "Solid Firewall"),
    PATH('.', "Open Channel"),
    DATA_STORE('D', "Encrypted Data Cache"),
    ENCRYPTED_PORTAL('P', "Sub-Sector Gate"),
    VIRUS_NODE('V', "Active Security Process"),
    SAFE_ZONE('S', "Access Point"),
    SECRET_CACHE('C', "Classified Crypt-Cache"),
    GRAND_HALL('H', "Grand Hall Core"),
    DOME_CHAMBER('O', "Dome Central Vault"),
    VENT_TUNNEL('T', "Narrow Service Conduit"),
    ELEVATED_BALCONY('B', "High-Level Balcony"),
    STAIRS_UP('U', "Vertical Uplink Stairs"),
    STAIRS_DOWN('N', "Sub-Level Downstairs"),
    GRAVITY_SLOPE('L', "Pulsing Gravity Slope"),
    ECHO('E', "Phantom Echo Data"),
    ELEVATOR('X', "Express Elevator Column"),
    SECRET_WALL('W', "Illusory Firewall Wall"),
    HACKABLE_TERMINAL('K', "Locked Access Terminal"),
    TERMINAL_DOOR('G', "Security Gate Door"),
    SCAN_CACHE('M', "Quantum Stealth Cache"),
    ALTERNATIVE_VENT('Q', "Sub-Conduit Vent")
}

enum class Zone(val displayName: String) {
    BUILDING("Meat Space (Corp Tower)"),
    COLLECTORS("Cyber Space (Sub-Grid Collectors)"),
    CITY("Cyber Space (The Metro Core)")
}

enum class CyberWeather(
    val title: String,
    val description: String,
    val colorHex: Long,
    val effectDuration: Int
) {
    CLEAR("Clear Bandwidth", "Data channels are stable and signal loss is minimal.", 0xFF10B981, 0),
    DATA_STORM("Gibsonian Data Storm", "Dense streams of raw telemetric static scramble direction vectors and restrict line-of-sight.", 0xFFEF4444, 8),
    COLD_SPOT("Frozen Sector (Cold Spot)", "System temperature drops to absolute zero. Movements feel sluggish and color registers desaturate.", 0xFF38BDF8, 10),
    HOT_NODE("Overheated Sub-Grid (Hot Node)", "High-voltage processing packets flood the sector. Movement speed is boosted, but systems overheat.", 0xFFF57C00, 10),
    FRAGMENTATION("Memory Fragmentation", "The physical sectors warp and shift. Doors and firewall codes are dynamic.", 0xFFEC4899, 6),
    ECHOES("Spectral Echoes Flow", "Deceased netrunner telemetry fragments materialize as phantom echoes.", 0xFFC084FC, 12),
    APEX_STORM("Apex Firewall Surge", "Layered defensive firewalls flood the sector. Direction vectors scramble wildly and thermal bleed scorches the runner.", 0xFFDC2626, 5),
    GHOST_PROTOCOL("Ghost Protocol Saturation", "Hostile constructs phase out of spectrum. Radar sweeps blank while phased remnants occasionally flicker back into reality.", 0xFF818CF8, 7);

    companion object {
        val VALUES = values()
    }
}

enum class DigitalMutation(
    val title: String,
    val description: String,
    val icon: String,
    val effectSummary: String,
    // Mechanical deltas applied once at character creation (baked into run stats).
    val hpMult: Float = 1.0f,
    val integrityBonus: Int = 0,
    val dmgBonus: Int = 0,
    val defBonus: Int = 0,
    val ramMaxBonus: Int = 0,
    val ramRecoveryBonus: Int = 0,
    val shieldMaxBonus: Int = 0,
    val creditBonus: Int = 0,
    val affinity: String = "ANY"
) {
    GLASS_PROXY(
        "Glass Proxy",
        "A null-body proxy pump that trades structural bulk for blistering output.",
        "🗡️",
        "+4 DMG, +6 Max RAM, but Max Integrity ×0.70",
        hpMult = 0.70f, dmgBonus = 4, ramMaxBonus = 6, affinity = "DPS"
    ),
    CHROME_CARAPACE(
        "Chrome Carapace",
        "Subdermal alloy plating fused directly onto the core chassis.",
        "🛡️",
        "+35 Max Integrity, +4 DEF, but −2 DMG",
        integrityBonus = 35, dmgBonus = -2, defBonus = 4, affinity = "TANK"
    ),
    VOLTAIC_SURGE(
        "Voltaic Surge",
        "Patchwork energy diodes wired into the RAM banks.",
        "⚡",
        "+2 RAM Recovery, +3 Max RAM, but Max Integrity ×0.80",
        hpMult = 0.80f, ramMaxBonus = 3, ramRecoveryBonus = 2, affinity = "RAM"
    ),
    GHOST_INTERFACE(
        "Ghost Interface",
        "A dead netrunner's co-processor, still whispering routing tables.",
        "👻",
        "+1 RAM Recovery, +4 DEF, but −4 Max RAM",
        defBonus = 4, ramMaxBonus = -4, ramRecoveryBonus = 1, affinity = "STEALTH"
    ),
    PREDATOR_CACHE(
        "Predator Cache",
        "Pre-wiped credit slush funds and combat-priority compiler stubs.",
        "🎯",
        "+4 DMG, +150 credits, but −3 Max RAM",
        dmgBonus = 4, ramMaxBonus = -3, creditBonus = 150, affinity = "DPS"
    ),
    REGEN_FRAMEWORK(
        "Regeneration Framework",
        "Borrowed medical mainframe cycles constantly repairing the chassis.",
        "🔋",
        "+20 Max Integrity, +15 Max Shield, but −4 DMG",
        integrityBonus = 20, dmgBonus = -4, shieldMaxBonus = 15, affinity = "TANK"
    ),
    EMPATHIC_LINK(
        "Empathic Link",
        "Fragments of another runner's wetware grafted into your buffers.",
        "🧠",
        "+2 RAM Recovery, +3 DMG, but Max Integrity ×0.85",
        hpMult = 0.85f, dmgBonus = 3, ramRecoveryBonus = 2, affinity = "RAM"
    ),
    AUSTERE_NODE(
        "Austere Node",
        "A stripped-down hardened kernel with generous firewall allocation.",
        "🏰",
        "+10 Max Integrity, +5 DEF, but −1 RAM Recovery",
        integrityBonus = 10, defBonus = 5, ramRecoveryBonus = -1, affinity = "TANK"
    ),
    LIQUID_CREDIT(
        "Liquid Credit",
        "Anonymous crypto slush account hotwired into the starting uplink.",
        "💳",
        "+250 credits, +3 Max RAM, but −5 DEF",
        defBonus = -5, ramMaxBonus = 3, creditBonus = 250, affinity = "CREDITS"
    );

    companion object {
        val VALUES = values()

        /** Rolls up to 3 mutation candidates weighted toward the runner class affinity. */
        fun rollCandidates(runnerClass: NetrunnerClass): List<DigitalMutation> {
            val affinity = when (runnerClass) {
                NetrunnerClass.NETRUNNER, NetrunnerClass.CODE_SLASHER, NetrunnerClass.STREET_SAMURAI -> "DPS"
                NetrunnerClass.TECHIE, NetrunnerClass.SCRIPT_KIDDIE -> "RAM"
                NetrunnerClass.CYBER_SHIELD, NetrunnerClass.BUFFER_OVERFLOW -> "TANK"
            }
            val affinityMatches = VALUES.filter { it.affinity == affinity }
            val others = VALUES.filter { it.affinity != affinity }
            return (affinityMatches + others).take(3)
        }
    }
}

data class Enemy(
    val id: String,
    val name: String,
    val maxIntegrity: Int,
    var integrity: Int,
    val maxShield: Int,
    var shield: Int,
    val damage: Int,
    val armor: Int,
    val iconAscii: String,
    val bountyCredits: Int,
    val description: String,
    var statusEffects: MutableList<ActiveStatusEffect> = mutableListOf(),
    val isBoss: Boolean = false,
    val bossType: BossType? = null,
    val isElite: Boolean = false,
    var bossPhase: Int = 1,
    var turnCounter: Int = 0
)

enum class BossType {
    FIREWALL_SENTINEL,
    DAEMON_OVERLORD,
    BLACK_ICE_COLOSSUS
}

data class LogMessage(
    val text: String,
    val type: LogType = LogType.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogType {
    INFO,      // green
    ALERT,     // yellow
    SUCCESS,   // cyan
    ERROR      // red
}

@Entity(tableName = "run_records")
data class RunRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val runnerName: String,
    val runnerClass: String,
    val levelReached: Int,
    val nodesHacked: Int,
    val creditsEarned: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: String // "DECEASED" or "DISCONNECTED" (won/alive)
)

@Entity(tableName = "character_profiles")
data class CharacterProfileEntity(
    @PrimaryKey val profileId: String = "primary_profile",
    val runnerName: String,
    val runnerClass: String,
    val level: Int = 1,
    val credits: Int = 0,
    val totalCreditsEarned: Int = 0,
    val maxIntegrity: Int = 100,
    val maxRam: Int = 12,
    val nodesHackedCount: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_save_progress")
data class GameSaveProgressEntity(
    @PrimaryKey val saveSlotId: String = "current_save",
    val runnerName: String,
    val runnerClass: String,
    val level: Int,
    val integrity: Int,
    val maxIntegrity: Int,
    val playerShield: Int = 0,
    val playerMaxShield: Int = 50,
    val ram: Int,
    val maxRam: Int,
    val ramRecoveryRate: Int = 2,
    val credits: Int,
    val damageBonus: Int = 0,
    val defenseBonus: Int = 0,
    val characterLevel: Int = 1,
    val characterXp: Int = 0,
    val xpToNextLevel: Int = 100,
    val gridX: Int,
    val gridY: Int,
    val direction: String,
    val currentZone: String,
    val buildingFloor: Int,
    val collectorsLevel: Int,
    val cityDistrictIndex: Int,
    val hasElevatorKeycard: Boolean,
    val activeWeather: String,
    val weatherTurnsLeft: Int = 0,
    val stepsSinceLastEvent: Int = 0,
    val nextEventSteps: Int = 30,
    val predictedWeather: String = "",
    val nodesHackedCount: Int,
    val totalCreditsEarned: Int,
    val inventoryCsv: String,
    val installedCyberwareCsv: String,
    val installedProgramsCsv: String,
    val installedImplantsCsv: String = "",
    val exploredCellsCsv: String = "",
    val mazeData: String = "",
    val originalMazeData: String = "",
    val buildingFloorsData: String = "",
    val buildingExploredData: String = "",
    val collectorsLevelsData: String = "",
    val collectorsExploredData: String = "",
    val cityDistrictsData: String = "",
    val cityExploredData: String = "",
    val gameStateName: String = "EXPLORATION",
    val logFeedSerialized: String = "",
    val lastSavedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saveSlotId: String = "current_save",
    val itemName: String,
    val itemType: String = "UTILITY",
    val quantity: Int = 1,
    val description: String = "",
    val acquiredTimestamp: Long = System.currentTimeMillis()
)

enum class GameState {
    EXPLORATION,
    COMBAT_START,
    PLAYER_TURN,
    ENEMY_TURN,
    COMBAT_END
}

/**
 * Phase lifecycle within the turn-based combat loop.
 */
enum class TurnPhase {
    PLAYER_INPUT,
    PLAYER_RESOLVING,
    ENEMY_RESOLVING,
    ROUND_MAINTENANCE,
    COMBAT_VICTORY,
    COMBAT_DEFEAT
}

/**
 * Types of tactical actions available during combat rounds.
 */
enum class CombatActionType {
    STRIKE,
    DEFEND,
    QUICK_HACK,
    PROGRAM,
    USE_ITEM,
    SCAN,
    FLEE,
    PASS
}

/**
 * Audit and telemetry log entry for actions performed by player or enemy in a combat round.
 */
data class TurnActionRecord(
    val roundNumber: Int,
    val actorName: String,
    val isPlayer: Boolean,
    val actionType: CombatActionType,
    val summary: String,
    val damageDealt: Int = 0,
    val shieldAbsorbed: Int = 0,
    val healAmount: Int = 0,
    val isCrit: Boolean = false,
    val isMiss: Boolean = false,
    val statusApplied: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)


