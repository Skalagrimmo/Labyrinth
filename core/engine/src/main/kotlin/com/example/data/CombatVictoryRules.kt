package com.example.data

data class CombatVictoryResolution(
    val isVictory: Boolean,
    val isCombatOver: Boolean,
    val turn: EngineCombatTurn,
    val winner: CombatWinner?
)

object CombatVictoryRules {
    fun resolve(state: CombatEngineState): CombatVictoryResolution {
        val victory = state.enemyHealth <= 0
        return CombatVictoryResolution(
            isVictory = victory,
            isCombatOver = if (victory) true else state.isCombatOver,
            turn = if (victory) EngineCombatTurn.COMBAT_ENDED else state.turn,
            winner = if (victory) CombatWinner.PLAYER else state.winner
        )
    }
}
