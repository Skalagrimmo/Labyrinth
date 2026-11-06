package com.example.ui

import com.example.core.model.CellType
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
        mazeGrid: Array<Array<CellType>>? = null
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
                levelSeed = 0L,
                isSneaking = sneaking
            )
        )
        val controller = StealthSessionController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            _uiState = flow,
            onLog = { message, type -> logs.add(message to type) },
            onGuardDetection = { x, y -> detections.add(x to y) }
        )
        return controller to flow
    }

    @Test
    fun seedsGuardFromVirusNodeAndStaysCalmBehindIt() {
        // Player west of the guard (x=1); facing derived from levelSeed=0 -> EAST.
        // The player is outside the vision cone: no detection over a long window.
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
        // Player east of the guard, inside the vision cone and line of sight:
        // suspicion (60/s, distance-scaled) crosses 100 within ~2.5s of ticks.
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
    fun hudTracksRisingSuspicionUpToAlert() {
        // Inside the vision cone the gauge climbs: after the first tick the HUD
        // already shows a non-zero trace while the ICE is still calm.
        val (controller, flow) = newController(px = 7)
        controller.tickOnce()
        assertEquals(StealthGuardState.PATROLLING, flow.value.iceThreatState)
        assertTrue(
            "HUD must surface suspicion after one tick, got " + flow.value.iceSuspicionPercent,
            flow.value.iceSuspicionPercent in 1..24
        )
        // Full detection pins the gauge at 100 and the threat state at ALERTED.
        var ticks = 0
        while (detections.isEmpty() && ticks < 60) {
            controller.tickOnce()
            ticks++
        }
        assertTrue(detections.isNotEmpty())
        assertEquals(100, flow.value.iceSuspicionPercent)
        assertEquals(StealthGuardState.ALERTED, flow.value.iceThreatState)
    }

    @Test
    fun hudStaysCalmWhileOutsideTheVisionCone() {
        val (controller, flow) = newController(px = 1)
        repeat(40) { controller.tickOnce() }
        assertEquals(0, flow.value.iceSuspicionPercent)
        assertEquals(StealthGuardState.PATROLLING, flow.value.iceThreatState)
    }

    @Test
    fun hudReflectsDecoyNoiseOnTheSuspicionGauge() {
        // A chaff detonation pins the gauge at the noise-driven suspicion
        // (40 x intensity, capped at 85) and flips the threat state.
        val (controller, flow) = newController(px = 3, sneaking = true)
        controller.emitNoiseAt(cellX = 7, cellY = 1, radiusCells = 6f, intensity = 1f)
        controller.tickOnce()
        assertEquals(40, flow.value.iceSuspicionPercent)
        assertEquals(StealthGuardState.INVESTIGATING, flow.value.iceThreatState)
    }

    private fun ticksUntilDetection(sneaking: Boolean, mazeGrid: Array<Array<CellType>>?): Int {
        val (controller, _) = newController(px = 7, sneaking = sneaking, mazeGrid = mazeGrid)
        var ticks = 0
        while (detections.isEmpty() && ticks < 120) {
            controller.tickOnce()
            ticks++
        }
        return if (detections.isEmpty()) -1 else ticks
    }
}