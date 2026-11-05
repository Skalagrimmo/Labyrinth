package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatVictoryRulesTest {
    @Test
    fun `enemy defeat resolves player victory`() {
        val result = CombatVictoryRules.resolve(
            CombatEngineState(
                enemyHealth = 0,
                turn = EngineCombatTurn.ANIMATING
            )
        )
        assertEquals(true, result.isVictory)
        assertEquals(true, result.isCombatOver)
        assertEquals(EngineCombatTurn.COMBAT_ENDED, result.turn)
        assertEquals(CombatWinner.PLAYER, result.winner)
    }

    @Test
    fun `living enemy keeps current combat state`() {
        val result = CombatVictoryRules.resolve(
            CombatEngineState(
                enemyHealth = 10,
                turn = EngineCombatTurn.ANIMATING
            )
        )
        assertEquals(false, result.isVictory)
        assertEquals(false, result.isCombatOver)
        assertEquals(EngineCombatTurn.ANIMATING, result.turn)
        assertEquals(null, result.winner)
    }
}
