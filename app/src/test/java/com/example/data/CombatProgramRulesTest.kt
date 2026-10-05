package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatProgramRulesTest {
    @Test
    fun `insufficient RAM leaves combat values unchanged`() {
        val state = CombatEngineState(playerRam = 2, enemyShield = 8, enemyHealth = 50)
        val result = CombatProgramRules.resolve(
            state,
            PlayerCombatAction.RunProgram("Overload", ramCost = 3, damage = 20, heal = 5, shield = 5)
        )
        assertFalse(result.canExecute)
        assertEquals(2, result.remainingRam)
        assertEquals(8, result.enemyShield)
        assertEquals(50, result.enemyHealth)
        assertEquals(0, result.damageDealt)
    }

    @Test
    fun `program spends RAM scales damage and resolves shield first`() {
        val state = CombatEngineState(playerRam = 10, playerLevel = 3, enemyShield = 5, enemyHealth = 40)
        val result = CombatProgramRules.resolve(
            state,
            PlayerCombatAction.RunProgram("Spike", ramCost = 4, damage = 10, heal = 0, shield = 0)
        )
        assertTrue(result.canExecute)
        assertEquals(6, result.remainingRam)
        assertEquals(16, result.damageDealt)
        assertEquals(0, result.enemyShield)
        assertEquals(29, result.enemyHealth)
    }

    @Test
    fun `program heal and shield are capped by maxima`() {
        val state = CombatEngineState(
            playerRam = 10, playerHealth = 95, playerMaxHealth = 100,
            playerShield = 48, playerMaxShield = 50
        )
        val result = CombatProgramRules.resolve(
            state,
            PlayerCombatAction.RunProgram("Repair", ramCost = 2, damage = 0, heal = 20, shield = 20)
        )
        assertEquals(100, result.playerHealth)
        assertEquals(50, result.playerShield)
    }
}
