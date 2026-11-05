package com.example.data

/**
 * Data-driven Skill Tree (plan item 3.2).
 *
 * Three branches — Hacking, Combat, Engineering — each with a linear chain of nodes.
 * A node is unlocked by spending skill points once its prerequisite (the previous node
 * in the chain) is unlocked. Each node grants a concrete, already-supported stat bonus.
 *
 * Pure Kotlin (no Android dependencies) so the model can be unit-tested in isolation.
 */
enum class SkillBranch(val displayName: String, val icon: String, val description: String) {
    HACKING("Hacking", "⌨️", "Deepen your breach protocols and RAM management."),
    COMBAT("Combat", "⚔️", "Hardening your offensive payloads and defenses."),
    ENGINEERING("Engineering", "🔧", "Structural integrity, RAM capacity, and credits.");

    companion object {
        fun from(raw: String): SkillBranch? = values().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

data class SkillNode(
    val id: String,                 // e.g. "hacking_scan_range"
    val branch: SkillBranch,
    val name: String,
    val description: String,
    val cost: Int = 1,              // skill points to unlock
    // Stat effects applied on unlock (all map to existing GameUiState fields):
    val bonusDamage: Int = 0,
    val bonusDefense: Int = 0,
    val bonusMaxRam: Int = 0,
    val bonusRamRecovery: Int = 0,
    val bonusMaxIntegrity: Int = 0,
    val bonusCreditsOneTime: Int = 0
) {
    val prerequisiteId: String? get() = SkillTree.prerequisiteOf(this)
}

/**
 * The live skill-tree definition. The chain order per branch is implicit: to unlock a
 * higher-index node in a branch you must already own the one before it.
 */
object SkillTree {

    val branches: List<List<SkillNode>> = listOf(
        // --- HACKING ---
        listOf(
            SkillNode("hacking_ram1", SkillBranch.HACKING, "Extended Buffer", "Permanently +2 Max RAM.", bonusMaxRam = 2),
            SkillNode("hacking_ram2", SkillBranch.HACKING, "Volatile Cache", "Permanently +3 Max RAM.", bonusMaxRam = 3),
            SkillNode("hacking_recovery", SkillBranch.HACKING, "Thermal Regen", "+2 RAM recovery rate per turn.", bonusRamRecovery = 2),
            SkillNode("hacking_ram3", SkillBranch.HACKING, "Overclock Matrix", "Permanently +4 Max RAM.", bonusMaxRam = 4)
        ),
        // --- COMBAT ---
        listOf(
            SkillNode("combat_dmg1", SkillBranch.COMBAT, "Payload Amp", "Permanently +2 Attack Damage.", bonusDamage = 2),
            SkillNode("combat_def1", SkillBranch.COMBAT, "Packet Filter", "Permanently +2 Defense.", bonusDefense = 2),
            SkillNode("combat_dmg2", SkillBranch.COMBAT, "Crit Overclocker", "Permanently +3 Attack Damage.", bonusDamage = 3),
            SkillNode("combat_def2", SkillBranch.COMBAT, "Hardened ICE Gateway", "Permanently +3 Defense.", bonusDefense = 3),
            SkillNode("combat_dmg3", SkillBranch.COMBAT, "Micro-Fusion Blade", "Permanently +5 Attack Damage.", bonusDamage = 5)
        ),
        // --- ENGINEERING ---
        listOf(
            SkillNode("eng_credits", SkillBranch.ENGINEERING, "Black Market Contact", "Instantly gain 300 credits.", bonusCreditsOneTime = 300),
            SkillNode("eng_hp1", SkillBranch.ENGINEERING, "Reinforced Chassis", "Permanently +20 Max Integrity.", bonusMaxIntegrity = 20),
            SkillNode("eng_ramcore", SkillBranch.ENGINEERING, "Dual-Core Router", "Permanently +3 Max RAM.", bonusMaxRam = 3),
            SkillNode("eng_hp2", SkillBranch.ENGINEERING, "Subdermal Weave", "Permanently +30 Max Integrity.", bonusMaxIntegrity = 30),
            SkillNode("eng_hp3", SkillBranch.ENGINEERING, "Titanium Frame", "Permanently +40 Max Integrity.", bonusMaxIntegrity = 40)
        )
    )

    /** Flattened list of every node with its computed prerequisite. */
    val allNodes: List<SkillNode> by lazy {
        branches.flatMap { branch ->
            branch.mapIndexed { index, node ->
                node.copy() // prerequisite resolved via prerequisiteOf
            }
        }
    }

    fun nodesFor(branch: SkillBranch): List<SkillNode> =
        branches.firstOrNull { it.isNotEmpty() && it.first().branch == branch } ?: emptyList()

    /** Returns the node that must be unlocked before [node] (previous in its branch). */
    fun prerequisiteOf(node: SkillNode): String? {
        val branch = nodesFor(node.branch)
        val index = branch.indexOfFirst { it.id == node.id }
        return if (index > 0) branch[index - 1].id else null
    }

    fun nodeById(id: String): SkillNode? = allNodes.firstOrNull { it.id == id }

    /** All nodes currently unlocked if [unlockedIds] is the set of owned node ids. */
    fun combinedEffects(unlockedIds: Set<String>): SkillTreeEffects {
        var maxRam = 0
        var ramRecovery = 0
        var damage = 0
        var defense = 0
        var maxIntegrity = 0
        for (node in allNodes) {
            if (node.id in unlockedIds) {
                maxRam += node.bonusMaxRam
                ramRecovery += node.bonusRamRecovery
                damage += node.bonusDamage
                defense += node.bonusDefense
                maxIntegrity += node.bonusMaxIntegrity
            }
        }
        return SkillTreeEffects(maxRam, ramRecovery, damage, defense, maxIntegrity)
    }
}

/** Aggregated stat bonuses from all unlocked skill nodes. */
data class SkillTreeEffects(
    val maxRam: Int = 0,
    val ramRecovery: Int = 0,
    val damage: Int = 0,
    val defense: Int = 0,
    val maxIntegrity: Int = 0
)
