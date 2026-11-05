package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the shared KOTOR RTwP pacing core (CombatRoundClock).
 * These semantics are consumed by both CombatRoundScheduler (core:engine)
 * and the app combat clock controller, so they are pinned exactly once.
 */
class CombatRoundClockTest {

    private fun freshState(): CombatRoundClock.ClockState<String> = CombatRoundClock.start()

    @Test
    fun idleRoundWindowElapsesWithNullConsumedAndCarriesOver() {
        var state = freshState()
        repeat(29) {
            val paced = CombatRoundClock.tick(state, 100L)
            assertTrue(paced is CombatRoundClock.TickResult.Running)
            state = (paced as CombatRoundClock.TickResult.Running<String>).state
        }
        assertEquals(100L, state.millisLeft)

        // Empty queue: no consumed head, the caller applies the idle default (Strike).
        val boundary = CombatRoundClock.tick(state, 150L) as CombatRoundClock.TickResult.RoundElapsed<String>
        assertNull(boundary.consumed)
        // 50ms beyond the boundary carry over toward the next round.
        assertEquals(2_950L, boundary.state.millisLeft)
    }

    @Test
    fun exactBoundaryResetsAFullWindow() {
        val boundary = CombatRoundClock.tick(freshState(), 3_000L) as CombatRoundClock.TickResult.RoundElapsed<String>
        assertNull(boundary.consumed)
        assertEquals(CombatRoundClock.DEFAULT_ROUND_MILLIS, boundary.state.millisLeft)
    }

    @Test
    fun queuedHeadIsConsumedAndTheRestStaysQueued() {
        var state = freshState()
        state = CombatRoundClock.enqueue(state, "FORTIFY")
        state = CombatRoundClock.enqueue(state, "SCAN")

        val boundary = CombatRoundClock.tick(state, 3_000L) as CombatRoundClock.TickResult.RoundElapsed<String>
        assertEquals("FORTIFY", boundary.consumed)
        // The caller executes the head and drops it:
        val after = CombatRoundClock.consumeHead(boundary.state)
        assertEquals(listOf("SCAN"), after.queue)
    }

    @Test
    fun fullQueueReplacesTheLastEntry() {
        var state = freshState()
        for (i in 1..4) {
            state = CombatRoundClock.enqueue(state, "S" + i)
        }
        state = CombatRoundClock.enqueue(state, "S5")
        assertEquals(listOf("S1", "S2", "S3", "S5"), state.queue)
    }

    @Test
    fun pausedClockDoesNotAdvance() {
        val paused = CombatRoundClock.pause(freshState())
        val paced = CombatRoundClock.tick(paused, 1_000L)
        assertTrue(paced is CombatRoundClock.TickResult.Running)
        assertEquals(paused, (paced as CombatRoundClock.TickResult.Running<String>).state)
        assertEquals(CombatRoundClock.DEFAULT_ROUND_MILLIS, paused.millisLeft)
        assertTrue(paused.playerPaused)
    }

    @Test
    fun frozenClockDoesNotAdvanceAndUnfreezeKeepsPlayerPause() {
        val paused = CombatRoundClock.pause(freshState())
        val frozen = CombatRoundClock.freeze(paused)
        assertEquals(CombatRoundClock.ClockMode.FROZEN, frozen.clockMode)

        val during = CombatRoundClock.tick(frozen, 1_000L)
        assertEquals(frozen, (during as CombatRoundClock.TickResult.Running<String>).state)

        val unfrozen = CombatRoundClock.unfreeze(frozen)
        assertEquals(CombatRoundClock.ClockMode.PAUSED, unfrozen.clockMode)

        val resumed = CombatRoundClock.resume(unfrozen)
        assertEquals(CombatRoundClock.ClockMode.RUNNING, resumed.clockMode)
    }

    @Test
    fun pauseWhileFrozenIsNoOp() {
        val frozen = CombatRoundClock.freeze(freshState())
        val pausedWhileFrozen = CombatRoundClock.pause(frozen)
        assertEquals(frozen, pausedWhileFrozen)
    }

    @Test
    fun resumeWhileFrozenIsNoOp() {
        val frozen = CombatRoundClock.freeze(CombatRoundClock.pause(freshState()))
        assertEquals(frozen, CombatRoundClock.resume(frozen))
    }

    @Test
    fun resetWindowRestoresAFullRound() {
        val drained = freshState().copy(millisLeft = 400L)
        val reset = CombatRoundClock.resetWindow(drained)
        assertEquals(CombatRoundClock.DEFAULT_ROUND_MILLIS, reset.millisLeft)
    }

    @Test
    fun clearQueueEmptiesTheQueue() {
        var state = CombatRoundClock.enqueue(freshState(), "STRIKE")
        state = CombatRoundClock.clearQueue(state)
        assertTrue(state.queue.isEmpty())
    }
}
