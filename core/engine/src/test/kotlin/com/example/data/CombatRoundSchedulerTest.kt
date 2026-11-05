package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * JVM tests for the KOTOR-style real-time-with-pause combat scheduler.
 * All combat randomness is seeded, so every test is deterministic.
 */
class CombatRoundSchedulerTest {

    private fun freshScheduler(): CombatRoundScheduler.SchedulerState =
        CombatRoundScheduler.start(CombatEngineState())

    @Test
    fun tickDoesNotAdvanceTimeWhilePaused() {
        val paused = CombatRoundScheduler.pause(freshScheduler())

        val after = CombatRoundScheduler.tick(paused, 5_000L)

        assertEquals(paused, after)
        assertEquals(0L, after.elapsedMillis)
    }

    @Test
    fun hackingFreezeStopsClockAndUnfreezeResumesExactlyWhereItWas() {
        val frozen = CombatRoundScheduler.freezeForHacking(freshScheduler())

        val during = CombatRoundScheduler.tick(frozen, 5_000L)
        assertEquals(frozen, during)

        val resumed = CombatRoundScheduler.unfreezeAfterHacking(during)
        val after = CombatRoundScheduler.tick(resumed, 1_000L, Random(1))
        assertEquals(1_000L, after.elapsedMillis)
        assertEquals(2_000L, after.millisUntilNextRound)
    }

    @Test
    fun playerRoundConsumesQueuedAction() {
        var state = freshScheduler()
        state = CombatRoundScheduler.enqueue(state, PlayerCombatAction.Defend)

        state = CombatRoundScheduler.tick(state, 3_000L, Random(2))

        assertEquals(1, state.events.size)
        assertTrue(state.events.first().isPlayer)
        assertTrue(state.events.first().result.newState.isPlayerDefending)
        assertEquals(EngineCombatTurn.ENEMY_TURN, state.engineState.turn)
        assertTrue(state.actionQueue.isEmpty())
    }

    @Test
    fun idlePlayerDefaultsToStrikeAttack() {
        val state = CombatRoundScheduler.tick(freshScheduler(), 3_000L, Random(3))

        assertTrue(state.events.first().isPlayer)
        assertEquals(EngineCombatTurn.ENEMY_TURN, state.engineState.turn)
        assertTrue(state.actionQueue.isEmpty())
    }

    @Test
    fun enemyRoundResolvesAndReturnsToPlayerTurn() {
        val enemyTurnState = CombatRoundScheduler.SchedulerState(
            engineState = CombatEngineState(turn = EngineCombatTurn.ENEMY_TURN)
        )

        val state = CombatRoundScheduler.tick(enemyTurnState, 3_000L, Random(4))

        assertFalse(state.events.first().isPlayer)
        assertEquals(EngineCombatTurn.PLAYER_TURN, state.engineState.turn)
    }

    @Test
    fun queueIsCappedAtFourAndReplacesLastActionWhenFull() {
        var state = freshScheduler()
        val names = listOf("P1", "P2", "P3", "P4", "P5")
        names.forEach { name ->
            state = CombatRoundScheduler.enqueue(
                state,
                PlayerCombatAction.RunProgram(programName = name, ramCost = 1, damage = 0, heal = 0, shield = 0)
            )
        }

        assertEquals(4, state.actionQueue.size)
        val last = state.actionQueue.last() as PlayerCombatAction.RunProgram
        assertEquals("P5", last.programName)
    }

    @Test
    fun combatOverStopsTheScheduler() {
        val finished = CombatRoundScheduler.start(
            CombatEngineState(isCombatOver = true, winner = CombatWinner.PLAYER)
        )

        assertEquals(finished, CombatRoundScheduler.tick(finished, 10_000L))
    }

    @Test
    fun sameSeedProducesIdenticalRealTimeSequence() {
        fun runSchedule(): CombatRoundScheduler.SchedulerState {
            var state = CombatRoundScheduler.start(CombatEngineState())
            state = CombatRoundScheduler.enqueue(state, PlayerCombatAction.Defend)
            state = CombatRoundScheduler.tick(state, 2_999L, Random(11))
            state = CombatRoundScheduler.tick(state, 1L, Random(11))
            state = CombatRoundScheduler.tick(state, 3_000L, Random(11))
            state = CombatRoundScheduler.tick(state, 3_000L, Random(11))
            return state
        }

        assertEquals(runSchedule(), runSchedule())
    }
}
