package com.example.ui

import com.example.data.LogType
import com.example.data.SkillBranch
import com.example.data.SkillTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * SkillTreeManager — owns skill-point allocation (plan item 3.2).
 *
 * Follows the established manager pattern:
 *  - reads/writes the single source of truth `GameUiState` via `_uiState`
 *  - pushes logs back through the `onLog` callback
 *  - exposes `runTerminalCommand(parts, state)` for the terminal dispatch
 *
 * Terminal usage:
 *   - `skilltree` (or `skills`)   — list all branches, nodes, and unlocked status
 *   - `skill learn <branch> <#>`  — learn the <#>-th node (1-based) of a branch
 *   - `skill points`              — show unallocated points
 *   - `skill reset`               — clear all learned skills (stats remain applied)
 */
class SkillTreeManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val onLog: (String, LogType) -> Unit
) {

    fun runTerminalCommand(parts: List<String>, state: GameViewModel.GameUiState): Boolean {
        if (parts.isEmpty()) return false
        when (parts[0].lowercase()) {
            "skilltree", "skills" -> { listSkillTree(state); return true }
            "skill" -> { handleSkillCommand(parts, state); return true }
        }
        return false
    }

    private fun handleSkillCommand(parts: List<String>, state: GameViewModel.GameUiState) {
        when (parts.getOrNull(1)?.lowercase()) {
            "learn", "buy", "unlock" -> {
                val branch = parts.getOrNull(2)?.let { SkillBranch.from(it) }
                val nodeIndex = parts.getOrNull(3)?.toIntOrNull()
                if (branch == null || nodeIndex == null) {
                    onLog("USAGE: skill learn <HACKING|COMBAT|ENGINEERING> <node#>", LogType.ERROR)
                } else {
                    learnNode(state, branch, nodeIndex)
                }
            }
            "points" -> onLog("UNALLOCATED SKILL POINTS: ${state.skillPoints}", LogType.INFO)
            "reset" -> {
                _uiState.update { it.copy(unlockedSkills = emptySet()) }
                onLog("SKILL TREE CLEARED.", LogType.ALERT)
            }
            else -> onLog("USAGE: skill <learn|points|reset>", LogType.ERROR)
        }
    }

    private fun listSkillTree(state: GameViewModel.GameUiState) {
        onLog("═══ SKILL TREE // ${state.skillPoints} PTS UNALLOCATED ═══", LogType.SUCCESS)
        for (node in SkillTree.allNodes) {
            val owned = node.id in state.unlockedSkills
            val status = if (owned) "[LEARNED]" else "[locked ]"
            val cost = if (owned) "" else " (${node.cost}pt)"
            onLog(
                "${node.branch.icon} ${node.name} $status$cost — ${node.description}",
                if (owned) LogType.SUCCESS else LogType.INFO
            )
        }
    }

    private fun learnNode(state: GameViewModel.GameUiState, branch: SkillBranch, nodeIndex1Based: Int) {
        val nodes = SkillTree.nodesFor(branch)
        val node = nodes.getOrNull(nodeIndex1Based - 1)
        if (node == null) {
            onLog("ERROR: No node #$nodeIndex1Based in ${branch.displayName}. Range: 1..${nodes.size}", LogType.ERROR)
            return
        }
        val unlocked = state.unlockedSkills
        if (node.id in unlocked) {
            onLog("ALREADY LEARNED: ${node.name}.", LogType.ALERT)
            return
        }
        val prereq = SkillTree.prerequisiteOf(node)
        if (prereq != null && prereq !in unlocked) {
            onLog("BLOCKED: Unlock '${SkillTree.nodeById(prereq)?.name}' first.", LogType.ERROR)
            return
        }
        if (state.skillPoints < node.cost) {
            onLog("ERROR: Need ${node.cost} skill points (have ${state.skillPoints}).", LogType.ERROR)
            return
        }

        _uiState.update {
            it.copy(
                skillPoints = it.skillPoints - node.cost,
                unlockedSkills = it.unlockedSkills + node.id
            )
        }

        if (node.bonusCreditsOneTime > 0) {
            _uiState.update {
                it.copy(
                    credits = it.credits + node.bonusCreditsOneTime,
                    totalCreditsEarned = it.totalCreditsEarned + node.bonusCreditsOneTime
                )
            }
        }

        _uiState.update { st ->
            st.copy(
                maxRam = st.maxRam + node.bonusMaxRam,
                ram = st.ram + node.bonusMaxRam,
                ramRecoveryRate = st.ramRecoveryRate + node.bonusRamRecovery,
                damageBonus = st.damageBonus + node.bonusDamage,
                defenseBonus = st.defenseBonus + node.bonusDefense,
                maxIntegrity = st.maxIntegrity + node.bonusMaxIntegrity,
                integrity = st.integrity + node.bonusMaxIntegrity
            )
        }

        onLog("✅ SKILL LEARNED: ${node.name} (${branch.displayName}). ${node.description}", LogType.SUCCESS)
        onLog("SKILL POINTS LEFT: ${_uiState.value.skillPoints}", LogType.INFO)
    }
}
