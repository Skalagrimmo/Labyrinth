package com.example.data

import kotlin.random.Random

/**
 * KOTOR-style real-time-with-pause pacing layer over the deterministic
 * turn-based combat engine.
 *
 * All pacing semantics (round window with carryover, queue rules, pause and
 * freeze modes) live exactly once, in the shared [CombatRoundClock]; the
 * scheduler owns only the deterministic round resolution, delegated to
 * TurnBasedCombatEngine, so all existing combat rules, characterization
 * tests and save compatibility are preserved.
 *
 * Combat time only advances while the clock is RUNNING:
 * - PAUSED: the player paused combat;
 * - FROZEN: a hacking breach protocol froze the combat clock.
 */
object CombatRoundScheduler {

    /** KOTOR-style default combat round length. */
    const val DEFAULT_ROUND_MILLIS: Long = CombatRoundClock.DEFAULT_ROUND_MILLIS

    /** KOTOR-style default action queue depth. */
    const val DEFAULT_QUEUE_CAPACITY: Int = CombatRoundClock.DEFAULT_QUEUE_CAPACITY

    /** One resolved combat round, emitted by tick when a round boundary is crossed. */
    data class RoundEvent(
        val roundNumber: Int,
        val isPlayer: Boolean,
        val result: CombatTurnResult
    )

    /** Immutable snapshot of the real-time combat scheduler. */
    data class SchedulerState(
        val engineState: CombatEngineState,
        val clockMode: CombatRoundClock.ClockMode = CombatRoundClock.ClockMode.RUNNING,
        val roundMillis: Long = DEFAULT_ROUND_MILLIS,
        val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
        val elapsedMillis: Long = 0L,
        val millisUntilNextRound: Long = DEFAULT_ROUND_MILLIS,
        val actionQueue: List<PlayerCombatAction> = emptyList(),
        val events: List<RoundEvent> = emptyList(),
        val proximityDistance: Int = 1
    )

    fun start(
        engineState: CombatEngineState,
        roundMillis: Long = DEFAULT_ROUND_MILLIS
    ): SchedulerState = SchedulerState(
        engineState = engineState,
        roundMillis = roundMillis,
        millisUntilNextRound = roundMillis
    )

    /** Pacing view of the scheduler state for the shared CombatRoundClock. */
    private fun SchedulerState.clock(): CombatRoundClock.ClockState<PlayerCombatAction> =
        CombatRoundClock.ClockState(
            roundMillis = roundMillis,
            millisLeft = millisUntilNextRound,
            playerPaused = clockMode == CombatRoundClock.ClockMode.PAUSED,
            frozen = clockMode == CombatRoundClock.ClockMode.FROZEN,
            queue = actionQueue,
            queueCapacity = queueCapacity
        )

    /** Writes a paced clock back into the scheduler state. */
    private fun SchedulerState.fromClock(clock: CombatRoundClock.ClockState<PlayerCombatAction>): SchedulerState =
        copy(
            clockMode = clock.clockMode,
            millisUntilNextRound = clock.millisLeft,
            actionQueue = clock.queue
        )

    /**
     * Queues a player action. KOTOR-style queue semantics (shared with the
     * UI combat clock): when the queue is full a newly queued action replaces
     * the last queued one.
     */
    fun enqueue(state: SchedulerState, action: PlayerCombatAction): SchedulerState {
        if (state.engineState.isCombatOver) return state
        return state.fromClock(CombatRoundClock.enqueue(state.clock(), action))
    }

    fun clearQueue(state: SchedulerState): SchedulerState =
        state.copy(actionQueue = emptyList())

    /** Player pause: stops the combat clock until resume. */
    fun pause(state: SchedulerState): SchedulerState {
        val paced = CombatRoundClock.pause(state.clock())
        return if (paced.clockMode == state.clockMode) state else state.fromClock(paced)
    }

    /** Clears a player pause. Does not clear a hacking freeze. */
    fun resume(state: SchedulerState): SchedulerState {
        val paced = CombatRoundClock.resume(state.clock())
        return if (paced.clockMode == state.clockMode) state else state.fromClock(paced)
    }

    /**
     * Hacking breach protocol: freezes the combat clock. The world stays
     * exactly where it was until the breach resolves; a player pause set
     * before the freeze survives it (shared KOTOR semantics).
     */
    fun freezeForHacking(state: SchedulerState): SchedulerState {
        val paced = CombatRoundClock.freeze(state.clock())
        return if (paced.clockMode == state.clockMode) state else state.fromClock(paced)
    }

    /** Resumes combat after a resolved breach protocol. Does not clear a player pause. */
    fun unfreezeAfterHacking(state: SchedulerState): SchedulerState {
        val paced = CombatRoundClock.unfreeze(state.clock())
        return if (paced.clockMode == state.clockMode) state else state.fromClock(paced)
    }

    /**
     * Advances real-time combat by deltaMillis. At most one round resolves per
     * tick so UI animations can follow each resolution deterministically; time
     * beyond a round boundary carries over toward the next round.
     */
    fun tick(
        state: SchedulerState,
        deltaMillis: Long,
        random: Random = Random.Default
    ): SchedulerState {
        if (deltaMillis <= 0L) return state
        if (state.engineState.isCombatOver) return state

        val before = state.clock()
        val paced = CombatRoundClock.tick(before, deltaMillis)

        if (paced is CombatRoundClock.TickResult.Running) {
            if (paced.state == before) return state // PAUSED/FROZEN: the world stands still
            return state.copy(
                elapsedMillis = state.elapsedMillis + deltaMillis,
                millisUntilNextRound = paced.state.millisLeft
            )
        }

        val roundElapsed = paced as CombatRoundClock.TickResult.RoundElapsed
        // A round boundary was crossed: resolve exactly one round.
        val engine = state.engineState
        val isPlayerTurn = engine.turn == EngineCombatTurn.PLAYER_TURN
        val consumed = roundElapsed.consumed
        val result: CombatTurnResult
        val queueAfter: List<PlayerCombatAction>

        if (isPlayerTurn) {
            // KOTOR-style default attack: an idle player strikes.
            val action = consumed ?: PlayerCombatAction.Strike()
            queueAfter = if (consumed != null) state.actionQueue.drop(1) else state.actionQueue
            result = TurnBasedCombatEngine.processPlayerAction(action, engine, random)
        } else {
            queueAfter = state.actionQueue
            result = TurnBasedCombatEngine.processEnemyTurn(engine, state.proximityDistance, random)
        }

        val event = RoundEvent(
            roundNumber = result.newState.turnNumber,
            isPlayer = isPlayerTurn,
            result = result
        )

        return state.copy(
            engineState = result.newState,
            actionQueue = queueAfter,
            elapsedMillis = state.elapsedMillis + deltaMillis,
            millisUntilNextRound = if (result.newState.isCombatOver) 0L else roundElapsed.state.millisLeft,
            events = state.events + event
        )
    }
}
