package com.example.stealth

import com.example.core.model.CellType
import com.example.core.model.CyberWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabyrinthStealthSessionWeatherTest {
    private fun openMaze(width: Int = 5, height: Int = 5): Array<Array<CellType>> =
        Array(height) { Array(width) { CellType.PATH } }

    private fun stationaryGuard(id: String): StealthGuard = StealthGuard(
        id = id,
        position = StealthPosition(0.5f, 0.5f),
        facingAngleRadians = 0f
    )

    @Test
    fun clearWeatherNeverStirsIceOnItsOwn() {
        val guard = stationaryGuard("guard-clear")
        val session = LabyrinthStealthSession(openMaze(), listOf(guard), levelId = 0)
        val start = guard.position

        repeat(100) {
            session.update(0.1f, gridX = 4, gridY = 4, weather = CyberWeather.CLEAR)
        }

        assertEquals(StealthGuardState.PATROLLING, guard.state)
        assertEquals(0f, guard.suspicion, 0.001f)
        assertEquals(start, guard.position)
    }

    @Test
    fun echoWeatherLuresIceOffItsPost() {
        val guard = stationaryGuard("guard-echo")
        val session = LabyrinthStealthSession(openMaze(), listOf(guard), levelId = 0)
        val start = guard.position

        // The runner parks out of sight in the far corner; echoes pulse every
        // ECHO_PULSE_INTERVAL_TICKS and every candidate cell of this small maze
        // lies within the ICE hearing radius, so the first pulse must land.
        var ticks = 0
        val maxTicks = 3 * LabyrinthStealthSession.ECHO_PULSE_INTERVAL_TICKS
        while (guard.state == StealthGuardState.PATROLLING && ticks < maxTicks) {
            session.update(0.1f, gridX = 4, gridY = 4, weather = CyberWeather.ECHOES)
            ticks++
        }

        assertTrue("ICE should have heard a phantom echo", ticks < maxTicks)
        assertTrue(
            guard.state == StealthGuardState.INVESTIGATING ||
                guard.state == StealthGuardState.SEARCHING
        )
        assertTrue(guard.position.distanceTo(start) > 0.1f)
    }

    @Test
    fun echoSequenceIsFullyDeterministic() {
        fun runSession(): StealthGuard {
            val guard = stationaryGuard("guard-det")
            val session = LabyrinthStealthSession(openMaze(), listOf(guard), levelId = 3)
            repeat(90) {
                session.update(0.1f, gridX = 4, gridY = 4, weather = CyberWeather.ECHOES)
            }
            return guard
        }

        val first = runSession()
        val second = runSession()

        assertEquals(first.position, second.position)
        assertEquals(first.state, second.state)
        assertEquals(first.suspicion, second.suspicion, 0.0001f)
    }

    @Test
    fun echoesNeverSpawnCloseToTheRunner() {
        // Only the runner's own corner is walkable: no echo candidate beyond
        // the exclusion radius exists, so no pulse may ever fire.
        val maze = Array(5) { Array(5) { CellType.WALL } }
        maze[3][3] = CellType.PATH
        maze[3][4] = CellType.PATH
        maze[4][3] = CellType.PATH
        maze[4][4] = CellType.PATH
        val guard = stationaryGuard("guard-none")
        val session = LabyrinthStealthSession(maze, listOf(guard), levelId = 0)

        repeat(120) {
            session.update(0.1f, gridX = 4, gridY = 4, weather = CyberWeather.ECHOES)
        }

        assertEquals(StealthGuardState.PATROLLING, guard.state)
        assertEquals(0f, guard.suspicion, 0.001f)
    }
}
