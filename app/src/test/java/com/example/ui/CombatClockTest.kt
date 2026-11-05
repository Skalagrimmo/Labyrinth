package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatClockTest {

    private fun freshState(): CombatClockState = CombatClockState()

    @Test
    fun idleRoundWindowElapsesIntoDefaultStrikeAndResets() {
        var state = freshState()
        var outcome: CombatClock.TickOutcome = CombatClock.TickOutcome.None
        repeat(29) {
            val (next, result) = CombatClock.tick(state, 100L)
            state = next
            outcome = result
        }
        assertTrue(outcome is CombatClock.TickOutcome.None)
        assertEquals(100L, state.millisLeft)

        val (after, elapsed) = CombatClock.tick(state, 100L)
        assertTrue(elapsed is CombatClock.TickOutcome.RoundElapsed)
        assertEquals(
            QueuedCombatActionType.STRIKE,
            (elapsed as CombatClock.TickOutcome.RoundElapsed).action
        )
        assertEquals(CombatClockState.DEFAULT_ROUND_MILLIS, after.millisLeft)
    }

    @Test
    fun queuedHeadAutoExecutesAndLeavesTheRestQueued() {
        var state = freshState()
        state = CombatClock.enqueue(state, QueuedCombatActionType.DEFEND, id = 1L, label = "FORTIFY")
        state = CombatClock.enqueue(state, QueuedCombatActionType.SCAN, id = 2L, label = "SCAN")

        val (after, elapsed) = CombatClock.tick(state, 3_000L)
        val roundElapsed = elapsed as CombatClock.TickOutcome.RoundElapsed
        assertEquals(QueuedCombatActionType.DEFEND, roundElapsed.action)
        assertEquals(listOf("SCAN"), after.queue.map { it.label })
    }

    @Test
    fun fullQueueReplacesTheLastEntry() {
        var state = freshState()
        for (i in 1..4) {
            state = CombatClock.enqueue(state, QueuedCombatActionType.STRIKE, id = i.toLong(), label = "S" + i)
        }
        state = CombatClock.enqueue(state, QueuedCombatActionType.SCAN, id = 5L, label = "S5")
        assertEquals(listOf("S1", "S2", "S3", "S5"), state.queue.map { it.label })
    }

    @Test
    fun pausedClockDoesNotAdvance() {
        val state = CombatClock.pause(freshState())
        val (after, outcome) = CombatClock.tick(state, 1_000L)
        assertTrue(outcome is CombatClock.TickOutcome.None)
        assertEquals(CombatClockState.DEFAULT_ROUND_MILLIS, after.millisLeft)
        assertFalse(after.isRunning)
    }

    @Test
    fun frozenClockDoesNotAdvanceAndUnfreezeKeepsPlayerPause() {
        val paused = CombatClock.pause(freshState())
        val frozen = CombatClock.freeze(paused)
        val (after, outcome) = CombatClock.tick(frozen, 1_000L)
        assertTrue(outcome is CombatClock.TickOutcome.None)
        assertTrue(after.isFrozen)
        assertTrue(after.isPaused)

        val unfrozen = CombatClock.unfreeze(after)
        assertFalse(unfrozen.isFrozen)
        assertTrue(unfrozen.isPaused)

        val resumed = CombatClock.resume(unfrozen)
        assertTrue(resumed.isRunning)
    }

    @Test
    fun pauseWhileFrozenIsNoOp() {
        val frozen = CombatClock.freeze(freshState())
        val pausedWhileFrozen = CombatClock.pause(frozen)
        assertTrue(pausedWhileFrozen.isFrozen)
        assertFalse(pausedWhileFrozen.isPaused)
    }

    @Test
    fun resetRoundRestoresAFullWindow() {
        val drained = freshState().copy(millisLeft = 400L)
        val reset = CombatClock.resetRound(drained)
        assertEquals(CombatClockState.DEFAULT_ROUND_MILLIS, reset.millisLeft)
    }

    @Test
    fun clearQueueEmptiesTheQueue() {
        var state = CombatClock.enqueue(freshState(), QueuedCombatActionType.STRIKE, id = 1L, label = "STRIKE")
        state = CombatClock.clearQueue(state)
        assertTrue(state.queue.isEmpty())
    }
}
