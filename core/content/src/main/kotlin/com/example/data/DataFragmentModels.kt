package com.example.data

/**
 * Cosmetic Themes unlockable with Data Fragments.
 */
enum class CosmeticTheme(
    val id: String,
    val title: String,
    val cost: Int,
    val primaryHex: Long,
    val backgroundHex: Long,
    val textHex: Long,
    val accentHex: Long,
    val description: String
) {
    DEFAULT_CYBER(
        id = "DEFAULT_CYBER",
        title = "Neon Cyberpunk (Default)",
        cost = 0,
        primaryHex = 0xFF00FFCC,
        backgroundHex = 0xFF0B0F19,
        textHex = 0xFFE2E8F0,
        accentHex = 0xFFFF007F,
        description = "Standard high-contrast cyan and magenta cyberpunk HUD."
    ),
    MATRIX_EMERALD(
        id = "MATRIX_EMERALD",
        title = "Matrix Emerald CRT",
        cost = 5,
        primaryHex = 0xFF00FF66,
        backgroundHex = 0xFF021208,
        textHex = 0xFF80FFB3,
        accentHex = 0xFF10B981,
        description = "Classic falling digital rain green terminal aesthetic."
    ),
    AMBER_RETRO(
        id = "AMBER_RETRO",
        title = "Retro CRT Amber",
        cost = 8,
        primaryHex = 0xFFFFB000,
        backgroundHex = 0xFF171004,
        textHex = 0xFFFFE0A3,
        accentHex = 0xFFF59E0B,
        description = "1980s monochrome phosphor amber terminal display."
    ),
    QUANTUM_FROST(
        id = "QUANTUM_FROST",
        title = "Quantum Frost Ice",
        cost = 10,
        primaryHex = 0xFF00E5FF,
        backgroundHex = 0xFF06152B,
        textHex = 0xFFBAE6FD,
        accentHex = 0xFF38BDF8,
        description = "Sub-zero cryogenic quantum blue illumination."
    ),
    OBSIDIAN_STEALTH(
        id = "OBSIDIAN_STEALTH",
        title = "Obsidian Dark Mode",
        cost = 12,
        primaryHex = 0xFFE2E8F0,
        backgroundHex = 0xFF030712,
        textHex = 0xFFF8FAFC,
        accentHex = 0xFF94A3B8,
        description = "Monochrome stealth dark interface for high privacy."
    );

    companion object {
        fun fromId(id: String): CosmeticTheme = values().find { it.id == id } ?: DEFAULT_CYBER
    }
}

/**
 * Custom Terminal Prompt Styles unlockable with Data Fragments.
 */
enum class TerminalPromptStyle(
    val id: String,
    val title: String,
    val cost: Int,
    val promptString: String,
    val description: String
) {
    DEFAULT("DEFAULT", "Runner Prompt", 0, "USER@CYBERSPACE:~#", "Standard runner shell prompt."),
    ROOT_MAINFRAME("ROOT_MAINFRAME", "Root Mainframe", 4, "ROOT@MAINFRAME[SYS]#", "Elevated root superuser prompt."),
    GHOST_NODE("GHOST_NODE", "Ghost Node", 6, "GHOST_RUNNER//0x7F$", "Anonymous stealth runner channel."),
    QUANTUM_CORE("QUANTUM_CORE", "Quantum Core", 8, "QUANTUM_CORE::0x9A>", "Quantum computing execution thread."),
    BLACK_ICE_BREAKER("BLACK_ICE_BREAKER", "Black-ICE Breaker", 10, "ICE_BREAKER_v2.4!>", "Offensive ICE penetrator terminal prompt.");

    companion object {
        fun fromId(id: String): TerminalPromptStyle = values().find { it.id == id } ?: DEFAULT
    }
}

/**
 * Performance Buffs unlockable and toggleable with Data Fragments.
 */
enum class PerformanceBuff(
    val id: String,
    val title: String,
    val cost: Int,
    val icon: String,
    val ramRegenBonus: Float = 0f,
    val iceDetectionReduction: Float = 0f,
    val hackTimeBonusSec: Int = 0,
    val integrityShieldBonus: Int = 0,
    val creditYieldBonusPercent: Int = 0,
    val description: String
) {
    RAM_OVERCLOCK(
        id = "RAM_OVERCLOCK",
        title = "Hyper-RAM Overclock",
        cost = 6,
        icon = "⚡",
        ramRegenBonus = 0.25f,
        description = "+25% faster RAM buffer regeneration during node hacking."
    ),
    STEALTH_MASK(
        id = "STEALTH_MASK",
        title = "Signal Dampening Mask",
        cost = 8,
        icon = "🥷",
        iceDetectionReduction = 0.30f,
        description = "-30% ICE patrol detection radius in SVDAG cyberspace."
    ),
    HACK_OVERTIME(
        id = "HACK_OVERTIME",
        title = "Clock-Cycle Extender",
        cost = 7,
        icon = "⏱️",
        hackTimeBonusSec = 5,
        description = "+5 extra seconds execution window on hacking minigames."
    ),
    SHIELD_MATRIX(
        id = "SHIELD_MATRIX",
        title = "Integrity Thermal Shield",
        cost = 10,
        icon = "🛡️",
        integrityShieldBonus = 20,
        description = "+20 max Integrity/HP buffer against firewall attacks."
    ),
    CREDIT_SIPHON(
        id = "CREDIT_SIPHON",
        title = "Data Siphon Protocol",
        cost = 8,
        icon = "💰",
        creditYieldBonusPercent = 25,
        description = "+25% credit yield from node hacks, secrets, and defeated ICE."
    );

    companion object {
        fun fromId(id: String): PerformanceBuff? = values().find { it.id == id }
    }
}
