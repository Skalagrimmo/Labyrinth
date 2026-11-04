package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Characterization tests for deterministic combat paths.
 *
 * Random strike/flee behavior is deliberately excluded until RNG is injected into the engine.
 */
class TurnBasedCombatEngineTest {

    @Test
    fun `program damage spends ram and applies shield before integrity`() {
        val state = CombatEngineState(
            playerRam = 12,
            playerMaxRam = 12,
            playerLevel = 1,
            enemyShield = 10,
            enemyMaxShield = 10,
            enemyHealth = 50,
            enemyMaxHealth = 50
        )

        val result = TurnBasedCombatEngine.processPlayerAction(
            PlayerCombatAction.RunProgram(
                programName = "Probe",
                ramCost = 4,
                damage = 20,
                heal = 0,
                shield = 0
            ),
            state
        )

        assertEquals(8, result.newState.playerRam)
        assertEquals(0, result.newState.enemyShield)
        assertEquals(38, result.newState.enemyHealth)
        assertEquals(EngineCombatTurn.ENEMY_TURN, result.newState.turn)
    }

    @Test
    fun `insufficient ram does not advance turn or mutate combat state`() {
        val state = CombatEngineState(playerRam = 1)

        val result = TurnBasedCombatEngine.processPlayerAction(
            PlayerCombatAction.RunProgram("Expensive", ramCost = 5, damage = 99, heal = 0, shield = 0),
            state
        )

        assertEquals(state, result.newState)
        assertTrue(result.logMessages.any { it.contains("INSUFFICIENT RAM") })
    }

    @Test
    fun `defend restores shield without exceeding maximum`() {
        val state = CombatEngineState(playerShield = 49, playerMaxShield = 50, playerLevel = 5)

        val result = TurnBasedCombatEngine.processPlayerAction(PlayerCombatAction.Defend, state)

        assertEquals(50, result.newState.playerShield)
        assertTrue(result.newState.isPlayerDefending)
        assertEquals(EngineCombatTurn.ENEMY_TURN, result.newState.turn)
    }

    @Test
    fun `lethal deterministic program ends encounter with player victory`() {
        val state = CombatEngineState(
            playerRam = 12,
            enemyShield = 0,
            enemyHealth = 5,
            enemyMaxHealth = 5
        )

        val result = TurnBasedCombatEngine.processPlayerAction(
            PlayerCombatAction.RunProgram("Kill", ramCost = 1, damage = 10, heal = 0, shield = 0),
            state
        )

        assertTrue(result.newState.isCombatOver)
        assertEquals(CombatWinner.PLAYER, result.newState.winner)
        assertEquals(EngineCombatTurn.COMBAT_ENDED, result.newState.turn)
        assertFalse(result.newState.enemyHealth > 0)
    }

    @Test
    fun `same seed produces identical strike result`() {
        val state = CombatEngineState(enemyShield = 0, enemyHealth = 100, enemyMaxHealth = 100)
        val first = TurnBasedCombatEngine.processPlayerAction(
            PlayerCombatAction.Strike(),
            state,
            Random(12345)
        )
        val second = TurnBasedCombatEngine.processPlayerAction(
            PlayerCombatAction.Strike(),
            state,
            Random(12345)
        )

        assertEquals(first, second)
    }

    @Test
    fun `same seed produces identical flee result`() {
        val state = CombatEngineState()
        val first = TurnBasedCombatEngine.processPlayerAction(PlayerCombatAction.Flee, state, Random(77))
        val second = TurnBasedCombatEngine.processPlayerAction(PlayerCombatAction.Flee, state, Random(77))

        assertEquals(first, second)
    }

    @Test
    fun `same seed produces identical enemy turn`() {
        val state = CombatEngineState(
            enemyHealth = 80,
            enemyMaxHealth = 80,
            enemyShield = 30,
            enemyMaxShield = 30,
            playerHealth = 100,
            playerRam = 12
        )
        val first = TurnBasedCombatEngine.processEnemyTurn(state, proximityDistance = 2, random = Random(2026))
        val second = TurnBasedCombatEngine.processEnemyTurn(state, proximityDistance = 2, random = Random(2026))

        assertEquals(first, second)
    }
}

