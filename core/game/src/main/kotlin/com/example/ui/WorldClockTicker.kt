package com.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single shared 100ms world-clock loop that drives every RTwP subsystem
 * (d6 slice 5: unified pacing driver).
 *
 * Before this the combat round clock and the stealth world clock each ran
 * their own coroutine ticker with identical plumbing; now exactly one loop
 * serves both consumers and phase relevance is decided by the consumers
 * themselves - the stealth controller freezes the world while a legacy
 * combat is active, the combat controller disengages once the encounter is
 * over. The KOTOR pacing semantics themselves still live exactly once, in
 * :core:engine (CombatRoundClock / RealTimeGameSession).
 *
 * The loop is NOT started on construction (same rule as the controllers):
 * GameViewModel starts it when a run actually begins and the stealth
 * controller stops it on menu screens, so no coroutine spins during menus
 * or UI tests.
 */
class WorldClockTicker(
    private val scope: CoroutineScope,
    private val tickMillis: Long = TICK_MILLIS
) {
    private var tickerJob: Job? = null
    private val consumers = mutableListOf<() -> Unit>()

    /** True while the shared world-clock loop is dispatching ticks. */
    val isRunning: Boolean get() = tickerJob?.isActive == true

    /**
     * Registers a world-tick consumer. Idempotent for the same consumer
     * reference (controllers subscribe once, on construction); the loop
     * itself is controlled by [start]/[stop], not by subscription.
     */
    fun subscribe(consumer: () -> Unit) {
        if (consumers.contains(consumer)) return
        consumers.add(consumer)
    }

    /** Starts the shared loop (idempotent: exactly one loop per ticker). */
    fun start() {
        if (isRunning) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(tickMillis)
                // Snapshot: subscribing mid-tick must not mutate the
                // consumer list under iteration.
                consumers.toList().forEach { it() }
            }
        }
    }

    /** Stops the shared loop. Subscriptions survive a restart. */
    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        /** KOTOR-style world-clock granularity shared by every RTwP subsystem. */
        const val TICK_MILLIS: Long = 100L
    }
}
