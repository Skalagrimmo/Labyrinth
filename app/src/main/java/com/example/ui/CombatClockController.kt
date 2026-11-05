package com.example.ui

import com.example.data.CombatRoundClock
import com.example.data.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time-with-pause combat clock controller for the existing combat flow.
 *
 * The KOTOR pacing semantics live exactly once, in the shared core:engine
 * CombatRoundClock (the same pacing core CombatRoundScheduler uses):
 *  - each player turn gets a fresh 3s round window;
 *  - when it elapses, the head of the queued actions auto-executes; an empty
 *    queue falls back to the idle default Strike (KOTOR rule);
 *  - combat enters PAUSED (KOTOR convention) so the player can assess first;
 *  - the breach-protocol hacking minigame FREEZES the clock;
 *  - any player action consumes the current window; the next round re-arms it,
 *    so an auto-fire can never race a manual action.
 *
 * This controller keeps only the legacy-flow wiring: it observes the UI state,
 * maps the shared clock onto GameUiState fields and invokes the same public
 * combat actions the HUD buttons use, which are gated by
 * isCombatInputEnabled. No changes to CombatManager.
 *
 * The ticker is NOT started on construction: GameViewModel starts it when a
 * combat actually begins (triggerCombat) and it stops itself when the combat
 * is over, so no coroutine spins during menus or UI tests.
 */
class CombatClockController(
    private val scope: CoroutineScope,
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val onAutoAction: (QueuedCombatActionType) -> Unit
) {
    private var clock: CombatRoundClock.ClockState<QueuedCombatAction> = CombatRoundClock.start()
    private var tickerJob: Job? = null
    private var armedPlayerTurnRound: Int = -1
    private var windowArmed: Boolean = false
    private var lastPlayerActionsCount: Int = -1
    private var nextActionId: Long = 0L

    fun start() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                tickOnce()
            }
        }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        armedPlayerTurnRound = -1
        windowArmed = false
        lastPlayerActionsCount = -1
        clock = CombatRoundClock.start()
        publish()
    }

    fun togglePause() {
        clock = if (clock.playerPaused) CombatRoundClock.resume(clock) else CombatRoundClock.pause(clock)
        publish()
    }

    fun enqueueAction(type: QueuedCombatActionType) {
        val label = when (type) {
            QueuedCombatActionType.STRIKE -> "STRIKE"
            QueuedCombatActionType.DEFEND -> "FORTIFY"
            QueuedCombatActionType.SCAN -> "SCAN"
        }
        clock = CombatRoundClock.enqueue(
            clock,
            QueuedCombatAction(id = nextActionId++, type = type, label = label)
        )
        publish()
    }

    fun clearQueue() {
        clock = CombatRoundClock.clearQueue(clock)
        publish()
    }

    private fun tickOnce() {
        val state = _uiState.value
        val inCombat = state.activeEnemy != null &&
            state.gameState != GameState.EXPLORATION &&
            state.screen != ActiveScreen.GAME_OVER
        if (!inCombat) {
            // Combat concluded (victory, flee or defeat): stop the world
            // clock entirely instead of spinning while the player reads the
            // aftermath. The next combat re-starts it via start().
            stop()
            return
        }

        val frozenByHack = state.activeCombatHack != null
        if (frozenByHack != clock.frozen) {
            clock = if (frozenByHack) CombatRoundClock.freeze(clock) else CombatRoundClock.unfreeze(clock)
        }

        val playerWindow = state.gameState == GameState.PLAYER_TURN &&
            state.isCombatInputEnabled &&
            state.activeCombatHack == null

        if (playerWindow) {
            // Any player action (manual button or an auto-fired queued one)
            // consumes the current window; the next round re-arms it.
            if (state.totalPlayerActionsCount != lastPlayerActionsCount) {
                lastPlayerActionsCount = state.totalPlayerActionsCount
                clock = CombatRoundClock.resetWindow(clock)
                windowArmed = false
            }
            if (state.combatRound != armedPlayerTurnRound) {
                val isNewCombat = armedPlayerTurnRound == -1
                armedPlayerTurnRound = state.combatRound
                clock = CombatRoundClock.resetWindow(clock)
                windowArmed = true
                if (isNewCombat) {
                    // KOTOR convention: combat enters paused for assessment.
                    clock = CombatRoundClock.pause(clock)
                }
            }
            if (windowArmed) {
                when (val paced = CombatRoundClock.tick(clock, TICK_MILLIS)) {
                    is CombatRoundClock.TickResult.Running -> clock = paced.state
                    is CombatRoundClock.TickResult.RoundElapsed -> {
                        clock = paced.state
                        val head = paced.consumed
                        if (head != null) {
                            clock = CombatRoundClock.consumeHead(clock)
                        }
                        windowArmed = false
                        onAutoAction(head?.type ?: QueuedCombatActionType.STRIKE)
                    }
                }
            }
        }
        publish()
    }

    private fun publish() {
        _uiState.update { s ->
            s.copy(
                combatClockMillisLeft = clock.millisLeft,
                combatClockRoundMillis = clock.roundMillis,
                combatClockPaused = clock.playerPaused,
                combatClockFrozen = clock.frozen,
                combatActionQueue = clock.queue.map { it.label }
            )
        }
    }

    companion object {
        const val TICK_MILLIS: Long = 100L
    }
}
