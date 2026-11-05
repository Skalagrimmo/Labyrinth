package com.example.data

import kotlin.random.Random

/**
 * KOTOR-style real-time-with-pause pacing layer over the deterministic
 * turn-based combat engine.
 *
 * Combat time only advances while the clock is RUNNING:
 * - PAUSED: the player paused combat;
 * - FROZEN: a hacking breach protocol froze the combat clock.
 *
 * The deterministic resolution core is unchanged: when a round boundary is
 * crossed the scheduler delegates to TurnBasedCombatEngine, so all existing
 * combat rules, characterization tests and save compatibility are preserved.
 */
object CombatRoundScheduler {

    /** KOTOR-style default combat round length. */
    const val DEFAULT_ROUND_MILLIS: Long = 3_000L

    /** KOTOR-style default action queue depth. */
    const val DEFAULT_QUEUE_CAPACITY: Int = 4

    /** Real-time clock mode of the combat scheduler. */
    enum class ClockMode { RUNNING, PAUSED, FROZEN }

    /** One resolved combat round, emitted by tick when a round boundary is crossed. */
    data class RoundEvent(
        val roundNumber: Int,
        val isPlayer: Boolean,
        val result: CombatTurnResult
    )

    /** Immutable snapshot of the real-time combat scheduler. */
    data class SchedulerState(
        val engineState: CombatEngineState,
        val clockMode: ClockMode = ClockMode.RUNNING,
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

    /**
     * Queues a player action. KOTOR-style queue semantics: when the queue is
     * full a newly queued action replaces the last queued one.
     */
    fun enqueue(state: SchedulerState, action: PlayerCombatAction): SchedulerState {
        if (state.engineState.isCombatOver) return state
        val queue = if (state.actionQueue.size >= state.queueCapacity) {
            state.actionQueue.dropLast(1) + action
        } else {
            state.actionQueue + action
        }
        return state.copy(actionQueue = queue)
    }

    fun clearQueue(state: SchedulerState): SchedulerState =
        state.copy(actionQueue = emptyList())

    /** Player pause: stops the combat clock until resume. */
    fun pause(state: SchedulerState): SchedulerState =
        if (state.clockMode == ClockMode.RUNNING) {
            state.copy(clockMode = ClockMode.PAUSED)
        } else {
            state
        }

    /** Clears a player pause. Does not clear a hacking freeze. */
    fun resume(state: SchedulerState): SchedulerState =
        if (state.clockMode == ClockMode.PAUSED) {
            state.copy(clockMode = ClockMode.RUNNING)
        } else {
            state
        }

    /**
     * Hacking breach protocol: freezes the combat clock. The world stays
     * exactly where it was until the breach resolves.
     */
    fun freezeForHacking(state: SchedulerState): SchedulerState =
        if (state.clockMode == ClockMode.RUNNING) {
            state.copy(clockMode = ClockMode.FROZEN)
        } else {
            state
        }

    /** Resumes combat after a resolved breach protocol. Does not clear a player pause. */
    fun unfreezeAfterHacking(state: SchedulerState): SchedulerState =
        if (state.clockMode == ClockMode.FROZEN) {
            state.copy(clockMode = ClockMode.RUNNING)
        } else {
            state
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
        if (state.clockMode != ClockMode.RUNNING) return state
        if (state.engineState.isCombatOver) return state

        var current = state.copy(elapsedMillis = state.elapsedMillis + deltaMillis)
        val remaining = current.millisUntilNextRound - deltaMillis

        if (remaining > 0L) {
            return current.copy(millisUntilNextRound = remaining)
        }

        // A round boundary was crossed: resolve exactly one round.
        val engine = current.engineState
        val isPlayerTurn = engine.turn == EngineCombatTurn.PLAYER_TURN
        val result: CombatTurnResult
        val queueAfter: List<PlayerCombatAction>

        if (isPlayerTurn) {
            // KOTOR-style default attack: an idle player strikes.
            val action = current.actionQueue.firstOrNull() ?: PlayerCombatAction.Strike()
            queueAfter = if (current.actionQueue.isEmpty()) {
                emptyList()
            } else {
                current.actionQueue.drop(1)
            }
            result = TurnBasedCombatEngine.processPlayerAction(action, engine, random)
        } else {
            queueAfter = current.actionQueue
            result = TurnBasedCombatEngine.processEnemyTurn(engine, current.proximityDistance, random)
        }

        val event = RoundEvent(
            roundNumber = result.newState.turnNumber,
            isPlayer = isPlayerTurn,
            result = result
        )

        current = current.copy(
            engineState = result.newState,
            actionQueue = queueAfter,
            millisUntilNextRound = if (result.newState.isCombatOver) 0L else current.roundMillis + remaining,
            events = current.events + event
        )
        return current
    }
}
