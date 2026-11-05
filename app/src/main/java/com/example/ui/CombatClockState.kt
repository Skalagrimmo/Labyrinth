package com.example.ui

/**
 * Pure state of the KOTOR-style real-time-with-pause combat clock that paces
 * the legacy combat flow at UI level.
 *
 * Mirrors the semantics of the core:engine CombatRoundScheduler: a 3 second
 * round window, an action queue with depth 4 (a full queue replaces the last
 * entry), PAUSED / FROZEN clock modes and the idle default Strike. The pure
 * reducer is JVM-testable without Android dependencies.
 */
data class CombatClockState(
    val millisLeft: Long = DEFAULT_ROUND_MILLIS,
    val roundMillis: Long = DEFAULT_ROUND_MILLIS,
    val isPaused: Boolean = false,
    val isFrozen: Boolean = false,
    val queue: List<QueuedCombatAction> = emptyList(),
    val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
) {
    val isRunning: Boolean get() = !isPaused && !isFrozen

    companion object {
        const val DEFAULT_ROUND_MILLIS: Long = 3_000L
        const val DEFAULT_QUEUE_CAPACITY: Int = 4
    }
}

/** Action types that can be queued from the combat HUD. */
enum class QueuedCombatActionType { STRIKE, DEFEND, SCAN }

/** One entry of the combat action queue. */
data class QueuedCombatAction(
    val id: Long,
    val type: QueuedCombatActionType,
    val label: String
)

/**
 * Deterministic reducer for the RTwP combat clock. All transitions are pure;
 * the coroutine wiring lives in CombatClockController.
 */
object CombatClock {

    sealed interface TickOutcome {
        object None : TickOutcome
        data class RoundElapsed(val action: QueuedCombatActionType) : TickOutcome
    }

    /**
     * Advances the clock by deltaMillis. When the round window elapses the head
     * of the queue is consumed; an empty queue falls back to the KOTOR-style
     * idle default Strike. The window resets to a full round afterwards.
     */
    fun tick(state: CombatClockState, deltaMillis: Long): Pair<CombatClockState, TickOutcome> {
        if (deltaMillis <= 0L || !state.isRunning) return state to TickOutcome.None
        val remaining = state.millisLeft - deltaMillis
        if (remaining > 0L) {
            return state.copy(millisLeft = remaining) to TickOutcome.None
        }
        val head = state.queue.firstOrNull()
        val action = head?.type ?: QueuedCombatActionType.STRIKE
        val queueAfter = if (head != null) state.queue.drop(1) else state.queue
        val next = state.copy(millisLeft = state.roundMillis, queue = queueAfter)
        return next to TickOutcome.RoundElapsed(action)
    }

    /** Queues an action; a full queue replaces the last entry (KOTOR semantics). */
    fun enqueue(state: CombatClockState, type: QueuedCombatActionType, id: Long, label: String): CombatClockState {
        val action = QueuedCombatAction(id = id, type = type, label = label)
        val queue = if (state.queue.size >= state.queueCapacity) {
            state.queue.dropLast(1) + action
        } else {
            state.queue + action
        }
        return state.copy(queue = queue)
    }

    fun clearQueue(state: CombatClockState): CombatClockState = state.copy(queue = emptyList())

    /** Player pause. A hacking freeze is stronger: pausing keeps the freeze. */
    fun pause(state: CombatClockState): CombatClockState =
        if (state.isFrozen) state else state.copy(isPaused = true)

    /** Clears the player pause; does not clear a hacking freeze. */
    fun resume(state: CombatClockState): CombatClockState =
        if (state.isFrozen) state else state.copy(isPaused = false)

    /** Breach protocol freezes the clock (hacking minigame is active). */
    fun freeze(state: CombatClockState): CombatClockState = state.copy(isFrozen = true)

    /** Resumes after a resolved breach; a player pause survives. */
    fun unfreeze(state: CombatClockState): CombatClockState =
        state.copy(isFrozen = false)

    /** Fresh full round window (used when a new player turn begins). */
    fun resetRound(state: CombatClockState): CombatClockState = state.copy(millisLeft = state.roundMillis)
}
