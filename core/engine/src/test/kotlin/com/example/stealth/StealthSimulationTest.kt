package com.example.stealth

import com.example.core.model.CellType
import com.example.core.model.CyberWeather
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
    fun deescalateAfterCombatKeepsAlertedGuardSearchingWithMemory() {
        val world = openWorld()
        val guard = StealthGuard(
            id = "guard-post-combat",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            viewRange = 6f,
            fieldOfViewRadians = PI.toFloat() / 2f,
            detectionRatePerSecond = 200f,
            investigationSpeed = 1.25f,
            searchDurationSeconds = 1f
        )
        val simulation = StealthSimulation(world, listOf(guard))
        val player = StealthPlayer(StealthPosition(3.5f, 2.5f))

        var safety = 0
        while (guard.state != StealthGuardState.ALERTED && safety < 40) {
            simulation.update(0.25f, player)
            safety++
        }
        assertEquals(StealthGuardState.ALERTED, guard.state)
        val remembered = guard.lastKnownPlayerPosition
        assertNotNull(remembered)

        val playerGone = StealthPlayer(StealthPosition(6.5f, 4.5f, levelId = 1))
        val events = simulation.deescalateAfterCombat()

        assertEquals(StealthGuardState.INVESTIGATING, guard.state)
        assertEquals(remembered, guard.investigationTarget)
        assertTrue(
            events.any {
                it.type == StealthEventType.GUARD_STATE_CHANGED &&
                    it.currentState == StealthGuardState.INVESTIGATING
            }
        )
        assertFalse(events.any { it.type == StealthEventType.PLAYER_DETECTED })

        // The guard walks the remembered spot, sweeps it, then returns to patrol
        // and forgets the player.
        var sweeps = 0
        while (guard.state != StealthGuardState.PATROLLING && sweeps < 200) {
            simulation.update(0.1f, playerGone)
            sweeps++
        }
        assertEquals(StealthGuardState.PATROLLING, guard.state)
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

    @Test
    fun dataStormHidesPlayerBeyondShortenedViewRange() {
        fun guardAfterTwoSeconds(weather: WeatherPerception): StealthGuard {
            val world = openWorld()
            val guard = StealthGuard(
                id = "guard-storm",
                position = StealthPosition(1.5f, 2.5f),
                facingAngleRadians = 0f,
                viewRange = 5f,
                fieldOfViewRadians = PI.toFloat() / 2f,
                detectionRatePerSecond = 100f
            )
            val simulation = StealthSimulation(world, listOf(guard))
            val player = StealthPlayer(StealthPosition(5.5f, 2.5f))
            repeat(20) { simulation.update(0.1f, player, emptyList(), weather) }
            return guard
        }

        assertEquals(StealthGuardState.ALERTED, guardAfterTwoSeconds(WeatherPerception.NONE).state)
        assertEquals(
            StealthGuardState.PATROLLING,
            guardAfterTwoSeconds(WeatherPerception.forWeather(CyberWeather.DATA_STORM)).state
        )
    }

    @Test
    fun apexStormAcceleratesSuspicionGrowth() {
        fun suspicionAfterOneTick(weather: WeatherPerception): Float {
            val world = openWorld()
            val guard = StealthGuard(
                id = "guard-apex",
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
            simulation.update(0.25f, StealthPlayer(StealthPosition(2.5f, 2.5f)), emptyList(), weather)
            return guard.suspicion
        }

        val clear = suspicionAfterOneTick(WeatherPerception.NONE)
        val apex = suspicionAfterOneTick(WeatherPerception.forWeather(CyberWeather.APEX_STORM))
        assertTrue(apex > clear)
        // APEX_STORM overdrives detection by the 1.25x detectionFactor, and
        // its 1.2x sight range additionally relaxes the distance falloff
        // (the target sits deeper inside the extended cone), so the combined
        // clear->apex ratio lands slightly above 1.25.
        val ratio = apex / clear
        assertTrue(
            "expected apex/clear suspicion ratio in 1.2..1.35, got " + ratio,
            ratio in 1.2f..1.35f
        )
    }

    @Test
    fun coldSpotDampensNoiseRadiusWhileEchoesHeightenIt() {
        fun hears(weather: WeatherPerception): Boolean {
            val world = openWorld()
            val guard = StealthGuard(
                id = "guard-cold",
                position = StealthPosition(1.5f, 2.5f),
                facingAngleRadians = PI.toFloat(),
                fieldOfViewRadians = PI.toFloat() / 3f
            )
            val simulation = StealthSimulation(world, listOf(guard))
            val playerOnAnotherLayer = StealthPlayer(StealthPosition(6.5f, 4.5f, levelId = 1))
            val events = simulation.update(
                0.1f,
                playerOnAnotherLayer,
                listOf(NoisePulse(StealthPosition(6.2f, 2.5f), radius = 5f)),
                weather
            )
            return events.any { it.type == StealthEventType.NOISE_HEARD }
        }

        // The pulse sits 4.7 cells away: clear weather (radius 5.0) hears it,
        // a frozen sector (0.9 x 5.0 = 4.5) does not, echoes (1.25 x 5.0 = 6.25) do.
        assertTrue(hears(WeatherPerception.NONE))
        assertFalse(hears(WeatherPerception.forWeather(CyberWeather.COLD_SPOT)))
        assertTrue(hears(WeatherPerception.forWeather(CyberWeather.ECHOES)))
    }

    @Test
    fun hotNodeSpeedsUpPatrolMovement() {
        fun distanceCovered(weather: WeatherPerception): Float {
            val world = openWorld()
            val guard = StealthGuard(
                id = "guard-hot",
                position = StealthPosition(1.5f, 2.5f),
                patrolRoute = listOf(GridCell(6, 2)),
                patrolSpeed = 1f
            )
            val simulation = StealthSimulation(world, listOf(guard))
            val playerOnAnotherLayer = StealthPlayer(StealthPosition(6.5f, 4.5f, levelId = 1))
            repeat(10) { simulation.update(0.1f, playerOnAnotherLayer, emptyList(), weather) }
            return guard.position.distanceTo(StealthPosition(1.5f, 2.5f))
        }

        val clear = distanceCovered(WeatherPerception.NONE)
        val hot = distanceCovered(WeatherPerception.forWeather(CyberWeather.HOT_NODE))
        assertEquals(1f, clear, 0.01f)
        // HOT_NODE overclocks patrol speed by 1.15x, but patrol movement
        // snaps onto cell centers along the A* path: at each cell boundary
        // the overshoot of the crossing step is absorbed, so the measured
        // distance lands slightly under 1.15 while staying strictly above
        // the clear-weather pace.
        assertTrue(
            "expected HOT_NODE patrol distance in 1.05..1.16, got " + hot,
            hot in 1.05f..1.16f
        )
        assertTrue(hot > clear)
    }

    @Test
    fun alertedGuardRelaysTheAlarmToNearbyUnits() {
        // d8 slice 3: the unit that spots the runner broadcasts the detection
        // spot once; units inside the relay radius converge on it, units
        // beyond it never hear the call.
        val world = openWorld(width = 13)
        val alerter = StealthGuard(
            id = "guard-alarm",
            position = StealthPosition(1.5f, 2.5f),
            facingAngleRadians = 0f,
            viewRange = 6f,
            fieldOfViewRadians = PI.toFloat() / 2f,
            detectionRatePerSecond = 80f
        )
        val near = StealthGuard(
            id = "guard-near",
            position = StealthPosition(5.5f, 2.5f),
            facingAngleRadians = 0f,
            fieldOfViewRadians = PI.toFloat() / 3f
        )
        val far = StealthGuard(
            id = "guard-far",
            position = StealthPosition(11.5f, 2.5f),
            facingAngleRadians = 0f,
            fieldOfViewRadians = PI.toFloat() / 3f
        )
        val simulation = StealthSimulation(world, listOf(alerter, near, far))
        val player = StealthPlayer(StealthPosition(2.5f, 2.5f))

        val events = mutableListOf<StealthEvent>()
        var safety = 0
        while (alerter.state != StealthGuardState.ALERTED && safety < 40) {
            events += simulation.update(0.1f, player)
            safety++
        }

        assertEquals(StealthGuardState.ALERTED, alerter.state)
        // The nearby unit (3 cells from the detection spot) is relayed the
        // alarm: it turns to investigate the spot, primed to the
        // coordination suspicion floor.
        assertEquals(StealthGuardState.INVESTIGATING, near.state)
        assertEquals(StealthPosition(2.5f, 2.5f), near.investigationTarget)
        assertEquals(40f, near.suspicion, 0.001f)
        // The distant unit (9 cells out) never hears the call.
        assertEquals(StealthGuardState.PATROLLING, far.state)
        assertEquals(0f, far.suspicion, 0.001f)
        assertTrue(
            events.any {
                it.type == StealthEventType.ICE_ALERT_RELAYED && it.guardId == "guard-near"
            }
        )
        assertFalse(
            events.any {
                it.type == StealthEventType.ICE_ALERT_RELAYED && it.guardId == "guard-far"
            }
        )
    }

    @Test
    fun alertRelayRangeTracksTheWeatherPropagation() {
        // The alarm call rides the same medium as sound: its effective radius
        // is the coordination base scaled by the weather's hearingFactor.
        fun relayed(weather: WeatherPerception): Boolean {
            val world = openWorld(width = 12)
            val alerter = StealthGuard(
                id = "guard-alarm",
                position = StealthPosition(1.5f, 2.5f),
                facingAngleRadians = 0f,
                viewRange = 6f,
                fieldOfViewRadians = PI.toFloat() / 2f,
                detectionRatePerSecond = 80f
            )
            val edge = StealthGuard(
                id = "guard-edge",
                position = StealthPosition(9.5f, 2.5f),
                facingAngleRadians = 0f,
                fieldOfViewRadians = PI.toFloat() / 3f
            )
            val simulation = StealthSimulation(world, listOf(alerter, edge))
            val player = StealthPlayer(StealthPosition(2.5f, 2.5f))
            var safety = 0
            while (alerter.state != StealthGuardState.ALERTED && safety < 40) {
                simulation.update(0.1f, player, emptyList(), weather)
                safety++
            }
            return edge.state == StealthGuardState.INVESTIGATING
        }

        // The edge unit sits 7 cells from the detection spot: clear bandwidth
        // (radius 8.0) relays the alarm, a ghost-protocol sector (8 x 0.85 =
        // 6.8) mutes it, an apex storm (8 x 1.15 = 9.2) carries it.
        assertTrue(relayed(WeatherPerception.NONE))
        assertFalse(relayed(WeatherPerception.forWeather(CyberWeather.GHOST_PROTOCOL)))
        assertTrue(relayed(WeatherPerception.forWeather(CyberWeather.APEX_STORM)))
    }

    @Test
    fun alertRelayReplaysIdenticallyAcrossSessions() {
        // Seed determinism: two independent sessions replay the same alert,
        // relay and convergence tick for tick (no randomness anywhere in
        // the coordination path).
        fun runSession(): List<Triple<String, StealthGuardState, StealthPosition?>> {
            val world = openWorld(width = 13)
            val alerter = StealthGuard(
                id = "guard-alarm",
                position = StealthPosition(1.5f, 2.5f),
                facingAngleRadians = 0f,
                viewRange = 6f,
                fieldOfViewRadians = PI.toFloat() / 2f,
                detectionRatePerSecond = 80f
            )
            val near = StealthGuard(
                id = "guard-near",
                position = StealthPosition(5.5f, 2.5f),
                facingAngleRadians = 0f,
                fieldOfViewRadians = PI.toFloat() / 3f
            )
            val far = StealthGuard(
                id = "guard-far",
                position = StealthPosition(11.5f, 2.5f),
                facingAngleRadians = 0f,
                fieldOfViewRadians = PI.toFloat() / 3f
            )
            val simulation = StealthSimulation(world, listOf(alerter, near, far))
            val player = StealthPlayer(StealthPosition(2.5f, 2.5f))
            repeat(25) { simulation.update(0.1f, player) }
            return simulation.guards.map { Triple(it.id, it.state, it.investigationTarget) }
        }

        assertEquals(runSession(), runSession())
    }
}
