package com.example.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationMovementRulesTest {

    @Test
    fun `clear weather forward uses facing vector`() {
        val result = ExplorationMovementRules.resolveMove(
            x = 5,
            y = 6,
            direction = Direction.EAST,
            forward = true,
            weather = CyberWeather.CLEAR,
            random = AlwaysChanceRandom
        )

        assertEquals(6, result.nextX)
        assertEquals(6, result.nextY)
        assertFalse(result.wasScrambled)
    }

    @Test
    fun `clear weather backward uses inverse facing vector`() {
        val result = ExplorationMovementRules.resolveMove(
            x = 5,
            y = 6,
            direction = Direction.EAST,
            forward = false,
            weather = CyberWeather.CLEAR,
            random = AlwaysChanceRandom
        )

        assertEquals(4, result.nextX)
        assertEquals(6, result.nextY)
        assertFalse(result.wasScrambled)
    }

    @Test
    fun `storm can scramble forward movement away from current direction`() {
        val result = ExplorationMovementRules.resolveMove(
            x = 5,
            y = 6,
            direction = Direction.EAST,
            forward = true,
            weather = CyberWeather.DATA_STORM,
            random = AlwaysChanceRandom
        )

        assertEquals(5, result.nextX)
        assertEquals(5, result.nextY)
        assertTrue(result.wasScrambled)
    }

    @Test
    fun `storm backward movement preserves legacy unrestricted scramble pool`() {
        val result = ExplorationMovementRules.resolveMove(
            x = 5,
            y = 6,
            direction = Direction.EAST,
            forward = false,
            weather = CyberWeather.APEX_STORM,
            random = AlwaysChanceRandom
        )

        assertEquals(5, result.nextX)
        assertEquals(5, result.nextY)
        assertTrue(result.wasScrambled)
    }

    @Test
    fun `clear weather turns follow requested direction`() {
        val left = ExplorationMovementRules.resolveTurn(
            direction = Direction.NORTH,
            turnLeft = true,
            weather = CyberWeather.CLEAR,
            random = AlwaysChanceRandom
        )
        val right = ExplorationMovementRules.resolveTurn(
            direction = Direction.NORTH,
            turnLeft = false,
            weather = CyberWeather.CLEAR,
            random = AlwaysChanceRandom
        )

        assertEquals(Direction.WEST, left.direction)
        assertEquals(Direction.EAST, right.direction)
        assertFalse(left.wasScrambled)
        assertFalse(right.wasScrambled)
    }

    @Test
    fun `storm reverses turn direction when scramble triggers`() {
        val left = ExplorationMovementRules.resolveTurn(
            direction = Direction.NORTH,
            turnLeft = true,
            weather = CyberWeather.DATA_STORM,
            random = AlwaysChanceRandom
        )
        val right = ExplorationMovementRules.resolveTurn(
            direction = Direction.NORTH,
            turnLeft = false,
            weather = CyberWeather.APEX_STORM,
            random = AlwaysChanceRandom
        )

        assertEquals(Direction.EAST, left.direction)
        assertEquals(Direction.WEST, right.direction)
        assertTrue(left.wasScrambled)
        assertTrue(right.wasScrambled)
    }

    @Test
    fun `ram recovery chance is capped by max ram`() {
        assertEquals(
            10,
            ExplorationMovementRules.recoverRam(9, 10, AlwaysChanceRandom)
        )
        assertEquals(
            10,
            ExplorationMovementRules.recoverRam(10, 10, AlwaysChanceRandom)
        )
        assertEquals(
            9,
            ExplorationMovementRules.recoverRam(9, 10, NeverChanceRandom)
        )
    }

    private object AlwaysChanceRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = 0
    }

    private object NeverChanceRandom : Random() {
        override fun nextBits(bitCount: Int): Int =
            if (bitCount == 24) 20_000_000 else 0

        override fun nextInt(until: Int): Int = until - 1
    }
}
