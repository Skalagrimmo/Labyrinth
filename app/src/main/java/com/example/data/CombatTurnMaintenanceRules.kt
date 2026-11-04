package com.example.data

import kotlin.math.min

data class CombatTurnMaintenanceResolution(
    val playerRam: Int,
    val isPlayerDefending: Boolean,
    val turn: EngineCombatTurn,
    val turnNumber: Int,
    val isCombatOver: Boolean,
    val winner: CombatWinner?
)

object CombatTurnMaintenanceRules {
    fun resolve(state: CombatEngineState): CombatTurnMaintenanceResolution {
        val defeated = state.playerHealth <= 0
        return CombatTurnMaintenanceResolution(
            playerRam = min(state.playerMaxRam, state.playerRam + 2),
            isPlayerDefending = false,
            turn = if (defeated) EngineCombatTurn.COMBAT_ENDED else EngineCombatTurn.PLAYER_TURN,
            turnNumber = state.turnNumber + 1,
            isCombatOver = defeated,
            winner = if (defeated) CombatWinner.ENEMY else null
        )
    }
}
