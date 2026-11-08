package com.example.ui

import com.example.core.model.CellType
import com.example.core.model.CyberWeather
import com.example.core.model.LogType
import com.example.data.GameSessionMode
import com.example.stealth.StealthGuardState
import com.example.stealth.StealthPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization tests for the exploration-phase RTwP wiring.
 * The stealth semantics themselves are covered by LabyrinthStealthSessionTest
 * and RealTimeGameSessionTest in :core:engine; these tests pin the controller:
 * guard seeding from VIRUS_NODE cells, detection routing into the legacy
 * combat flow, and the post-combat de-escalation.
 */
class StealthSessionControllerTest {

    private val logs = mutableListOf<Pair<String, LogType>>()
    private val detections = mutableListOf<Pair<Int, Int>>()

    private fun maze(): Array<Array<CellType>> {
        // 9x3 corridor with wall border; a single enemy node at (5, 1).
        val grid = Array(3) { y -> Array(9) { x -> CellType.PATH } }
        for (x in 0 until 9) {
            grid[0][x] = CellType.WALL
            grid[2][x] = CellType.WALL
        }
        for (y in 0 until 3) {
            grid[y][0] = CellType.WALL
            grid[y][8] = CellType.WALL
        }
        grid[1][5] = CellType.VIRUS_NODE
        return grid
    }

    private fun patrolRoom(width: Int, height: Int): Array<Array<CellType>> {
        // Open room with a wall border and no virus nodes: the walkable
        // area is (width - 2) * (height - 2) cells, large enough for mobile
        // ICE patrols to roam.
        val grid = Array(height) { y -> Array(width) { x -> CellType.PATH } }
        for (x in 0 until width) {
            grid[0][x] = CellType.WALL
            grid[height - 1][x] = CellType.WALL
        }
        for (y in 0 until height) {
            grid[y][0] = CellType.WALL
            grid[y][width - 1] = CellType.WALL
        }
        return grid
    }

    private fun patrolIdsIn(roomWidth: Int, roomHeight: Int): List<String> {
        val (controller, _) = newController(px = 1, py = 1, mazeGrid = patrolRoom(roomWidth, roomHeight))
        controller.tickOnce()
        return controller.peekStealthSession()!!.snapshot().guards.map { it.id }
    }

    private fun newController(
        px: Int,
        py: Int = 1,
        sneaking: Boolean = false,
        mazeGrid: Array<Array<CellType>>? = null,
        levelSeed: Long = 0L,
        weather: CyberWeather = CyberWeather.CLEAR
    ): Pair<StealthSessionController, MutableStateFlow<GameUiState>> {
        logs.clear()
        detections.clear()
        val flow = MutableStateFlow(
            GameUiState(
                screen = ActiveScreen.EXPLORATION,
                maze = mazeGrid ?: maze(),
                gridX = px,
                gridY = py,
                level = 1,
                levelSeed = levelSeed,
                isSneaking = sneaking,
                activeWeather = weather
            )
        )
        val controller = StealthSessionController(
            ticker = WorldClockTicker(CoroutineScope(Dispatchers.Unconfined)),
            _uiState = flow,
            onLog = { message, type -> logs.add(message to type) },
            onGuardDetection = { x, y -> detections.add(x to y) }
        )
        return controller to flow
    }

    @Test
    fun seedsGuardFromVirusNodeAndStaysCalmBehindIt() {
        // Player west of the guard (x=1); facing derived from levelSeed=0 ->
        // NORTH (Direction.values() order: NORTH, EAST, SOUTH, WEST). The
        // stationary guard slowly rotates its cone (~2 degrees per tick),
        // so the runner west of the node stays unseen for this short window.
        val (controller, _) = newController(px = 1)
        repeat(40) { controller.tickOnce() }
        assertTrue(detections.isEmpty())
        assertEquals(GameSessionMode.EXPLORATION, controller.peekSession()?.mode)
        val stealth = controller.peekStealthSession()
        assertNotNull(stealth)
        assertEquals(1, stealth!!.snapshot().guards.size)
        assertEquals("ice_5_1", stealth.snapshot().guards.first().id)
    }

