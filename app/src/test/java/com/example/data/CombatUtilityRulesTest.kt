package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatUtilityRulesTest {
    @Test
    fun `defend scales with level and caps shield`() {
        val r = CombatDefendRules.resolve(
            CombatEngineState(playerLevel = 5, playerShield = 45, playerMaxShield = 50)
        )
        assertEquals(30, r.shieldRestored)
        assertEquals(50, r.playerShield)
    }

    @Test
    fun `nanomed preserves legacy heal amount and cap`() {
        val r = CombatItemRules.resolve(
            CombatEngineState(playerHealth = 80, playerMaxHealth = 100, playerRam = 4),
            "NanoMed.sys"
        )
        assertEquals(100, r.playerHealth)
        assertEquals(4, r.playerRam)
        assertEquals(35, r.appliedAmount)
        assertEquals(UtilityItemEffect.HEAL, r.effect)
    }

    @Test
    fun `ram boost preserves legacy amount and cap`() {
        val r = CombatItemRules.resolve(
            CombatEngineState(playerRam = 10, playerMaxRam = 12),
            "RAMBoost.exe"
        )
        assertEquals(12, r.playerRam)
        assertEquals(6, r.appliedAmount)
        assertEquals(UtilityItemEffect.RAM, r.effect)
    }

    @Test
    fun `unknown utility item does not mutate combat resources`() {
        val state = CombatEngineState(playerHealth = 73, playerRam = 9)
        val r = CombatItemRules.resolve(state, "unknown")
        assertEquals(73, r.playerHealth)
        assertEquals(9, r.playerRam)
        assertEquals(UtilityItemEffect.NONE, r.effect)
    }
}
