package com.example.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatEngineModelsTest {
    @Test
    fun `combat engine defaults remain stable after extraction`() {
        val state = CombatEngineState()
        assertEquals(EngineCombatTurn.PLAYER_TURN, state.turn)
        assertEquals(1, state.turnNumber)
        assertEquals(100, state.playerHealth)
        assertEquals(80, state.enemyHealth)
        assertEquals(null, state.winner)
    }

    @Test
    fun `status effect ids are reproducible with seeded identity source`() {
        fun create() = CombatStatusEffectFactory.create(
            name = "Burn",
            type = StatusEffectType.BURN,
            durationTurns = 3,
            potency = 5,
            random = Random(42)
        )
        val first = create()
        val second = create()
        assertEquals(first, second)
        assertTrue(first.id.toInt() in 10000..99998)
    }
}
