package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatDomainModelsTest {
    @Test
    fun `turn action record preserves legacy defaults`() {
        val record = TurnActionRecord(
            roundNumber = 2,
            actorName = "Runner",
            isPlayer = true,
            actionType = CombatActionType.STRIKE,
            summary = "Strike"
        )

        assertEquals(0, record.damageDealt)
        assertEquals(0, record.shieldAbsorbed)
        assertEquals(0, record.healAmount)
        assertEquals(false, record.isCrit)
        assertEquals(false, record.isMiss)
        assertEquals(null, record.statusApplied)
    }

    @Test
    fun `combat winner retains escaped as distinct outcome`() {
        assertEquals(listOf("PLAYER", "ENEMY", "ESCAPED"), CombatWinner.values().map { it.name })
    }

    @Test
    fun `portable game state names remain stable`() {
        assertEquals(
            listOf("EXPLORATION", "COMBAT_START", "PLAYER_TURN", "ENEMY_TURN", "COMBAT_END"),
            GameState.values().map { it.name }
        )
    }
}
