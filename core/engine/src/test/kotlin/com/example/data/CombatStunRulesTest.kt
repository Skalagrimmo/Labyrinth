package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatStunRulesTest {
    @Test
    fun `stun clears effect restores RAM and advances turn`() {
        val result = CombatStunRules.resolve(
            CombatEngineState(
                isEnemyStunned = true,
                playerRam = 5,
                playerMaxRam = 12,
                isPlayerDefending = true,
                turnNumber = 4
            )
        )
        assertEquals(false, result.isEnemyStunned)
        assertEquals(7, result.playerRam)
        assertEquals(false, result.isPlayerDefending)
        assertEquals(EngineCombatTurn.PLAYER_TURN, result.turn)
        assertEquals(5, result.turnNumber)
    }

    @Test
    fun `stun RAM recovery is capped`() {
        val result = CombatStunRules.resolve(
            CombatEngineState(
                playerRam = 11,
                playerMaxRam = 12
            )
        )
        assertEquals(12, result.playerRam)
    }
}
