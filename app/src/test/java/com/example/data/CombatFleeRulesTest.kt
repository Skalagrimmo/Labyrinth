package com.example.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class CombatFleeRulesTest {
    @Test
    fun `flee is deterministic with seeded random`() {
        val first = CombatFleeRules.resolve(Random(1))
        val second = CombatFleeRules.resolve(Random(1))
        assertEquals(first, second)
    }

    @Test
    fun `successful flee ends combat with escaped winner`() {
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val result = CombatFleeRules.resolve(random)
        assertEquals(true, result.escaped)
        assertEquals(EngineCombatTurn.COMBAT_ENDED, result.turn)
        assertEquals(true, result.isCombatOver)
        assertEquals(CombatWinner.ESCAPED, result.winner)
    }

    @Test
    fun `failed flee keeps combat active`() {
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = (1 shl bitCount) - 1
        }
        val result = CombatFleeRules.resolve(random)
        assertEquals(false, result.escaped)
        assertEquals(EngineCombatTurn.ENEMY_TURN, result.turn)
        assertEquals(false, result.isCombatOver)
        assertEquals(null, result.winner)
    }
}
