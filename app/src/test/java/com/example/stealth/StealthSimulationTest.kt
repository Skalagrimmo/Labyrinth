package com.example.stealth

import com.example.data.CellType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class StealthSimulationTest {
    private fun openWorld(width: Int = 7, height: Int = 5, levelId: Int = 0): LabyrinthWorldAdapter {
        val cells = Array(height) { Array(width) { CellType.PATH } }
        return LabyrinthWorldAdapter(cells = cells, levelId = levelId)
    }

    @Test
    fun adapterUsesLabyrinthRowMajorCoordinates() {
        val cells = Array(4) { Array(6) { CellType.PATH } }
        cells[2][4] = CellType.WALL
        val world = LabyrinthWorldAdapter(cells)

        assertEquals(6, world.width)
        assertEquals(4, world.height)
        assertEquals(CellType.WALL, world.tileAt(4, 2))
        assertFalse(world.isWalkable(4, 2))
        assertTrue(world.isWalkable(2, 3))
        assertFalse(world.isWalkable(-1, 0))
    }

    @Test
    fun pathfinderRoutesAroundBlockedCell() {
        val cells = Array(5) { Array(7) { CellType.PATH } }
        cells[2][3] = CellType.WALL
        val world = LabyrinthWorldAdapter(cells)

        val path = world.findPath(GridCell(1, 2), GridCell(5, 2))

        assertNotNull(path)
        assertEquals(GridCell(5, 2), path!!.last())
        assertTrue(path.size > 4)
        assertFalse(path.contains(GridCell(3, 2)))
    }

    @Test
    fun pathfinderReturnsNullWhenTargetIsSealedOff() {
        val cells = Array(3) { Array(5) { CellType.PATH } }
        cells[0][2] = CellType.WALL
        cells[1][2] = CellType.WALL
        cells[2][2] = CellType.WALL
        val world = LabyrinthWorldAdapter(cells)

        assertNull(world.findPath(GridCell(0, 1), GridCell(4, 1)))
    }

    @Test
    fun lineOfSightIsBlockedByWall() {
        val cells = Array(5) { Array(7) { CellType.PATH } }
        cells[2][3] = CellType.WALL
        val world = LabyrinthWorldAdapter(cells)

        assertFalse(
            world.hasLineOfSight(
                StealthPosition(1.5f, 2.5f),
                StealthPosition(5.5f, 2.5f)
            )
        )
        assertTrue(
            world.hasLineOfSight(
                StealthPosition(1.5f, 1.5f),
                StealthPosition(5.5f, 1.5f)
            )
        )
    }

    @Test
    fun guardRaisesAlertAfterSustainedVisibility() {
        val world = openWorld()
        val guard = StealthGuard(
            id = "guard-1",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            viewRange = 6f,
            fieldOfViewRadians = PI.toFloat() / 2f,
            detectionRatePerSecond = 80f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val player = StealthPlayer(StealthPosition(2.5f, 2.5f))

        val events = mutableListOf<StealthEvent>()
        repeat(20) {
            events += simulation.update(0.1f, player)
        }

        assertEquals(StealthGuardState.ALERTED, guard.state)
        assertTrue(events.any { it.type == StealthEventType.PLAYER_DETECTED })
    }

    @Test
    fun guardCannotSeeThroughWall() {
        val cells = Array(5) { Array(7) { CellType.PATH } }
        cells[2][3] = CellType.WALL
        val world = LabyrinthWorldAdapter(cells)
        val guard = StealthGuard(
            id = "guard-wall",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            viewRange = 6f,
            fieldOfViewRadians = PI.toFloat() / 2f,
            detectionRatePerSecond = 100f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val player = StealthPlayer(StealthPosition(5.5f, 2.5f))

        repeat(20) { simulation.update(0.1f, player) }

        assertEquals(StealthGuardState.PATROLLING, guard.state)
        assertEquals(0f, guard.suspicion, 0.001f)
    }

    @Test
    fun guardInvestigatesNoiseWhenPlayerIsOutOfView() {
        val world = openWorld()
        val guard = StealthGuard(
            id = "guard-noise",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = PI.toFloat(),
            fieldOfViewRadians = PI.toFloat() / 3f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val player = StealthPlayer(StealthPosition(5.5f, 2.5f))
        val pulse = NoisePulse(StealthPosition(2.5f, 2.5f), radius = 2.5f)

        val events = simulation.update(0.1f, player, listOf(pulse))

        assertEquals(StealthGuardState.INVESTIGATING, guard.state)
        assertTrue(events.any { it.type == StealthEventType.NOISE_HEARD })
    }

    @Test
    fun invisiblePlayerDoesNotBuildDetectionMeter() {
        val world = openWorld()
        val guard = StealthGuard(
            id = "guard-cloak",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            fieldOfViewRadians = PI.toFloat() / 2f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val player = StealthPlayer(StealthPosition(2.5f, 2.5f), isInvisible = true)

        repeat(10) { simulation.update(0.1f, player) }

        assertEquals(0f, guard.suspicion, 0.001f)
        assertEquals(StealthGuardState.PATROLLING, guard.state)
    }

    @Test
    fun guardPatrolRouteNeverMovesThroughBlockedCells() {
        val cells = Array(5) { Array(7) { CellType.PATH } }
        cells[2][3] = CellType.WALL
        val world = LabyrinthWorldAdapter(cells)
        val guard = StealthGuard(
            id = "guard-route",
            position = StealthPosition(1.5f, 2.5f),
            patrolRoute = listOf(GridCell(5, 2)),
            patrolSpeed = 0.75f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val playerOnAnotherLayer = StealthPlayer(StealthPosition(6.5f, 4.5f, levelId = 1))

        repeat(100) {
            simulation.update(0.1f, playerOnAnotherLayer)
            assertTrue(world.isWalkable(guard.position.cell()))
        }

        assertEquals(GridCell(5, 2), guard.position.cell())
    }


    @Test
    fun guardSearchesLastKnownNoisePositionThenReturnsToPatrol() {
        val world = openWorld()
        val guard = StealthGuard(
            id = "guard-search",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = PI.toFloat(),
            searchDurationSeconds = 1.0f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val playerOnAnotherLayer = StealthPlayer(StealthPosition(6.5f, 4.5f, levelId = 1))
        val noise = NoisePulse(StealthPosition(2.5f, 2.5f), radius = 2.5f)

        simulation.update(0.1f, playerOnAnotherLayer, listOf(noise))
        assertEquals(StealthGuardState.INVESTIGATING, guard.state)

        repeat(40) { simulation.update(0.1f, playerOnAnotherLayer) }

        assertEquals(StealthGuardState.PATROLLING, guard.state)
        assertEquals(0f, guard.suspicion, 0.001f)
        assertNull(guard.lastKnownPlayerPosition)
    }

    @Test
    fun suspicionAccumulationIsTimeBasedAcrossTickRates() {
        fun suspicionAfterOneSecond(stepSeconds: Float, count: Int): Float {
            val world = openWorld()
            val guard = StealthGuard(
                id = "guard-rate",
                position = StealthPosition(1.5f, 2.5f),
                facingAngleRadians = 0f,
                viewRange = 6f,
                fieldOfViewRadians = PI.toFloat() / 2f,
                detectionRatePerSecond = 60f,
                patrolSpeed = 0f,
                investigationSpeed = 0f,
                chaseSpeed = 0f
            )
            val simulation = StealthSimulation(world, listOf(guard))
            val player = StealthPlayer(StealthPosition(2.5f, 2.5f))
            repeat(count) { simulation.update(stepSeconds, player) }
            return guard.suspicion
        }

        val thirtyHz = suspicionAfterOneSecond(1f / 30f, 30)
        val sixtyHz = suspicionAfterOneSecond(1f / 60f, 60)

        assertEquals(thirtyHz, sixtyHz, 0.1f)
    }

    @Test
    fun zeroOrNegativeDeltaDoesNotAdvanceSimulation() {
        val world = openWorld()
        val guard = StealthGuard(
            id = "guard-delta",
            position = StealthPosition(1.5f, 2.5f),
            patrolRoute = listOf(GridCell(4, 2))
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val before = guard.position

        simulation.update(0f, StealthPlayer(StealthPosition(6.5f, 4.5f)))
        simulation.update(-0.5f, StealthPlayer(StealthPosition(6.5f, 4.5f)))

        assertEquals(before, guard.position)
    }
}
