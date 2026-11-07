package com.example.ui

import com.example.core.model.CellType
import com.example.core.model.CyberWeather
import com.example.core.model.LogType
import com.example.data.GameSessionMode
import com.example.stealth.StealthGuardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun newController(
        px: Int,
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
                gridY = 1,
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
