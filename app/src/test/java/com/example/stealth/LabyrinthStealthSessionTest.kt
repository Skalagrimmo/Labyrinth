package com.example.stealth

import com.example.data.CellType
import com.example.data.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class LabyrinthStealthSessionTest {

    @Test
    fun convertsLabyrinthGridCoordinatesToCellCentre() {
        val guard = stationaryGuard(
            id = "guard-coordinates",
            position = StealthPosition(1.5f, 2.5f),
            facing = 0f
        )
        val session = LabyrinthStealthSession(openMaze(), listOf(guard))

        val frame = session.update(
            deltaTimeSeconds = 0f,
            gridX = 4,
            gridY = 3
        )

        assertEquals(StealthPosition(4.5f, 3.5f), frame.playerPosition)
        assertFalse(frame.playerDetected)
    }

    @Test
    fun detectsPlayerAndExposesAlertStateForUi() {
        val guard = StealthGuard(
            id = "guard-detection",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            viewRange = 6f,
            fieldOfViewRadians = PI.toFloat() / 2f,
            detectionRatePerSecond = 150f,
            patrolSpeed = 0f,
            investigationSpeed = 0f,
            chaseSpeed = 0f
        )
        val session = LabyrinthStealthSession(openMaze(), listOf(guard))

        var frame = session.snapshot()
        repeat(4) {
            frame = session.update(
                deltaTimeSeconds = 0.25f,
                gridX = 4,
                gridY = 2
            )
        }

        assertTrue(frame.playerDetected)
        assertEquals(StealthGuardState.ALERTED, frame.guards.single().state)
        assertTrue(frame.events.any { it.type == StealthEventType.PLAYER_DETECTED })
    }

    @Test
    fun replacingMazeRefreshesLineOfSightAndDetection() {
        val guard = StealthGuard(
            id = "guard-wall-update",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            viewRange = 6f,
            fieldOfViewRadians = PI.toFloat() / 2f,
            detectionRatePerSecond = 60f,
            suspicionRecoveryPerSecond = 30f,
            patrolSpeed = 0f,
            investigationSpeed = 0f,
            chaseSpeed = 0f
        )
        val session = LabyrinthStealthSession(openMaze(), listOf(guard))

        val beforeWall = session.update(
            deltaTimeSeconds = 0.25f,
            gridX = 4,
            gridY = 2
        ).guards.single().suspicion

        val blockedMaze = openMaze()
        blockedMaze[2][3] = CellType.WALL
        session.replaceMaze(blockedMaze)

        val afterWall = session.update(
            deltaTimeSeconds = 0.25f,
            gridX = 4,
            gridY = 2
        ).guards.single().suspicion

        assertTrue(beforeWall > 0f)
        assertTrue(afterWall < beforeWall)
    }

    @Test
    fun noisePulsesReturnAsEventsWithoutRendererDependency() {
        val guard = stationaryGuard(
            id = "guard-hearing",
            position = StealthPosition(1.5f, 2.5f),
            facing = 0f
        )
        val session = LabyrinthStealthSession(openMaze(), listOf(guard))

        val frame = session.update(
            deltaTimeSeconds = 0.1f,
            gridX = 5,
            gridY = 4,
            isInvisible = true,
            noisePulses = listOf(
                NoisePulse(
                    position = StealthPosition(2.5f, 2.5f),
                    radius = 2f,
                    intensity = 1f
                )
            )
        )

        assertTrue(frame.events.any { it.type == StealthEventType.NOISE_HEARD })
        assertEquals(StealthGuardState.INVESTIGATING, frame.guards.single().state)
    }

    @Test
    fun directionMappingMatchesGridCoordinates() {
        assertEquals(0f, LabyrinthStealthSession.angleForDirection(Direction.EAST), 0.0001f)
        assertEquals(PI.toFloat() / 2f, LabyrinthStealthSession.angleForDirection(Direction.SOUTH), 0.0001f)
        assertEquals(PI.toFloat(), LabyrinthStealthSession.angleForDirection(Direction.WEST), 0.0001f)
        assertEquals(-PI.toFloat() / 2f, LabyrinthStealthSession.angleForDirection(Direction.NORTH), 0.0001f)
    }

    private fun stationaryGuard(
        id: String,
        position: StealthPosition,
        facing: Float
    ) = StealthGuard(
        id = id,
        position = position,
        facingAngleRadians = facing,
        patrolSpeed = 0f,
        investigationSpeed = 0f,
        chaseSpeed = 0f
    )

    private fun openMaze(width: Int = 7, height: Int = 6): Array<Array<CellType>> =
        Array(height) { Array(width) { CellType.PATH } }
}
