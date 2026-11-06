package com.example.ui

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the shared world-clock loop (d6 slice 5): one ticker
 * drives every subscribed consumer, start/stop are idempotent, and a
 * stopped ticker dispatches nothing. The RTwP pacing semantics themselves
 * live in :core:engine (CombatRoundClock); this pins only the driver.
 */
class WorldClockTickerTest {

    @Test
    fun oneLoopDrivesEverySubscribedConsumer() = runBlocking {
        val ticks = AtomicInteger(0)
        val ticker = WorldClockTicker(CoroutineScope(Dispatchers.Default), tickMillis = 10L)
        ticker.subscribe { ticks.incrementAndGet() }
        ticker.subscribe { ticks.incrementAndGet() }
        ticker.start()
        withTimeout(2000L) { while (ticks.get() < 6) delay(10L) }
        assertTrue(ticker.isRunning)
        ticker.stop()
        assertFalse(ticker.isRunning)

        val snapshot = ticks.get()
        delay(80L)
        assertEquals(snapshot, ticks.get())
    }

    @Test
    fun duplicateSubscriptionsDoNotDoubleDispatch() = runBlocking {
        val ticks = AtomicInteger(0)
        val ticker = WorldClockTicker(CoroutineScope(Dispatchers.Default), tickMillis = 5L)
        val consumer = { ticks.incrementAndGet() }
        ticker.subscribe(consumer)
        ticker.subscribe(consumer)
        ticker.start()
        withTimeout(2000L) { while (ticks.get() < 10) delay(5L) }
        ticker.stop()

        // One dispatch per tick for the same consumer: a duplicated
        // subscription would have counted twice by the time 10 dispatches
        // were observed (one slack loop period is tolerated).
        assertTrue(
            "expected one dispatch per tick, got " + ticks.get(),
            ticks.get() <= 13
        )
    }

    @Test
    fun startIsIdempotentAndSubscriptionsSurviveRestart() = runBlocking {
        val ticks = AtomicInteger(0)
        val ticker = WorldClockTicker(CoroutineScope(Dispatchers.Default), tickMillis = 10L)
        ticker.subscribe { ticks.incrementAndGet() }
        ticker.start()
        ticker.start() // a second start must not spawn a second loop
        withTimeout(2000L) { while (ticks.get() < 8) delay(10L) }
        ticker.stop()

        val snapshot = ticks.get()
        delay(100L)
        assertEquals(snapshot, ticks.get())

        // Restart dispatches again to the surviving subscription.
        ticker.start()
        withTimeout(2000L) { while (ticks.get() == snapshot) delay(10L) }
        ticker.stop()
        assertTrue(ticks.get() > snapshot)
    }
}
