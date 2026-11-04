package com.example.data

/** All valid player tactical inputs accepted by the deterministic combat engine. */
sealed class PlayerCombatAction {
    data class Strike(val stance: String = "Strike") : PlayerCombatAction()
    object Defend : PlayerCombatAction()
    data class RunProgram(
        val programName: String,
        val ramCost: Int,
        val damage: Int,
        val heal: Int,
        val shield: Int
    ) : PlayerCombatAction()
    data class ConsumeItem(val itemName: String) : PlayerCombatAction()
    object ScanEnemy : PlayerCombatAction()
    object Flee : PlayerCombatAction()
}

/** Result returned after a combat state calculation. */
data class CombatTurnResult(
    val newState: CombatEngineState,
    val logMessages: List<String>,
    val damageDealtToEnemy: Int = 0,
    val damageDealtToPlayer: Int = 0,
    val wasCrit: Boolean = false,
    val wasMiss: Boolean = false
)
