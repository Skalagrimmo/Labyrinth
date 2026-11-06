package com.example.ui

import com.example.data.CombatRoundClock
import com.example.data.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
 * The controller rides the shared WorldClockTicker (d6 slice 5): it
 * subscribes as a world-tick consumer on construction and never owns a
 * coroutine loop. start() arms the combat clock and makes sure the shared
 * loop runs; when the encounter is over the controller disengages - resets
 * the shared clock and publishes the reset fields exactly once - while the
 * shared world loop keeps ticking exploration.
 */
class CombatClockController(
    private val ticker: WorldClockTicker,
    private val _uiState: MutableStateFlow<GameUiState>,
    private val onAutoAction: (QueuedCombatActionType) -> Unit
) {
    private var clock: CombatRoundClock.ClockState<QueuedCombatAction> = CombatRoundClock.start()
    private var engaged: Boolean = false
    private var armedPlayerTurnRound: Int = -1
    private var windowArmed: Boolean = false
    private var lastPlayerActionsCount: Int = -1
    private var nextActionId: Long = 0L

    init {
        // Shared world-clock consumer (d6 slice 5): no private loop - the
        // GameViewModel-owned WorldClockTicker dispatches every world tick.
        ticker.subscribe(this::tickOnce)
    }

    fun start() {
        // Arms the combat clock and makes sure the shared world loop runs
        // (idempotent: exploration usually started it already).
        engaged = true
        ticker.start()
    }

    fun stop() {
        // Disengages the combat clock (the encounter is over): resets the
        // shared clock state and publishes the reset fields ONCE, so a calm
        // exploration floor never churns GameUiState tick after tick. The
        // shared world loop keeps running - it is not stopped here.
        if (!engaged) return
        engaged = false
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

    internal fun tickOnce() {
        val state = _uiState.value
        val inCombat = state.activeEnemy != null &&
            state.gameState != GameState.EXPLORATION &&
            state.screen != ActiveScreen.GAME_OVER
        if (!inCombat) {
            // Combat concluded (victory, flee or defeat): disengage the
            // combat clock once; the shared world loop keeps ticking the
            // exploration phase and the next combat re-arms via start().
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
                when (val paced = CombatRoundClock.tick(clock, WorldClockTicker.TICK_MILLIS)) {
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
}
