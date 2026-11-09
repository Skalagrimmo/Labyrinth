package com.example.ui

import com.example.core.model.CellType
import com.example.core.model.Direction
import com.example.core.model.LogType
import com.example.data.GameItemRegistry
import com.example.data.GameSessionMode
import com.example.data.GameState
import com.example.data.RealTimeGameSession
import com.example.stealth.GridCell
import com.example.stealth.LabyrinthStealthSession
import com.example.stealth.NoisePulse
import com.example.stealth.StealthEventType
import com.example.stealth.StealthGuard
import com.example.stealth.StealthGuardState
import com.example.stealth.StealthPosition
import com.example.stealth.StealthSessionFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Real-time stealth world clock for the exploration phase - first slice of the
 * RTwP integration (04.11.2026 decision: KOTOR-style combat shares one world
 * clock with the real-time stealth simulation).
 *
 * The pacing and perception semantics live exactly once, in :core:engine
 * (LabyrinthStealthSession + RealTimeGameSession). This class is only the
 * app wiring, riding the shared WorldClockTicker together with
 * CombatClockController (d6 slice 5: one world-clock loop for both RTwP
 * consumers):
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
 *  - the highest ICE suspicion of the current floor is published into
 *  GameUiState (iceSuspicion + iceGuardState) for the exploration HUD;
 *  - the current CyberWeather scales ICE perception (WeatherPerception in
 *  :core:engine) and ECHOES spawns phantom echo pulses that lure ICE off
 *  patrol (d8 slice 1: weather -> ICE perception binding);
 *  - d8 slice 2: floors with enough walkable area host 1-3 mobile ICE
 *  patrols - roaming sweep units whose waypoint loops are hashed from
 *  the level seed (seed determinism); small corridors stay patrol-free
 *  and the stationary node guards keep their exact behaviour;
 *  - d8 slice 3: ICE coordination - a unit that raises the alarm relays the
 *    runner's last known position to every other unit within the relay radius
 *    (weather-scaled); each relay is surfaced as an ICE NET TRAFFIC log line;
 *  - d8 slice 4: ICE neutralization - the runner's node spike forces the
 *    nearest active unit into a temporary controlled reboot (DISABLED);
 *    the unit coming back online is surfaced as an ICE REBOOT log line;
 *  - d8 slice 5: breach acoustics - the breach-protocol outcome leaves an
 *    acoustic trace at the hacked node, never at the runner: a clean breach
 *    is a faint backwash, a failed breach trips the node's klaxon and pulls
 *    nearby ICE onto the terminal while the runner slips away (see
 *    [emitBreachOutcome]);
 *  - d8 slice 7: duct crawl & cover navigation - service vents
 *    (VENT_TUNNEL / ALTERNATIVE_VENT) are the runner's stealth network:
 *    inside a duct the runner is thermally invisible to ICE optics and
 *    the crawl is silent, ICE units are too bulky to follow into the
 *    conduits (a chase stalls into a sweep at the duct mouth) and a
 *    phantom signal aimed into a duct rolls back to the mouth; leaving
 *    a hardened cover cell keeps the perception dampening for a short
 *    grace window so cover-to-cover slips stay quiet (see
 *    [isRunnerInDuct] and [COVER_GRACE_TICKS]);
 *  - d8 slice 8: field acoustics - actions from the UI depth leave an EM
 *    footprint at the runner's cell: compiling a utility from the inventory
 *    overlay (or the terminal `use` command) blooms a tiered noise pulse
 *    derived from the item's own effect profile, and rummaging a cache
 *    rattles it open (see [emitItemUsage]);
 *  - the breach-protocol minigame and non-game screens freeze the world clock
 *    (KOTOR rule).
 *
 * The shared world loop is NOT started on construction (same rule as
 * CombatClockController): GameViewModel starts it when a run actually begins
 * (createCharacter / loadOrCreateLevel / loadGame / resumeGame / startNewRun)
 * and this consumer stops it on menu/character-creation/game-over screens, so
 * no coroutine spins during menus or UI tests.
 */
