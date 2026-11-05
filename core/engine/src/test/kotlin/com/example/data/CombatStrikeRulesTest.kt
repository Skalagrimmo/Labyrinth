package com.example.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CombatStrikeRulesTest {
    @Test
    fun `same seed produces identical strike resolution`() {
        val state = CombatEngineState(playerLevel = 4, playerRam = 10, enemyArmor = 7)
        assertEquals(
            CombatStrikeRules.resolve(state, Random(1234)),
            CombatStrikeRules.resolve(state, Random(1234))
        )
    }

    @Test
    fun `hit chance retains legacy clamp`() {
        assertEquals(25, CombatStrikeRules.resolve(
            CombatEngineState(playerLevel = -100, playerRam = 0), Random(1)
        ).hitChance)
        assertEquals(95, CombatStrikeRules.resolve(
            CombatEngineState(playerLevel = 100, playerRam = 100), Random(1)
        ).hitChance)
    }

    @Test
    fun `successful strike resolves damage through shield first`() {
        val state = CombatEngineState(
            playerLevel = 10, playerRam = 100,
            enemyArmor = 0, enemyShield = 5, enemyHealth = 100
        )
        val result = CombatStrikeRules.resolve(state, Random(42))
        assertFalse(result.wasMiss)
        assertEquals(0, result.enemyShield)
        assertEquals(100 - (result.damageDealt - 5), result.enemyHealth)
    }
}
