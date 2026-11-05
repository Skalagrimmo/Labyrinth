package com.example.ui

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
 * Observes the UI state and advances a KOTOR-style round clock during the
 * player's decision windows:
 *  - each player turn gets a fresh 3s round window;
 *  - when it elapses, the head of the queued actions auto-executes; an empty
 *    queue falls back to the idle default Strike (CombatRoundScheduler rule);
 *  - combat enters PAUSED (KOTOR convention) so the player can assess first;
 *  - the breach-protocol hacking minigame FREEZES the clock;
 *  - any player action consumes the current window; the next round re-arms it,
 *    so an auto-fire can never race a manual action.
 *
 * No changes to CombatManager: the controller only invokes the same public
 * combat actions the HUD buttons use, which are gated by isCombatInputEnabled.
 */
class CombatClockController(
    private val scope: CoroutineScope,
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val onAutoAction: (QueuedCombatActionType) -> Unit
) {
    private var clock: CombatClockState = CombatClockState()
    private var tickerJob: Job? = null
    private var armedPlayerTurnRound: Int = -1
    private var windowArmed: Boolean = false
    private var lastPlayerActionsCount: Int = -1
    private var nextActionId: Long = 0L

    init {
        start()
    }

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
        clock = CombatClockState()
        publish()
    }

    fun togglePause() {
        clock = if (clock.isPaused) CombatClock.resume(clock) else CombatClock.pause(clock)
        publish()
    }

    fun enqueueAction(type: QueuedCombatActionType) {
        val label = when (type) {
            QueuedCombatActionType.STRIKE -> "STRIKE"
            QueuedCombatActionType.DEFEND -> "FORTIFY"
            QueuedCombatActionType.SCAN -> "SCAN"
        }
        clock = CombatClock.enqueue(clock, type, id = nextActionId++, label = label)
        publish()
    }

    fun clearQueue() {
        clock = CombatClock.clearQueue(clock)
        publish()
    }

    private fun tickOnce() {
        val state = _uiState.value
        val inCombat = state.activeEnemy != null && state.gameState != GameState.EXPLORATION
        if (!inCombat) {
            if (clock != CombatClockState() || armedPlayerTurnRound != -1) {
                armedPlayerTurnRound = -1
                windowArmed = false
                lastPlayerActionsCount = -1
                clock = CombatClockState()
                publish()
            }
            return
        }

        val frozenByHack = state.activeCombatHack != null
        if (frozenByHack != clock.isFrozen) {
            clock = if (frozenByHack) CombatClock.freeze(clock) else CombatClock.unfreeze(clock)
        }

        val playerWindow = state.gameState == GameState.PLAYER_TURN &&
            state.isCombatInputEnabled &&
            state.activeCombatHack == null

        if (playerWindow) {
            // Any player action (manual button or an auto-fired queued one)
            // consumes the current window; the next round re-arms it.
            if (state.totalPlayerActionsCount != lastPlayerActionsCount) {
                lastPlayerActionsCount = state.totalPlayerActionsCount
                clock = CombatClock.resetRound(clock)
                windowArmed = false
            }
            if (state.combatRound != armedPlayerTurnRound) {
                val isNewCombat = armedPlayerTurnRound == -1
                armedPlayerTurnRound = state.combatRound
                clock = CombatClock.resetRound(clock)
                windowArmed = true
                if (isNewCombat) {
                    // KOTOR convention: combat enters paused for assessment.
                    clock = CombatClock.pause(clock)
                }
            }
            if (windowArmed) {
                val (next, outcome) = CombatClock.tick(clock, TICK_MILLIS)
                clock = next
                if (outcome is CombatClock.TickOutcome.RoundElapsed) {
                    windowArmed = false
                    onAutoAction(outcome.action)
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
                combatClockPaused = clock.isPaused,
                combatClockFrozen = clock.isFrozen,
                combatActionQueue = clock.queue.map { it.label }
            )
        }
    }

    companion object {
        const val TICK_MILLIS: Long = 100L
    }
}
