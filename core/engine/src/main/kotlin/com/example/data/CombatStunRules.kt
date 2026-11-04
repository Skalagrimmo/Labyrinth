package com.example.data

data class CombatStunResolution(
    val isEnemyStunned: Boolean,
    val playerRam: Int,
    val isPlayerDefending: Boolean,
    val turn: EngineCombatTurn,
    val turnNumber: Int
)

object CombatStunRules {
    fun resolve(state: CombatEngineState): CombatStunResolution =
        CombatStunResolution(
            isEnemyStunned = false,
            playerRam = (state.playerRam + 2).coerceAtMost(state.playerMaxRam),
            isPlayerDefending = false,
            turn = EngineCombatTurn.PLAYER_TURN,
            turnNumber = state.turnNumber + 1
        )
}