    @Test
    fun routesDetectionIntoLegacyCombatFlowAtNodeCell() {
        // Player east of the guard. With levelSeed=0 the node spawns facing
        // NORTH and slowly rotates its cone toward the runner: distance-scaled
        // suspicion crosses 100 well within the 60-tick window.
        val (controller, _) = newController(px = 7)
        var ticks = 0
        while (detections.isEmpty() && ticks < 60) {
            controller.tickOnce()
            ticks++
        }
        assertTrue("expected detection within 60 ticks, got " + ticks, detections.isNotEmpty())
        assertEquals(Pair(5, 1), detections.first())
        assertEquals(GameSessionMode.COMBAT, controller.peekSession()?.mode)
        assertTrue(logs.any { it.first.contains("ICE THREAT SCAN") })
    }

    @Test
    fun deescalatesAfterConcludedCombatAndResumesExploration() {
        val (controller, _) = newController(px = 7)
        var ticks = 0
        while (detections.isEmpty() && ticks < 60) {
            controller.tickOnce()
            ticks++
        }
        assertTrue(detections.isNotEmpty())

        // The legacy combat flow concludes (gameState back to EXPLORATION,
        // no active enemy): the controller must de-escalate ALERTED guards
        // (KOTOR post-combat policy) and resume exploration mode.
        controller.tickOnce()
        assertEquals(GameSessionMode.EXPLORATION, controller.peekSession()?.mode)
        assertTrue(logs.any { it.first.contains("DE-ESCALATION") })
    }

    @Test
    fun sneakingDampensVisualDetection() {
        val plainTicks = ticksUntilDetection(sneaking = false, mazeGrid = null)
        val sneakedTicks = ticksUntilDetection(sneaking = true, mazeGrid = null)
        assertTrue("plain detection expected, got " + plainTicks, plainTicks in 1..119)
        assertTrue("sneaked detection expected, got " + sneakedTicks, sneakedTicks in 1..119)
        assertTrue(
            "sneaking must slow detection: sneaked=" + sneakedTicks + " plain=" + plainTicks,
            sneakedTicks > plainTicks + 10
        )
    }

    @Test
    fun footstepNoiseAttractsGuardsOnlyWhenNotSneaking() {
        // Quiet footsteps (radius 2.5 cells) cannot reach the guard four cells away.
        val (controller, _) = newController(px = 1)
        controller.emitFootstep()
        repeat(3) { controller.tickOnce() }
        assertEquals(
            StealthGuardState.PATROLLING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )

        // A loud ping reaches the guard: it starts investigating the noise.
        controller.emitNoise(radiusCells = 5f, intensity = 1f)
        controller.tickOnce()
        assertEquals(
            StealthGuardState.INVESTIGATING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )

        // While sneaking, footsteps are fully suppressed even at close range.
        val (sneaky, _) = newController(px = 3, sneaking = true)
        sneaky.emitFootstep()
        repeat(3) { sneaky.tickOnce() }
        assertEquals(
            StealthGuardState.PATROLLING,
            sneaky.peekStealthSession()!!.snapshot().guards.first().state
        )
    }

    @Test
    fun coverCellsDampenVisualDetection() {
        val plainTicks = ticksUntilDetection(sneaking = false, mazeGrid = null)
        val coverGrid = maze().also { it[1][7] = CellType.SCAN_CACHE }
        val coverTicks = ticksUntilDetection(sneaking = false, mazeGrid = coverGrid)
        assertTrue("plain detection expected, got " + plainTicks, plainTicks in 1..119)
        assertTrue("cover detection expected, got " + coverTicks, coverTicks in 1..119)
        assertTrue(
            "cover must slow detection: cover=" + coverTicks + " plain=" + plainTicks,
            coverTicks > plainTicks + 10
        )
    }

