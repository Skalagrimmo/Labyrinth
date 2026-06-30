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

data class Program(
    val id: String,
    val name: String,
    val description: String,
    val ramCost: Int,
    val cooldownTurns: Int = 0,
    val damage: Int = 0,
    val shield: Int = 0,
    val heal: Int = 0,
    val piercesDefense: Boolean = false
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
    GRAVITY_SLOPE('L', "Pulsing Gravity Slope")
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
    val description: String
)

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