class StealthSessionController(
    private val ticker: WorldClockTicker,
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
    private var combatSeen: Boolean = false
    /** Remembers which floor+count was last announced, so the patrol advisory log fires once per level. */
    private var lastPatrolLogSignature: String = ""

    /** d8 slice 7: cover-slip grace - world ticks of perception dampening left after leaving a cover cell. */
    private var coverGraceTicksRemaining: Int = 0

    /** d8 slice 7: last announced duct state, so the DUCT CRAWL/EXIT logs fire once per transit. */
    private var lastDuctAnnounced: Boolean = false

    /** One-shot noise stimuli queued from player input; consumed by the next world tick. */
    private val pendingNoise = ArrayDeque<NoisePulse>()

    init {
        // Shared world-clock consumer (d6 slice 5): no private coroutine
        // loop - the GameViewModel-owned WorldClockTicker dispatches every
        // world tick to this consumer.
        ticker.subscribe(this::tickOnce)
    }

    /** Starts the shared world-clock loop (idempotent). */
    fun start() {
        ticker.start()
    }

    /** Stops the shared world-clock loop: menus freeze the whole world. */
    fun stop() {
        ticker.stop()
    }

    internal fun peekSession(): RealTimeGameSession? = session
    internal fun peekStealthSession(): LabyrinthStealthSession? = stealthSession

    internal fun tickOnce() {
        val state = _uiState.value
        val noisePulses = drainNoise()
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

        // d8 slice 7: duct crawl - a runner inside a service vent is
        // thermally invisible to ICE optics; the transit is announced once.
        val inDuct = isRunnerInDuct(state)
        if (inDuct != lastDuctAnnounced) {
            lastDuctAnnounced = inDuct
            if (inDuct) {
                onLog(
                    "DUCT CRAWL: thermal-dampened concealment active - ICE optics lose you.",
                    LogType.INFO
                )
            } else {
                onLog(
                    "DUCT EXIT: concealment dropped - the open grid sees you again.",
                    LogType.INFO
                )
            }
        }
        // d8 slice 7: cover-slip grace - stepping off a hardened cover cell
        // keeps the perception dampening for a short window, so slipping
        // between cover stays quiet. Deterministic tick countdown, no RNG.
        if (isInCover(state)) {
            coverGraceTicksRemaining = COVER_GRACE_TICKS
        } else if (coverGraceTicksRemaining > 0) {
            coverGraceTicksRemaining--
        }
        val result = currentSession.tick(
            deltaMillis = WorldClockTicker.TICK_MILLIS,
            gridX = state.gridX,
            gridY = state.gridY,
            isSneaking = state.isSneaking,
            isInvisible = inDuct,
            isInCover = coverGraceTicksRemaining > 0,
            noisePulses = noisePulses,
            weather = state.activeWeather
        )
        publishHud(result.stealthFrame)
        // d8 slice 3: surface the alert relay - nearby units acknowledging the
        // alarm and converging on the runner's last known position.
        result.stealthFrame?.events
            ?.filter { it.type == StealthEventType.ICE_ALERT_RELAYED }
            ?.forEach { event ->
                onLog(
                    "ICE NET TRAFFIC: unit " + event.guardId +
                        " acknowledges the alarm - hostile sweep converging.",
                    LogType.ALERT
                )
            }
        // d8 slice 4: a spiked unit coming back online announces its reboot.
        result.stealthFrame?.events
            ?.filter {
                it.type == StealthEventType.GUARD_STATE_CHANGED &&
                    it.previousState == StealthGuardState.DISABLED
            }
            ?.forEach { event ->
                onLog(
                    "ICE REBOOT: unit " + event.guardId +
                        " back online - perception restored.",
                    LogType.ALERT
                )
            }
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
     * Footstep stimulus from movement input: fully suppressed while
     * sneaking, and muffled inside a service vent - the duct crawl is
     * silent regardless of stance (d8 slice 7).
     */
    fun emitFootstep() {
        val state = _uiState.value
        if (state.isSneaking) return
        if (isRunnerInDuct(state)) return
        emitNoise(radiusCells = FOOTSTEP_RADIUS_CELLS, intensity = FOOTSTEP_INTENSITY)
    }

    /**
     * Accidental wall bump (a blocked move): a short scrape against the
     * firewall that the silent protocol cannot suppress - it is clumsiness,
     * not a footstep. Tiny radius: only ICE units hugging the crashed wall
     * will pick it up.
     */
    fun emitBump() {
        emitNoise(radiusCells = BUMP_RADIUS_CELLS, intensity = BUMP_INTENSITY)
    }

    /**
     * Emits a one-shot noise stimulus (radar pings, interactions). Reaches every
     * guard within radiusCells on the current level at the next world tick.
     */
    fun emitNoise(radiusCells: Float, intensity: Float) {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) return
        pendingNoise.addLast(
            NoisePulse(
                position = StealthPosition(state.gridX + 0.5f, state.gridY + 0.5f, state.level),
                radius = radiusCells,
                intensity = intensity
            )
        )
        while (pendingNoise.size > MAX_PENDING_NOISE) pendingNoise.removeFirst()
    }

    /**
     * Emits a one-shot noise pulse at an arbitrary cell of the current level
     * (chaff decoy throws). The landing cell is clamped into the maze and,
     * if it lands inside a firewall, walked back toward the runner until it
     * sits on a cell the ICE can actually walk to. Returns the landing cell,
     * or null when no pulse could be emitted.
     */
    fun emitNoiseAt(cellX: Int, cellY: Int, radiusCells: Float, intensity: Float): Pair<Int, Int>? {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) return null
        val maze = state.maze
        if (maze.isEmpty()) return null
        var x = cellX.coerceIn(0, maze[0].lastIndex)
        var y = cellY.coerceIn(0, maze.lastIndex)
        // A phantom signal embedded in a firewall or a crawl duct (d8 slice 7)
        // cannot be investigated:
        // walk the landing point straight back toward the runner until it
        // sits on a cell the ICE can actually reach (same movement blockers
        // as the stealth world adapter, so guard pathing agrees with the
        // pulse spot).
        while (maze[y][x] in NOISE_LANDING_BLOCKERS && (x != state.gridX || y != state.gridY)) {
            when {
                x > state.gridX -> x--
                x < state.gridX -> x++
                y > state.gridY -> y--
                else -> y++
            }
        }
        pendingNoise.addLast(
            NoisePulse(
                position = StealthPosition(x + 0.5f, y + 0.5f, state.level),
                radius = radiusCells,
                intensity = intensity
            )
        )
        while (pendingNoise.size > MAX_PENDING_NOISE) pendingNoise.removeFirst()
        return x to y
    }

    /**
     * d8 slice 4: ICE neutralization (the runner's overclocked node spike).
     * Disables the nearest active ICE unit of the current floor within
     * maxRangeCells for durationSeconds: while down it cannot see, hear,
     * move or relay the alarm, then it reboots into PATROLLING with a
     * clean slate. Returns the disabled unit's id, or null when the world
     * is not live, the floor has no active unit, or the nearest one sits
     * out of range.
     */
    fun disableNearestIceUnit(maxRangeCells: Float, durationSeconds: Float): String? {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) return null
        val stealth = stealthSession ?: return null
        val player = StealthPosition(state.gridX + 0.5f, state.gridY + 0.5f, state.level)
        val target = stealth.snapshot().guards
            .filter { it.state != StealthGuardState.DISABLED }
            .filter { it.position.levelId == state.level }
            .minByOrNull { it.position.distanceTo(player) }
            ?: return null
        if (target.position.distanceTo(player) > maxRangeCells) return null
        publishHud(stealth.disableGuard(target.id, durationSeconds))
        return target.id
    }

    /**
     * d8 slice 5: breach acoustics - the breach-protocol outcome leaves an
     * acoustic trace at the hacked node, never at the runner. A clean breach
     * is a faint backwash (only ICE units hugging the terminal glance over,
     * at the low noise floor); a failed breach trips the node's
     * buffer-overflow klaxon - a full-power noise pulse that pulls every ICE
     * unit within the radius onto the terminal, buying the runner a getaway
     * window while the alarm burns out away from their position. Returns the
     * pulse's landing cell, or null when the world is not live (e.g. the
     * feedback damage flatlined the runner).
     */
    fun emitBreachOutcome(cellX: Int, cellY: Int, success: Boolean): Pair<Int, Int>? {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) return null
        val radiusCells = if (success) BREACH_SUCCESS_RADIUS_CELLS else BREACH_FAILURE_RADIUS_CELLS
        val intensity = if (success) BREACH_SUCCESS_INTENSITY else BREACH_FAILURE_INTENSITY
        val landing = emitNoiseAt(
            cellX = cellX,
            cellY = cellY,
            radiusCells = radiusCells,
            intensity = intensity
        ) ?: return null
        if (success) {
            onLog(
                "QUIET BREACH: faint intrusion backwash at (" +
                    landing.first + ", " + landing.second +
                    ") - ICE hugging the node may glance over.",
                LogType.INFO
            )
        } else {
            onLog(
                "ACOUSTIC SPIKE: buffer-overflow klaxon at (" +
                    landing.first + ", " + landing.second +
                    ") - ICE audio sweeps converge on the node!",
                LogType.ALERT
            )
        }
        return landing
    }

    /**
     * d8 slice 8: field acoustics - a utility compiled from the UI depth
     * (the inventory overlay or the terminal `use` command) blooms an EM
     * footprint at the runner's cell. The acoustic signature is derived
     * from the item's own effect profile (a registry lookup - deterministic,
     * no RNG): a hardware reconfiguration screams, a RAM surge howls, a
     * nano-injector hisses, everything else hums. Combat compiles are the
     * combat flow's business and stay out of the stealth sim. Returns the
     * (radius, intensity) signature used, or null when the world is not
     * live.
     */
    fun emitItemUsage(itemName: String): Pair<Float, Float>? {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) return null
        if (state.activeEnemy != null || state.gameState != GameState.EXPLORATION) return null
        val item = GameItemRegistry.getItemByName(itemName)
        val radiusCells: Float
        val intensity: Float
        when {
            item.damageBonus > 0 || item.defenseBonus > 0 -> {
                radiusCells = ITEM_RECONFIGURE_RADIUS_CELLS
                intensity = ITEM_RECONFIGURE_INTENSITY
            }
            item.restoreRam > 0 -> {
                radiusCells = ITEM_SURGE_RADIUS_CELLS
                intensity = ITEM_SURGE_INTENSITY
            }
            item.healIntegrity > 0 -> {
                radiusCells = ITEM_INJECTOR_RADIUS_CELLS
                intensity = ITEM_INJECTOR_INTENSITY
            }
            else -> {
                radiusCells = ITEM_SOFT_RADIUS_CELLS
                intensity = ITEM_SOFT_INTENSITY
            }
        }
        emitNoise(radiusCells = radiusCells, intensity = intensity)
        onLog(
            "FIELD COMPILE: '" + item.name + "' deployed - EM bloom at your cell.",
            LogType.INFO
        )
        return radiusCells to intensity
    }

    /**
     * Cover rule for the Labyrinth grid: standing on a hardened/hidden cell
     * (stealth cache, crypt-cache, service vents) dampens ICE perception.
     * Deterministic from the maze alone, so seed determinism is preserved.
     */
    private fun isInCover(state: GameUiState): Boolean {
        val maze = state.maze
        val y = state.gridY
        val x = state.gridX
        if (y !in maze.indices || x !in maze[y].indices) return false
        return maze[y][x] in COVER_CELL_TYPES
    }

    /**
     * d8 slice 7: duct-crawl rule for the Labyrinth grid: standing inside
     * a service ventilation duct (VENT_TUNNEL / ALTERNATIVE_VENT) makes the
     * runner thermally invisible to ICE optics - the conduit walls dampen
     * their heat signature. Deterministic from the maze alone, so seed
     * determinism is preserved.
     */
    private fun isRunnerInDuct(state: GameUiState): Boolean {
        val maze = state.maze
        val y = state.gridY
        val x = state.gridX
        if (y !in maze.indices || x !in maze[y].indices) return false
        return maze[y][x] in VENT_CELL_TYPES
    }

    /**
     * Publishes the ICE threat HUD slice of GameUiState: the highest suspicion
     * among the active guards of the current floor plus their most advanced
     * hunt state. A pure read of the shared simulation frame - no extra
     * controller state and no gameplay effects. Unchanged values keep the
     * old GameUiState instance so a calm floor does not trigger recomposition.
     */
    private fun publishHud(frame: StealthSessionFrame?) {
        if (frame == null) return
        val activeGuards = frame.guards.filter { it.state != StealthGuardState.DISABLED }
        val suspicion = activeGuards.maxOfOrNull { it.suspicion } ?: 0f
        val guardState = activeGuards
            .maxByOrNull { it.state.ordinal }
            ?.state
            ?: StealthGuardState.PATROLLING
        val inDuct = isRunnerInDuct(_uiState.value)
        _uiState.update { s ->
            if (s.iceSuspicion == suspicion && s.iceGuardState == guardState && s.isInDuct == inDuct) s
            else s.copy(iceSuspicion = suspicion, iceGuardState = guardState, isInDuct = inDuct)
        }
    }

    private fun drainNoise(): List<NoisePulse> {
        val pulses = pendingNoise.toList()
        pendingNoise.clear()
        return pulses
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
        // d8 slice 2: the mobile patrol set derives from the floor's walkable
        // area, so the patrol count joins the node list in the guard-set
        // signature (a count change rebuilds the session).
        val walkable = mutableListOf<GridCell>()
        for (y in maze.indices) {
            for (x in maze[y].indices) {
                if (maze[y][x] !in LabyrinthStealthSession.DEFAULT_MOVEMENT_BLOCKERS) {
                    walkable.add(GridCell(x, y, levelId))
                }
            }
        }
        val patrolCount = patrolCountFor(walkable.size)
        val signature = buildString {
            append(levelId).append(':')
            nodes.sortedWith(compareBy({ it.second }, { it.first })).forEach {
                append(it.first).append(',').append(it.second).append(';')
            }
            append('|').append(patrolCount)
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
        // d8 slice 2: mobile ICE patrols - roaming sweep units with
        // deterministic waypoint loops. Surviving patrol guards keep their
        // runtime state across maze array replacement, exactly like the
        // stationary node guards; a floor switch reseeds the patrol set.
        for (n in 0 until patrolCount) {
            val id = patrolId(levelId, n)
            nextGuards.add(existingById[id] ?: newPatrol(id, n, walkable, levelId, levelSeed))
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

        // d8 slice 2: announce the roaming sweep units once per floor. The
        // controller re-syncs on every maze array replacement, so the log
        // fires only when the level or the patrol count changes.
        if (patrolCount > 0) {
            val patrolLogSignature = levelId.toString() + ":" + patrolCount
            if (patrolLogSignature != lastPatrolLogSignature) {
                lastPatrolLogSignature = patrolLogSignature
                onLog(
                    "ICE PATROL SIGNATURES: " + patrolCount +
                        " roaming sweep unit(s) detected on this floor.",
                    LogType.INFO
                )
            }
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
        publishHud(frame)
        session = RealTimeGameSession(stealth)
        combatSeen = false
    }

    private fun newGuard(id: String, x: Int, y: Int, levelId: Int, levelSeed: Long): StealthGuard =
        StealthGuard(
            id = id,
            position = StealthPosition(x + 0.5f, y + 0.5f, levelId),
            facingAngleRadians = LabyrinthStealthSession.angleForDirection(facingFor(x, y, levelSeed))
        )

    /**
     * d8 slice 2: deterministic mobile patrols. The floor's walkable area
     * decides how many roam it (0 below the size threshold, then 1-3), so
     * cramped corridors and test mazes stay patrol-free while real floors
     * feel inhabited. Derived from the maze alone - seed determinism holds.
     */
    private fun patrolCountFor(walkableCells: Int): Int {
        if (walkableCells < PATROL_MIN_WALKABLE_CELLS) return 0
        return (walkableCells / PATROL_AREA_PER_UNIT).coerceIn(1, MAX_PATROLS_PER_FLOOR)
    }

    private fun patrolId(levelId: Int, n: Int): String = "ice_patrol_" + levelId + "_" + n

    /**
     * A patrol is a closed waypoint loop hashed out of the floor's walkable
     * cells (the same Knuth-style hash family as the ECHOES phantom pulses,
     * salted with the patrol index and the seed). The same maze + level seed
     * always replay the same routes; the engine's advancePatrol walks the
     * loop cell-by-cell through A* and skips waypoints a floor mutation
     * turned into a wall.
     */
    private fun newPatrol(
        id: String,
        n: Int,
        walkable: List<GridCell>,
        levelId: Int,
        levelSeed: Long
    ): StealthGuard {
        val route = mutableListOf<GridCell>()
        var attempt = 0
        while (route.size < PATROL_ROUTE_WAYPOINTS && attempt < walkable.size * 2) {
            val hash = (
                levelSeed * 2654435761L +
                    levelId * 40503L +
                    n * 97L +
                    route.size * 31L +
                    attempt * 7L
                ).toInt()
            val index = ((hash % walkable.size) + walkable.size) % walkable.size
            val cell = walkable[index]
            if (cell !in route) route.add(cell)
            attempt++
        }
        val start = route.first()
        return StealthGuard(
            id = id,
            position = start.center(),
            facingAngleRadians = LabyrinthStealthSession.angleForDirection(
                facingFor(start.x, start.y, levelSeed)
            ),
            patrolRoute = route
        )
    }

    private fun guardId(x: Int, y: Int): String = "ice_" + x + "_" + y

    private fun facingFor(x: Int, y: Int, levelSeed: Long): Direction {
        val values = Direction.values()
        val index = ((x * 31 + y * 17 + levelSeed.toInt()) % values.size + values.size) % values.size
        return values[index]
    }

    companion object {
        const val FOOTSTEP_RADIUS_CELLS: Float = 2.5f
        const val FOOTSTEP_INTENSITY: Float = 0.35f
        const val BUMP_RADIUS_CELLS: Float = 1.5f
        const val BUMP_INTENSITY: Float = 0.25f
        const val DECOY_THROW_CELLS: Int = 4
        const val DECOY_RADIUS_CELLS: Float = 6f
        const val DECOY_INTENSITY: Float = 1f
        const val DECOY_RAM_COST: Int = 1

        /** d8 slice 4: the node spike reaches this far from the runner (cells). */
        const val JAM_RANGE_CELLS: Float = 6f

        /** d8 slice 4: how long a spiked unit stays in a forced reboot. */
        const val JAM_DURATION_SECONDS: Float = 8f

        /** d8 slice 4: RAM cost of the overclocked node spike. */
        const val JAM_RAM_COST: Int = 2

        /** d8 slice 5: acoustic radius of a clean breach (faint backwash at the hacked node). */
        const val BREACH_SUCCESS_RADIUS_CELLS: Float = 2f

        /** d8 slice 5: noise-floor multiplier of a clean breach (a glance, not a sweep). */
        const val BREACH_SUCCESS_INTENSITY: Float = 0.3f

        /** d8 slice 5: acoustic radius of a failed breach (the node's klaxon). */
        const val BREACH_FAILURE_RADIUS_CELLS: Float = 5.5f

        /** d8 slice 5: full-power klaxon of a buffer overflow at the hacked node. */
        const val BREACH_FAILURE_INTENSITY: Float = 1f

        /** d8 slice 8: hardware reconfiguration (damage/defense mods) - the loudest field compile. */
        const val ITEM_RECONFIGURE_RADIUS_CELLS: Float = 4f
        const val ITEM_RECONFIGURE_INTENSITY: Float = 0.6f

        /** d8 slice 8: a RAM surge draws hard on the local power rail. */
        const val ITEM_SURGE_RADIUS_CELLS: Float = 3.5f
        const val ITEM_SURGE_INTENSITY: Float = 0.55f

        /** d8 slice 8: a nano-injector hisses as it knits the runner's core. */
        const val ITEM_INJECTOR_RADIUS_CELLS: Float = 3f
        const val ITEM_INJECTOR_INTENSITY: Float = 0.4f

        /** d8 slice 8: quiet compiles (credits, forecasts, combat stims) barely hum. */
        const val ITEM_SOFT_RADIUS_CELLS: Float = 2.5f
        const val ITEM_SOFT_INTENSITY: Float = 0.3f

        /** d8 slice 2: floors with fewer walkable cells host no mobile patrols. */
        const val PATROL_MIN_WALKABLE_CELLS: Int = 40

        /** One extra roaming patrol per this many walkable cells. */
        const val PATROL_AREA_PER_UNIT: Int = 100

        const val MAX_PATROLS_PER_FLOOR: Int = 3

        const val PATROL_ROUTE_WAYPOINTS: Int = 4
        private val NOISE_LANDING_BLOCKERS: Set<CellType> = setOf(
            CellType.WALL,
            CellType.SECRET_WALL,
            CellType.TERMINAL_DOOR,
            // d8 slice 7: a phantom signal embedded in a crawl duct is
            // unreachable for ICE - it rolls back to the duct mouth.
            CellType.VENT_TUNNEL,
            CellType.ALTERNATIVE_VENT
        )
        private const val MAX_PENDING_NOISE: Int = 16
        private val COVER_CELL_TYPES: Set<CellType> = setOf(
            CellType.SCAN_CACHE,
            CellType.SECRET_CACHE,
            CellType.VENT_TUNNEL,
            CellType.ALTERNATIVE_VENT
        )

        /** d8 slice 7: service ducts - the runner's thermal concealment network. */
        private val VENT_CELL_TYPES: Set<CellType> = setOf(
            CellType.VENT_TUNNEL,
            CellType.ALTERNATIVE_VENT
        )

        /** d8 slice 7: cover-slip grace in world ticks (100 ms cadence -> 1 second). */
        const val COVER_GRACE_TICKS: Int = 10
    }
}