    @Test
    fun bumpNoiseIsHeardEvenWhileSneaking() {
        // Sneaking runner one cell west of the guard, fully behind its cone:
        // silence alone keeps the ICE patrol calm.
        val (controller, _) = newController(px = 4, sneaking = true)
        controller.emitFootstep()
        repeat(3) { controller.tickOnce() }
        assertEquals(
            StealthGuardState.PATROLLING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )

        // Crashing into the firewall is clumsy, not loud: the silent protocol
        // cannot suppress the scrape, and the guard hugging that wall hears it.
        controller.emitBump()
        controller.tickOnce()
        assertEquals(
            StealthGuardState.INVESTIGATING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun decoyThrowPullsGuardsTowardTheLandingCell() {
        // The decoy lands east of the guard: a tiny pulse there goes unheard,
        // a full-power chaff detonation pulls the ICE off its patrol.
        val (controller, _) = newController(px = 3, sneaking = true)
        controller.emitNoiseAt(cellX = 7, cellY = 1, radiusCells = 0.5f, intensity = 1f)
        repeat(2) { controller.tickOnce() }
        assertEquals(
            StealthGuardState.PATROLLING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )

        controller.emitNoiseAt(cellX = 7, cellY = 1, radiusCells = 6f, intensity = 1f)
        controller.tickOnce()
        assertEquals(
            StealthGuardState.INVESTIGATING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        // The phantom signature diverts the ICE: the runner stays undetected.
        repeat(3) { controller.tickOnce() }
        assertTrue(detections.isEmpty())
    }

    @Test
    fun decoyLandingCellIsClampedIntoTheMaze() {
        // A throw aimed far beyond the sector edge clamps onto the border cell;
        // since that border is firewall, the phantom signal slides back along
        // the throw line to the last walkable cell - where it still detonates.
        val (controller, _) = newController(px = 3, sneaking = true)
        controller.emitNoiseAt(cellX = 99, cellY = 99, radiusCells = 6f, intensity = 1f)
        controller.tickOnce()
        assertEquals(
            StealthGuardState.INVESTIGATING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun hudSuspicionTracksTheMostSuspiciousGuard() {
        // levelSeed=1 turns the node guard to face EAST, dead ahead of the
        // runner at x=7: suspicion climbs past the investigation threshold
        // within six ticks, and the HUD must mirror the guard exactly.
        val (controller, flow) = newController(px = 7, levelSeed = 1L)
        repeat(6) { controller.tickOnce() }
        assertEquals(
            controller.peekStealthSession()!!.snapshot().guards.first().suspicion,
            flow.value.iceSuspicion,
            0.0001f
        )
        assertEquals(StealthGuardState.INVESTIGATING, flow.value.iceGuardState)
        assertTrue(flow.value.iceSuspicion > 0f)

        // The runner slips back west, far outside the sight range of the
        // spent investigation: the sweep times out and the HUD goes dormant.
        flow.value = flow.value.copy(gridX = 1)
        repeat(120) { controller.tickOnce() }
        assertEquals(0f, flow.value.iceSuspicion, 0.0001f)
        assertEquals(StealthGuardState.PATROLLING, flow.value.iceGuardState)
    }

    @Test
    fun hudStaysPassiveWhileTheRunnerIsHidden() {
        // Outside the vision cone the world tick publishes a dormant HUD:
        // zero suspicion, PATROLLING state, tick after tick.
        val (controller, flow) = newController(px = 1)
        repeat(40) { controller.tickOnce() }
        assertEquals(0f, flow.value.iceSuspicion, 0.0001f)
        assertEquals(StealthGuardState.PATROLLING, flow.value.iceGuardState)
    }

    @Test
    fun hudReflectsDecoyInvestigation() {
        // A chaff detonation raises the published suspicion to the noise
        // floor (40 x intensity) and flips the HUD state to INVESTIGATING.
        val (controller, flow) = newController(px = 3, sneaking = true)
        controller.emitNoiseAt(cellX = 7, cellY = 1, radiusCells = 6f, intensity = 1f)
        controller.tickOnce()
        assertEquals(40f, flow.value.iceSuspicion, 0.0001f)
        assertEquals(StealthGuardState.INVESTIGATING, flow.value.iceGuardState)
    }

    @Test
    fun ghostProtocolWeatherSlowsVisualDetection() {
        // GHOST_PROTOCOL masks the runner (0.65 x detection): the rotating
        // cone still finds them, but suspicion climbs far slower.
        val clearTicks = ticksUntilDetection(sneaking = false, mazeGrid = null, weather = CyberWeather.CLEAR)
        val ghostTicks = ticksUntilDetection(sneaking = false, mazeGrid = null, weather = CyberWeather.GHOST_PROTOCOL)
        assertTrue("clear detection expected, got " + clearTicks, clearTicks in 1..119)
        assertTrue("ghost detection expected, got " + ghostTicks, ghostTicks in 1..119)
        assertTrue(
            "ghost protocol must slow detection: ghost=" + ghostTicks + " clear=" + clearTicks,
            ghostTicks > clearTicks + 10
        )
    }

    @Test
    fun apexStormWeatherAcceleratesVisualDetection() {
        // APEX_STORM overdrives the ICE (1.25 x detection): the alarm
        // sounds measurably earlier than in clear bandwidth.
        val clearTicks = ticksUntilDetection(sneaking = false, mazeGrid = null, weather = CyberWeather.CLEAR)
        val apexTicks = ticksUntilDetection(sneaking = false, mazeGrid = null, weather = CyberWeather.APEX_STORM)
        assertTrue("clear detection expected, got " + clearTicks, clearTicks in 1..119)
        assertTrue("apex detection expected, got " + apexTicks, apexTicks in 1..119)
        // The stationary guard's cone rotates in discrete steps, so the
        // detection tick count is quantized: with levelSeed=0 the clear-weather
        // alarm fires at tick 50 and the apex storm at tick 46 (deterministic).
        assertTrue(
            "apex storm must accelerate detection: apex=" + apexTicks + " clear=" + clearTicks,
            apexTicks < clearTicks - 3
        )
    }

    @Test
    fun echoWeatherStirsIceWithoutExposingTheRunner() {
        // ECHOES pulses a phantom telemetry fragment every 40 ticks: the ICE
        // leaves its post to investigate the remnant while the runner stays
        // undetected in the far west of the corridor.
        val (controller, _) = newController(px = 1, weather = CyberWeather.ECHOES)
        var ticks = 0
        var state = StealthGuardState.PATROLLING
        val maxTicks = 3 * 40
        while (ticks < maxTicks) {
            controller.tickOnce()
            ticks++
            state = controller.peekStealthSession()!!.snapshot().guards.first().state
            if (state != StealthGuardState.PATROLLING) break
        }
        assertTrue("ICE should have chased a phantom echo, stayed " + state, state != StealthGuardState.PATROLLING)
        assertTrue(detections.isEmpty())
    }

    @Test
    fun smallFloorsStayPatrolFree() {
        // The 9x3 corridor (7 walkable cells) is far below the patrol size
        // threshold: the floor hosts only the stationary node guard and
        // no patrol advisory is logged.
        val (controller, _) = newController(px = 1)
        controller.tickOnce()
        val guards = controller.peekStealthSession()!!.snapshot().guards
        assertEquals(1, guards.size)
        assertEquals("ice_5_1", guards.first().id)
        assertTrue(guards.none { it.id.contains("patrol") })
        assertTrue(logs.none { it.first.contains("ICE PATROL") })
    }

    @Test
    fun patrolCountScalesWithTheWalkableArea() {
        // 91 walkable cells -> 1 patrol, 207 -> 2, 341 -> 3 (constants:
        // PATROL_MIN_WALKABLE_CELLS = 40, PATROL_AREA_PER_UNIT = 100,
        // MAX_PATROLS_PER_FLOOR = 3).
        assertEquals(listOf("ice_patrol_1_0"), patrolIdsIn(15, 9))
        assertEquals(
            listOf("ice_patrol_1_0", "ice_patrol_1_1"),
            patrolIdsIn(25, 11)
        )
        assertEquals(
            listOf("ice_patrol_1_0", "ice_patrol_1_1", "ice_patrol_1_2"),
            patrolIdsIn(33, 13)
        )
    }

    @Test
    fun patrolRoutesAreDeterministicPerLevelSeed() {
        // Same floor + same seed -> the identical patrol set replaying
        // the identical route: two independent sessions agree tick for
        // tick (seed determinism).
        val (alpha, _) = newController(px = 1, py = 1, mazeGrid = patrolRoom(15, 9), levelSeed = 7L)
        val (beta, _) = newController(px = 1, py = 1, mazeGrid = patrolRoom(15, 9), levelSeed = 7L)
        repeat(20) {
            alpha.tickOnce()
            beta.tickOnce()
        }
        val a = alpha.peekStealthSession()!!.snapshot().guards
        val b = beta.peekStealthSession()!!.snapshot().guards
        assertEquals(b.map { it.id }, a.map { it.id })
        assertTrue(a.any { it.id.startsWith("ice_patrol_") })
        for (i in a.indices) {
            assertEquals(a[i].position, b[i].position)
        }
    }

    @Test
    fun patrolGuardsRoamTheirRoute() {
        // The roaming unit leaves its spawn waypoint and keeps sweeping
        // the floor; the patrol advisory is logged exactly once per
        // floor. The runner waits in the corner farthest from the spawn
        // so the patrol has room to roam (a single tick cannot raise an
        // alert: suspicion grows at most 6 points per tick).
        val (controller, flow) = newController(px = 1, py = 1, mazeGrid = patrolRoom(15, 9))
        controller.tickOnce()
        val spawn = controller.peekStealthSession()!!.snapshot().guards.first().position
        assertEquals(1, logs.count { it.first.contains("ICE PATROL SIGNATURES") })
        val farCorner = listOf(1 to 1, 13 to 1, 1 to 7, 13 to 7).maxBy { corner ->
            spawn.distanceTo(StealthPosition(corner.first + 0.5f, corner.second + 0.5f, spawn.levelId))
        }
        flow.value = flow.value.copy(gridX = farCorner.first, gridY = farCorner.second)
        var maxDisplacement = 0f
        repeat(90) {
            controller.tickOnce()
            val position = controller.peekStealthSession()!!.snapshot().guards.first().position
            val displacement = spawn.distanceTo(position)
            if (displacement > maxDisplacement) maxDisplacement = displacement
        }
        assertTrue(
            "patrol should clearly leave its spawn waypoint, max displacement: " + maxDisplacement,
            maxDisplacement > 0.9f
        )
        assertEquals(1, logs.count { it.first.contains("ICE PATROL SIGNATURES") })
    }

    @Test
    fun patrolDetectionRoutesCombatToTheRunnerCell() {
        // A loud chaff-style ping at the runner's cell lures the roaming
        // patrol straight in; walking into the runner raises the alert,
        // and the detection routes into the legacy combat flow at the
        // runner's cell - this floor has no virus nodes, only the
        // mobile patrol, so the fallback position routing is exercised.
        val (controller, _) = newController(px = 7, py = 4, mazeGrid = patrolRoom(15, 9))
        controller.tickOnce()
        controller.emitNoiseAt(cellX = 7, cellY = 4, radiusCells = 25f, intensity = 1f)
        var ticks = 0
        while (detections.isEmpty() && ticks < 300) {
            controller.tickOnce()
            ticks++
        }
        assertTrue("expected the lured patrol to reach the runner, got " + ticks, detections.isNotEmpty())
        assertEquals(Pair(7, 4), detections.first())
        assertEquals(GameSessionMode.COMBAT, controller.peekSession()?.mode)
    }

    private fun relayCorridor(): Array<Array<CellType>> {
        // 13x3 corridor split by a firewall at x=6: an east-chamber node
        // guard with a clear line on the runner, and a west-chamber node
        // guard that cannot see them (7 cells, wall in between) but sits
        // inside the 8-cell relay radius of the detection spot.
        val grid = Array(3) { y -> Array(13) { x -> CellType.PATH } }
        for (x in 0 until 13) {
            grid[0][x] = CellType.WALL
            grid[2][x] = CellType.WALL
        }
        for (y in 0 until 3) {
            grid[y][0] = CellType.WALL
            grid[y][12] = CellType.WALL
        }
        grid[1][6] = CellType.WALL
        grid[1][10] = CellType.VIRUS_NODE
        grid[1][4] = CellType.VIRUS_NODE
        return grid
    }

    @Test
    fun alertedGuardRelaysTheAlarmThroughTheIceNet() {
        // levelSeed = 2 turns the east node guard (10, 1) to face EAST -
        // dead on the runner at x=11 - while the west node guard (4, 1)
        // faces WEST, blind. When the east unit raises the alarm, the west
        // unit is relayed the runner's position over the ICE net and
        // converges on the detection spot.
        val (controller, _) = newController(px = 11, levelSeed = 2L, mazeGrid = relayCorridor())
        var ticks = 0
        while (detections.isEmpty() && ticks < 120) {
            controller.tickOnce()
            ticks++
        }
        assertTrue("expected detection within 120 ticks, got " + ticks, detections.isNotEmpty())
        assertEquals(Pair(10, 1), detections.first())
        assertEquals(GameSessionMode.COMBAT, controller.peekSession()?.mode)
        assertTrue(
            logs.any { it.first.contains("ICE NET TRAFFIC") && it.first.contains("ice_4_1") }
        )
        val west = controller.peekStealthSession()!!.snapshot().guards
            .first { it.id == "ice_4_1" }
        assertEquals(StealthGuardState.INVESTIGATING, west.state)
        assertEquals(40f, west.suspicion, 0.0001f)
        assertEquals(StealthPosition(11.5f, 1.5f, 1), west.lastKnownPlayerPosition)
    }

    @Test
    fun ghostProtocolMutesTheIceNetRelay() {
        // Same split corridor under GHOST_PROTOCOL: the relay radius
        // shrinks (8 x 0.85 = 6.8 cells) below the west guard's 7-cell
        // distance, so the alarm never reaches it - the unit keeps
        // patrolling while the detecting guard fights alone.
        val (controller, _) = newController(
            px = 11,
            levelSeed = 2L,
            mazeGrid = relayCorridor(),
            weather = CyberWeather.GHOST_PROTOCOL
        )
        var ticks = 0
        while (detections.isEmpty() && ticks < 120) {
            controller.tickOnce()
            ticks++
        }
        assertTrue("expected detection within 120 ticks, got " + ticks, detections.isNotEmpty())
        assertEquals(Pair(10, 1), detections.first())
        assertTrue(logs.none { it.first.contains("ICE NET TRAFFIC") })
        val west = controller.peekStealthSession()!!.snapshot().guards
            .first { it.id == "ice_4_1" }
        assertEquals(StealthGuardState.PATROLLING, west.state)
        assertEquals(0f, west.suspicion, 0.0001f)
    }

    @Test
    fun spikeDisablesTheNearestIceUnitAndFreezesTheHud() {
        // d8 slice 4: levelSeed = 1 turns the node guard to face EAST, dead
        // ahead of the runner at x=7, so suspicion climbs. The node spike
        // drops the unit into a controlled reboot: the HUD freezes to
        // 0%/PATROLLING (a disabled unit does not count) and the 8s window
        // (60 ticks = 6s here) outlasts the runner's exposure.
        val (controller, flow) = newController(px = 7, levelSeed = 1L)
        repeat(6) { controller.tickOnce() }
        assertTrue(flow.value.iceSuspicion > 0f)

        val disabled = controller.disableNearestIceUnit(
            StealthSessionController.JAM_RANGE_CELLS,
            StealthSessionController.JAM_DURATION_SECONDS
        )
        assertEquals("ice_5_1", disabled)
        assertEquals(
            StealthGuardState.DISABLED,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        assertEquals(0f, flow.value.iceSuspicion, 0.0001f)
        assertEquals(StealthGuardState.PATROLLING, flow.value.iceGuardState)

        repeat(60) { controller.tickOnce() }
        assertEquals(
            StealthGuardState.DISABLED,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun spikeOutOfRangeFindsNoTarget() {
        // The runner four cells west of the node is beyond a 2-cell spike:
        // no target, no spent spike, the unit keeps patrolling. One tick
        // first - the spike needs the live session that syncSession creates.
        val (controller, _) = newController(px = 1)
        controller.tickOnce()
        assertNull(
            controller.disableNearestIceUnit(maxRangeCells = 2f, durationSeconds = 8f)
        )
        assertEquals(
            StealthGuardState.PATROLLING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
    }

    @Test
    fun spikedUnitRebootsAndResumesPerception() {
        // 80 ticks = the 8s spike: the unit reboots (ICE REBOOT log), resumes
        // its patrol and - still facing the runner dead ahead - rebuilds
        // suspicion from the clean slate and routes the detection into the
        // legacy combat flow.
        val (controller, _) = newController(px = 7, levelSeed = 1L)
        controller.tickOnce()
        assertNotNull(
            controller.disableNearestIceUnit(
                StealthSessionController.JAM_RANGE_CELLS,
                StealthSessionController.JAM_DURATION_SECONDS
            )
        )

        // 80 ticks at the 100ms cadence = the 8s spike. Iterated float
        // subtraction can leave a ~1e-6 residue on the exact boundary, so
        // allow a tick of slack before demanding the clean PATROLLING
        // reboot (and keep a hard bound so a broken timer still fails).
        var ticksAfterSpike = 0
        while (
            controller.peekStealthSession()!!.snapshot().guards.first().state ==
                StealthGuardState.DISABLED && ticksAfterSpike < 90
        ) {
            controller.tickOnce()
            ticksAfterSpike++
        }
        assertEquals(
            StealthGuardState.PATROLLING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        assertTrue(
            "expected the reboot at ~80 ticks, got " + ticksAfterSpike,
            ticksAfterSpike in 78..85
        )
        assertTrue(
            logs.any { it.first.contains("ICE REBOOT") && it.first.contains("ice_5_1") }
        )

        var ticks = 0
        while (detections.isEmpty() && ticks < 120) {
            controller.tickOnce()
            ticks++
        }
        assertTrue(
            "expected the rebooted unit to re-acquire the runner, ticks=" + ticks,
            detections.isNotEmpty()
        )
    }

    @Test
    fun failedBreachTripsTheNodeKlaxon() {
        // d8 slice 5: a buffer overflow at the hacked node detonates a
        // full-power klaxon AT THE NODE: every ICE unit inside the 5.5-cell
        // radius converges on the terminal, not on the runner's position.
        val (controller, flow) = newController(px = 1)
        controller.tickOnce()
        val landing = controller.emitBreachOutcome(3, 1, success = false)
        assertEquals(Pair(3, 1), landing)
        assertTrue(
            logs.any { it.first.contains("ACOUSTIC SPIKE") && it.first.contains("(3, 1)") }
        )
        assertTrue(
            logs.any { it.first.contains("ACOUSTIC SPIKE") && it.second == LogType.ALERT }
        )
        controller.tickOnce()
        val guard = controller.peekStealthSession()!!.snapshot().guards.first()
        assertEquals(StealthGuardState.INVESTIGATING, guard.state)
        assertEquals(StealthPosition(3.5f, 1.5f, 1), guard.lastKnownPlayerPosition)
        assertEquals(40f, guard.suspicion, 0.0001f)
        assertEquals(40f, flow.value.iceSuspicion, 0.0001f)
        assertEquals(StealthGuardState.INVESTIGATING, flow.value.iceGuardState)
        assertTrue(detections.isEmpty())
    }

    @Test
    fun quietBreachLeavesOnlyAFaintBackwash() {
        // d8 slice 5: a clean breach is a faint backwash - a 2-cell radius
        // at the 0.3 noise floor. A node three cells away from the ICE goes
        // unnoticed; a node one cell away only earns a glance (the floor
        // is 40 x 0.3 = 12 suspicion, a stir, not a sweep).
        val (controller, _) = newController(px = 1)
        controller.tickOnce()
        val landing = controller.emitBreachOutcome(2, 1, success = true)
        assertEquals(Pair(2, 1), landing)
        assertTrue(
            logs.any { it.first.contains("QUIET BREACH") && it.first.contains("(2, 1)") }
        )
        assertTrue(
            logs.any { it.first.contains("QUIET BREACH") && it.second == LogType.INFO }
        )
        // The guard at (5, 1) sits 3.0 cells from the backwash - outside
        // the 2-cell radius, the sweep is not even stirred.
        controller.tickOnce()
        assertEquals(
            StealthGuardState.PATROLLING,
            controller.peekStealthSession()!!.snapshot().guards.first().state
        )
        // A backwash one cell from the ICE hugging the node: a glance over.
        controller.emitBreachOutcome(4, 1, success = true)
        controller.tickOnce()
        val guard = controller.peekStealthSession()!!.snapshot().guards.first()
        assertEquals(StealthGuardState.INVESTIGATING, guard.state)
        assertEquals(12f, guard.suspicion, 0.0001f)
        assertEquals(StealthPosition(4.5f, 1.5f, 1), guard.lastKnownPlayerPosition)
        assertTrue(detections.isEmpty())
    }

    @Test
    fun klaxonConvergesOnTheNodeWhileTheRunnerSlipsAway() {
        // d8 slice 5: the getaway window. A failed breach far from the
        // runner: the klaxon pulls the ICE onto the hacked node (the
        // guard walks the corridor, sweeps the terminal in SEARCHING,
        // then falls back to its patrol) while the runner ten cells west
        // - far beyond the spent sweep's 5.5-cell view range - slips
        // away undetected.
        val grid = Array(3) { y -> Array(15) { x -> CellType.PATH } }
        for (x in 0 until 15) {
            grid[0][x] = CellType.WALL
            grid[2][x] = CellType.WALL
        }
        for (y in 0 until 3) {
            grid[y][0] = CellType.WALL
            grid[y][14] = CellType.WALL
        }
        grid[1][4] = CellType.VIRUS_NODE
        val (controller, _) = newController(px = 12, mazeGrid = grid)
        controller.tickOnce()
        val landing = controller.emitBreachOutcome(2, 1, success = false)
        assertEquals(Pair(2, 1), landing)
        assertTrue(logs.any { it.first.contains("ACOUSTIC SPIKE") })
        var sawSearching = false
        repeat(120) {
            controller.tickOnce()
            if (controller.peekStealthSession()!!.snapshot().guards.first().state ==
                StealthGuardState.SEARCHING
            ) {
                sawSearching = true
            }
        }
        assertTrue("expected the klaxon to pull the ICE onto the node", sawSearching)
        assertTrue(detections.isEmpty())
    }

    private fun ticksUntilDetection(sneaking: Boolean,
        mazeGrid: Array<Array<CellType>>?,
        weather: CyberWeather = CyberWeather.CLEAR
    ): Int {
        val (controller, _) = newController(
            px = 7,
            sneaking = sneaking,
            mazeGrid = mazeGrid,
            weather = weather
        )
        var ticks = 0
        while (detections.isEmpty() && ticks < 120) {
            controller.tickOnce()
            ticks++
        }
        return if (detections.isEmpty()) -1 else ticks
    }
}
