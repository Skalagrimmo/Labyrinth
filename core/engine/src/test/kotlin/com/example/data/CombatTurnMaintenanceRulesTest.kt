package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatTurnMaintenanceRulesTest {
    @Test
    fun `maintenance restores two RAM and advances to player turn`() {
        val result = CombatTurnMaintenanceRules.resolve(
            CombatEngineState(
                playerRam = 4,
                playerMaxRam = 12,
                isPlayerDefending = true,
                turnNumber = 7
            )
        )
        assertEquals(6, result.playerRam)
        assertEquals(false, result.isPlayerDefending)
        assertEquals(EngineCombatTurn.PLAYER_TURN, result.turn)
        assertEquals(8, result.turnNumber)
        assertEquals(false, result.isCombatOver)
        assertEquals(null, result.winner)
    }

    @Test
    fun `maintenance caps RAM and ends combat after player defeat`() {
        val result = CombatTurnMaintenanceRules.resolve(
            CombatEngineState(
                playerHealth = 0,
                playerRam = 11,
                playerMaxRam = 12,
                turnNumber = 3
            )
        )
        assertEquals(12, result.playerRam)
        assertEquals(EngineCombatTurn.COMBAT_ENDED, result.turn)
        assertEquals(4, result.turnNumber)
        assertEquals(true, result.isCombatOver)
        assertEquals(CombatWinner.ENEMY, result.winner)
    }
}
