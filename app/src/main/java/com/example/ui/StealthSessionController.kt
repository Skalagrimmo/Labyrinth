package com.example.ui

import com.example.core.model.CellType
import com.example.core.model.Direction
import com.example.core.model.LogType
import com.example.data.GameSessionMode
import com.example.data.GameState
import com.example.data.RealTimeGameSession
import com.example.stealth.LabyrinthStealthSession
import com.example.stealth.StealthEventType
import com.example.stealth.StealthGuard
import com.example.stealth.StealthPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time stealth world clock for the exploration phase - first slice of the
 * RTwP integration (04.11.2026 decision: KOTOR-style combat shares one world
 * clock with the real-time stealth simulation).
 *
 * The pacing and perception semantics live exactly once, in :core:engine
 * (LabyrinthStealthSession + RealTimeGameSession). This class is only the
 * app wiring, mirroring CombatClockController:
 *  - every VIRUS_NODE cell of the current maze becomes a stationary guard
 *    (facing derived deterministically from levelSeed, so the same seed always
 *    produces the same sight lines);
 *  - maze/level changes are mirrored into the session automatically (guard
 *    runtime state survives array replacement, a floor switch reseeds guards);
 *  - while a guard has visual on the player, suspicion builds in the shared
 *    simulation (patrol -> investigate -> alerted);
 *  - a PLAYER_DETECTED event routes into the existing combat flow
 *    (GameViewModel.triggerCombat at the detecting guard's node cell), so
 *    combat resolution stays on CombatManager + CombatClockController;
 *  - after a concluded combat the KOTOR post-combat policy applies
 *    (ALERTED guards de-escalate to INVESTIGATING/SEARCHING with the player's
 *    last known position) and exploration resumes on the same stealth session;
 *  - the breach-protocol minigame and non-game screens freeze the world clock
 *    (KOTOR rule).
 *
 * The ticker is NOT started on construction (same rule as
 * CombatClockController): GameViewModel starts it when a run actually begins
 * (createCharacter / loadOrCreateLevel / loadGame / resumeGame / startNewRun)
 * and it stops itself on menu/character-creation/game-over screens, so no
 * coroutine spins during menus or UI tests.
 */
class StealthSessionController(
    private val scope: CoroutineScope,
    private val _uiState: MutableStateFlow<GameUiState>,
    private val onLog: (String, LogType) -> Unit,
    private val onGuardDetection: (enemyX: Int, enemyY: Int) -> Unit
) {
    private var stealthSession: LabyrinthStealthSession? = null
    private var session: RealTimeGameSession? = null
    private var guards: MutableList<StealthGuard> = mutableListOf()
    private var guardCells: MutableMap<String, Pair<Int, Int>> = mutableMapOf()
    private var guardSignature: String = ""
    private var lastMaze: Array<Array<CellType>>? = null
    private var lastLevelId: Int = -1
    private var tickerJob: Job? = null
    private var combatSeen: Boolean = false

    fun start() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                tickOnce()
            }
        }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    internal fun peekSession(): RealTimeGameSession? = session
    internal fun peekStealthSession(): LabyrinthStealthSession? = stealthSession

    internal fun tickOnce() {
        val state = _uiState.value
        when (state.screen) {
            ActiveScreen.START_MENU,
            ActiveScreen.CHARACTER_CREATION,
            ActiveScreen.GAME_OVER,
            ActiveScreen.SVDAG_WORLD_BUILDER -> {
                stop()
                return
            }
            ActiveScreen.EXPLORATION -> {}
            else -> return // breach minigame freezes the world clock; other screens pause it
        }
        if (state.maze.isEmpty()) return

        val inRealCombat = state.activeEnemy != null || state.gameState != GameState.EXPLORATION
        if (inRealCombat) {
            // Combat (stealth-triggered or bump-started) pauses the world clock;
            // remember it so the post-combat de-escalation applies either way.
            combatSeen = true
            return
        }

        syncSession(state.maze, state.level, state.levelSeed)
        val currentSession = session ?: return
        val stealth = stealthSession ?: return

        if (combatSeen) {
            // The legacy combat flow concluded: apply the KOTOR post-combat
            // policy and resume exploration on a fresh session wrapper.
            concludeCombat(stealth)
            return
        }
        if (currentSession.mode != GameSessionMode.EXPLORATION) return

        val result = currentSession.tick(
            deltaMillis = TICK_MILLIS,
            gridX = state.gridX,
            gridY = state.gridY
        )
        if (result.enteredCombat) {
            combatSeen = true
            val detection = result.detection
            val nodeCell = detection?.let { guardCells[it.guardId] }
            onLog("ICE THREAT SCAN: hostile process has visual on your position!", LogType.ALERT)
            when {
                nodeCell != null -> onGuardDetection(nodeCell.first, nodeCell.second)
                detection != null -> onGuardDetection(
                    detection.position.x.toInt(),
                    detection.position.y.toInt()
                )
            }
        }
    }

    /**
     * Mirrors the current maze and its VIRUS_NODE cells into the shared session.
     * Surviving guards keep their runtime state across maze array replacement;
     * a level switch reseeds the guard set from scratch.
     */
    private fun syncSession(maze: Array<Array<CellType>>, levelId: Int, levelSeed: Long) {
        val mazeChanged = maze !== lastMaze
        val levelChanged = levelId != lastLevelId
        if (!mazeChanged && !levelChanged && session != null) return
        lastMaze = maze
        lastLevelId = levelId

        val nodes = mutableListOf<Pair<Int, Int>>()
        for (y in maze.indices) {
            for (x in maze[y].indices) {
                if (maze[y][x] == CellType.VIRUS_NODE) nodes.add(x to y)
            }
        }
        val signature = buildString {
            append(levelId).append(':')
            nodes.sortedWith(compareBy({ it.second }, { it.first })).forEach {
                append(it.first).append(',').append(it.second).append(';')
            }
        }

        if (levelChanged) guards.clear()
        val existingById = guards.associateBy { it.id }
        guardCells = mutableMapOf()
        val nextGuards = mutableListOf<StealthGuard>()
        for ((x, y) in nodes) {
            val id = guardId(x, y)
            guardCells[id] = x to y
            nextGuards.add(existingById[id] ?: newGuard(id, x, y, levelId, levelSeed))
        }
        guards = nextGuards

        if (signature != guardSignature || stealthSession == null) {
            guardSignature = signature
            stealthSession = LabyrinthStealthSession(
                initialMaze = maze,
                guards = guards.toList(),
                levelId = levelId
            )
            session = RealTimeGameSession(stealthSession!!)
        } else if (mazeChanged) {
            stealthSession!!.replaceMaze(maze)
        }
    }

    private fun concludeCombat(stealth: LabyrinthStealthSession) {
        val frame = stealth.deescalateAfterCombat()
        frame.events
            .filter { it.type == StealthEventType.GUARD_STATE_CHANGED }
            .forEach { event ->
                onLog(
                    "ICE SWEEP DE-ESCALATION: unit " + event.guardId + " " +
                        event.previousState + " -> " + event.currentState,
                    LogType.INFO
                )
            }
        session = RealTimeGameSession(stealth)
        combatSeen = false
    }

    private fun newGuard(id: String, x: Int, y: Int, levelId: Int, levelSeed: Long): StealthGuard =
        StealthGuard(
            id = id,
            position = StealthPosition(x + 0.5f, y + 0.5f, levelId),
            facingAngleRadians = LabyrinthStealthSession.angleForDirection(facingFor(x, y, levelSeed))
        )

    private fun guardId(x: Int, y: Int): String = "ice_" + x + "_" + y

    private fun facingFor(x: Int, y: Int, levelSeed: Long): Direction {
        val values = Direction.values()
        val index = ((x * 31 + y * 17 + levelSeed.toInt()) % values.size + values.size) % values.size
        return values[index]
    }

    companion object {
        const val TICK_MILLIS: Long = 100L
    }
}
