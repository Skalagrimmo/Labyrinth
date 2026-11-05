package com.example.data

import com.example.core.model.CellType
import com.example.stealth.LabyrinthStealthSession
import com.example.stealth.StealthGuard
import com.example.stealth.StealthPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

/**
 * JVM tests for the shared world clock session machine.
 * All combat randomness is seeded, so every test is deterministic.
 */
class RealTimeGameSessionTest {

    private fun openMaze(width: Int = 8, height: Int = 6): Array<Array<CellType>> =
        Array(height) { Array(width) { CellType.PATH } }

    private fun stationaryGuard(detectionRatePerSecond: Float): StealthGuard = StealthGuard(
        id = "guard-session",
        position = StealthPosition(1.5f, 2.5f),
        facingAngleRadians = 0f,
        viewRange = 6f,
        fieldOfViewRadians = PI.toFloat() / 2f,
        detectionRatePerSecond = detectionRatePerSecond,
        suspicionRecoveryPerSecond = 30f,
        patrolSpeed = 0f,
        investigationSpeed = 0f,
        chaseSpeed = 0f
    )

    @Test
    fun explorationTicksStealthWithoutCombat() {
        val session = RealTimeGameSession(
            stealthSession = LabyrinthStealthSession(openMaze(), listOf(stationaryGuard(150f)))
        )

        // Player behind the guard: no detection, world keeps exploring.
        val result = session.tick(250L, gridX = 0, gridY = 2, random = Random(1))

        assertEquals(GameSessionMode.EXPLORATION, session.mode)
        assertEquals(GameSessionMode.EXPLORATION, result.mode)
        assertNotNull(result.stealthFrame)
        assertFalse(result.enteredCombat)
    }

    @Test
    fun detectionTransitionsToKotorCombatWithEntryPause() {
        val session = RealTimeGameSession(
            stealthSession = LabyrinthStealthSession(openMaze(), listOf(stationaryGuard(150f)))
        )

        var result = session.tick(1000L, gridX = 5, gridY = 2, random = Random(2))
        var ticks = 1
        while (!result.enteredCombat && ticks < 20) {
            result = session.tick(1000L, gridX = 5, gridY = 2, random = Random(2))
            ticks++
        }

        assertTrue(result.enteredCombat)
        assertEquals(GameSessionMode.COMBAT, session.mode)
        assertNotNull(result.detection)
        assertEquals(CombatRoundScheduler.ClockMode.PAUSED, session.combat?.clockMode)
    }

    @Test
    fun combatResumesAndEndsOnEngineConclusion() {
        val session = RealTimeGameSession(
            stealthSession = LabyrinthStealthSession(openMaze(), listOf(stationaryGuard(1000f))),
            combatStartState = {
                CombatEngineState(
                    enemyHealth = 5,
                    enemyMaxHealth = 5,
                    enemyShield = 0,
                    enemyMaxShield = 0,
                    playerRam = 12
                )
            }
        )

        var entry = session.tick(1000L, gridX = 5, gridY = 2, random = Random(3))
        assertTrue(entry.enteredCombat)

        // KOTOR flow: queue a lethal program while paused, then resume.
        assertTrue(session.enqueueCombatAction(
            PlayerCombatAction.RunProgram(programName = "Kill", ramCost = 1, damage = 10, heal = 0, shield = 0)
        ))
        assertTrue(session.resumeCombat())

        var result = session.tick(3000L, gridX = 5, gridY = 2, random = Random(4))
        var combatTicks = 1
        while (session.mode == GameSessionMode.COMBAT && combatTicks < 10) {
            result = session.tick(3000L, gridX = 5, gridY = 2, random = Random(4))
            combatTicks++
        }

        assertEquals(GameSessionMode.COMBAT_ENDED, session.mode)
        assertTrue(result.combatEnded)
        assertTrue(result.combatEvents.isNotEmpty())
    }

    @Test
    fun pausedSessionTicksNothing() {
        val session = RealTimeGameSession(
            stealthSession = LabyrinthStealthSession(openMaze(), listOf(stationaryGuard(1000f)))
        )
        session.setPaused(true)

        val result = session.tick(5000L, gridX = 5, gridY = 2, random = Random(5))

        assertEquals(GameSessionMode.EXPLORATION, session.mode)
        assertNull(result.stealthFrame)
        assertFalse(result.enteredCombat)
    }

    @Test
    fun endedCombatStandsStillAndReturnsToExplorationWithoutRetriggering() {
        val session = RealTimeGameSession(
            stealthSession = LabyrinthStealthSession(openMaze(), listOf(stationaryGuard(1000f))),
            combatStartState = {
                CombatEngineState(
                    enemyHealth = 1,
                    enemyMaxHealth = 1,
                    enemyShield = 0,
                    enemyMaxShield = 0,
                    playerRam = 12
                )
            }
        )

        val entry = session.tick(1000L, gridX = 5, gridY = 2, random = Random(6))
        assertTrue(entry.enteredCombat)
        session.resumeCombat()

        var ended = false
        var guard = 0
        while (!ended && guard < 10) {
            val step = session.tick(3000L, gridX = 5, gridY = 2, random = Random(6))
            ended = step.combatEnded
            guard++
        }
        assertTrue(ended)
        assertEquals(GameSessionMode.COMBAT_ENDED, session.mode)

        // Ended world stands still.
        val still = session.tick(3000L, gridX = 5, gridY = 2, random = Random(6))
        assertFalse(still.combatEnded)
        assertTrue(still.combatEvents.isEmpty())

        // Return to exploration: the lingering ALERTED guard does not retrigger combat.
        session.returnToExploration()
        val explore = session.tick(250L, gridX = 5, gridY = 2, random = Random(6))
        assertEquals(GameSessionMode.EXPLORATION, session.mode)
        assertNotNull(explore.stealthFrame)
        assertFalse(explore.enteredCombat)
    }
}
