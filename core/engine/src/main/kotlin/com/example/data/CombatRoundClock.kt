package com.example.data

/**
 * Shared pure pacing core of the KOTOR-style real-time-with-pause clock.
 *
 * Single source of the RTwP timing semantics for both consumers:
 *  - core:engine CombatRoundScheduler (deterministic round resolution);
 *  - the app-level CombatClockController (legacy CombatManager flow).
 *
 * Semantics: a 3 second round window; time beyond a round boundary carries
 * over toward the next round; an action queue with depth 4 where a full
 * queue replaces the last entry; an orthogonal player pause and hacking
 * freeze (the freeze wins while active and a player pause survives it).
 */
object CombatRoundClock {

    /** KOTOR-style default combat round length. */
    const val DEFAULT_ROUND_MILLIS: Long = 3_000L

    /** KOTOR-style default action queue depth. */
    const val DEFAULT_QUEUE_CAPACITY: Int = 4

    /** Real-time clock mode (the hacking freeze wins over the player pause). */
    enum class ClockMode { RUNNING, PAUSED, FROZEN }

    /**
     * Immutable pacing state, generic over the queued action type: the core
     * scheduler queues PlayerCombatAction, the app HUD queues its own
     * UI-labelled actions.
     */
    data class ClockState<T>(
        val roundMillis: Long = DEFAULT_ROUND_MILLIS,
        val millisLeft: Long = DEFAULT_ROUND_MILLIS,
        val playerPaused: Boolean = false,
        val frozen: Boolean = false,
        val queue: List<T> = emptyList(),
        val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
    ) {
        val clockMode: ClockMode
            get() = when {
                frozen -> ClockMode.FROZEN
                playerPaused -> ClockMode.PAUSED
                else -> ClockMode.RUNNING
            }

        val isRunning: Boolean get() = clockMode == ClockMode.RUNNING
    }

    /** Result of one pacing tick. */
    sealed interface TickResult<T> {
        /** No round boundary yet (or the clock is paused/frozen). */
        data class Running<T>(val state: ClockState<T>) : TickResult<T>

        /**
         * A round boundary was crossed: [consumed] holds the head of the
         * queue (null when empty - the caller applies its idle default, e.g.
         * the KOTOR Strike). The queue is NOT modified here; the caller
         * drops the executed head via [consumeHead].
         */
        data class RoundElapsed<T>(val state: ClockState<T>, val consumed: T?) : TickResult<T>
    }

    fun <T> start(
        roundMillis: Long = DEFAULT_ROUND_MILLIS,
        queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
    ): ClockState<T> = ClockState(
        roundMillis = roundMillis,
        millisLeft = roundMillis,
        queueCapacity = queueCapacity
    )

    /** KOTOR queue semantics: when the queue is full a newly queued action replaces the last queued one. */
    fun <T> enqueue(state: ClockState<T>, action: T): ClockState<T> =
        state.copy(
            queue = if (state.queue.size >= state.queueCapacity) {
                state.queue.dropLast(1) + action
            } else {
                state.queue + action
            }
        )

    fun <T> clearQueue(state: ClockState<T>): ClockState<T> =
        state.copy(queue = emptyList())

    /** Drops the head of the queue after the caller executed it. */
    fun <T> consumeHead(state: ClockState<T>): ClockState<T> =
        state.copy(queue = state.queue.drop(1))

    /** Player pause: no-op while a hacking freeze is active. */
    fun <T> pause(state: ClockState<T>): ClockState<T> =
        if (state.frozen) state else state.copy(playerPaused = true)

    /** Clears the player pause: does not clear a hacking freeze. */
    fun <T> resume(state: ClockState<T>): ClockState<T> =
        if (state.frozen) state else state.copy(playerPaused = false)

    /** Breach protocol freeze: works from RUNNING and PAUSED; a player pause survives it. */
    fun <T> freeze(state: ClockState<T>): ClockState<T> =
        state.copy(frozen = true)

    /** Resumes after a resolved breach: a surviving player pause stays. */
    fun <T> unfreeze(state: ClockState<T>): ClockState<T> =
        state.copy(frozen = false)

    /** Fresh full round window (a new player decision window begins). */
    fun <T> resetWindow(state: ClockState<T>): ClockState<T> =
        state.copy(millisLeft = state.roundMillis)

    /**
     * Advances the clock by deltaMillis. At most one round boundary per
     * tick; time beyond the boundary carries over toward the next round.
     */
    fun <T> tick(state: ClockState<T>, deltaMillis: Long): TickResult<T> {
        if (deltaMillis <= 0L || !state.isRunning) return TickResult.Running(state)
        val remaining = state.millisLeft - deltaMillis
        if (remaining > 0L) return TickResult.Running(state.copy(millisLeft = remaining))
        return TickResult.RoundElapsed(
            state = state.copy(millisLeft = state.roundMillis + remaining),
            consumed = state.queue.firstOrNull()
        )
    }
}
