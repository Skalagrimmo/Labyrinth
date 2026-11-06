package com.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization tests for the combat clock wiring on the shared
 * world ticker (d6 slice 5). The KOTOR pacing semantics themselves live in
 * :core:engine (CombatRoundClockTest); these tests pin the controller:
 * disengagement outside combat is idempotent (a calm exploration floor must
 * not churn GameUiState) and the queue/pause controls keep working without
 * the controller owning a private ticker loop.
 */
class CombatClockControllerTest {

    private fun newFlow(): MutableStateFlow<GameUiState> =
        MutableStateFlow(GameUiState(screen = ActiveScreen.EXPLORATION))

    private fun newController(
        flow: MutableStateFlow<GameUiState>,
        autoActions: MutableList<QueuedCombatActionType>
    ): CombatClockController = CombatClockController(
        ticker = WorldClockTicker(CoroutineScope(Dispatchers.Unconfined)),
        _uiState = flow,
        onAutoAction = { type -> autoActions.add(type) }
    )

    @Test
    fun combatClockDisengagesOnceWhenCombatIsOver() {
        val flow = newFlow()
        val autoActions = mutableListOf<QueuedCombatActionType>()
        val controller = newController(flow, autoActions)

        controller.start()
        controller.tickOnce() // no active encounter: disengage and publish reset fields
        val afterDisengage = flow.value
        assertEquals(0, flow.value.combatActionQueue.size)
        assertFalse(flow.value.combatClockFrozen)
        assertTrue(flow.value.combatClockRoundMillis > 0L)

        // The shared world loop keeps ticking exploration: further world
        // ticks must not touch the published state at all.
        controller.tickOnce()
        assertSame(afterDisengage, flow.value)
        assertTrue(autoActions.isEmpty())
    }

    @Test
    fun queueAndPauseControlsWorkWithoutAStartedLoop() {
        val flow = newFlow()
        val controller = newController(flow, mutableListOf())

        controller.enqueueAction(QueuedCombatActionType.STRIKE)
        assertEquals(listOf("STRIKE"), flow.value.combatActionQueue)
        controller.enqueueAction(QueuedCombatActionType.DEFEND)
        assertEquals(listOf("STRIKE", "FORTIFY"), flow.value.combatActionQueue)

        controller.togglePause()
        assertTrue(flow.value.combatClockPaused)

        controller.clearQueue()
        assertEquals(emptyList<String>(), flow.value.combatActionQueue)
    }
}
