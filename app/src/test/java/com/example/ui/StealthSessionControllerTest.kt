package com.example.ui

import com.example.core.model.CellType
import com.example.core.model.LogType
import com.example.data.GameSessionMode
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

    private fun newController(px: Int): Pair<StealthSessionController, MutableStateFlow<GameUiState>> {
        logs.clear()
        detections.clear()
        val flow = MutableStateFlow(
            GameUiState(
                screen = ActiveScreen.EXPLORATION,
                maze = maze(),
                gridX = px,
                gridY = 1,
                level = 1,
                levelSeed = 0L
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
}
