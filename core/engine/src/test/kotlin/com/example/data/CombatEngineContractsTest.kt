package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CombatEngineContractsTest {
    @Test
    fun `strike keeps legacy default stance`() {
        assertEquals("Strike", PlayerCombatAction.Strike().stance)
    }

    @Test
    fun `combat result keeps zero damage and false flags by default`() {
        val state = CombatEngineState()
        val result = CombatTurnResult(state, listOf("ok"))

        assertEquals(state, result.newState)
        assertEquals(listOf("ok"), result.logMessages)
        assertEquals(0, result.damageDealtToEnemy)
        assertEquals(0, result.damageDealtToPlayer)
        assertFalse(result.wasCrit)
        assertFalse(result.wasMiss)
    }

    @Test
    fun `program action preserves complete execution payload`() {
        val action = PlayerCombatAction.RunProgram(
            programName = "Overload",
            ramCost = 4,
            damage = 20,
            heal = 3,
            shield = 5
        )
        assertEquals("Overload", action.programName)
        assertEquals(4, action.ramCost)
        assertEquals(20, action.damage)
        assertEquals(3, action.heal)
        assertEquals(5, action.shield)
    }
}
