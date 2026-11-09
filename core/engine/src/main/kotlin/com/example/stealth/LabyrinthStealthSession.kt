package com.example.stealth

import com.example.core.model.CellType
import com.example.core.model.CyberWeather
import com.example.core.model.Direction
import kotlin.math.PI

/**
 * Read-only view of a simulated guard suitable for a HUD or a renderer overlay.
 * The renderer remains free to decide how (or whether) to draw these values.
 */
data class StealthGuardSnapshot(
    val id: String,
    val position: StealthPosition,
    val facingAngleRadians: Float,
    val state: StealthGuardState,
    val suspicion: Float,
    val lastKnownPlayerPosition: StealthPosition?,
    val searchSecondsRemaining: Float
)

data class StealthSessionFrame(
    val playerPosition: StealthPosition?,
    val events: List<StealthEvent>,
    val guards: List<StealthGuardSnapshot>
) {
    val playerDetected: Boolean
        get() = guards.any { it.state == StealthGuardState.ALERTED }
}

/**
 * Bridge between Labyrinth's cell-based game state and the independent stealth simulation.
 *
 * - Player coordinates are the gridX/gridY values used by GameUiState.
 * - The simulation places the player at the centre of that cell.
 * - Map snapshots can be replaced when a floor or dynamic tile layout changes.
 * - Rendering, combat, persistence and game input remain owned by the existing game.
 *
 * This class does not start its own timer: the caller controls update cadence, which keeps
 * the runtime usable for a future real-time loop as well as deterministic tests.
 */
class LabyrinthStealthSession(
    initialMaze: Array<Array<CellType>>,
    guards: List<StealthGuard>,
    private val levelId: Int = 0,
    movementBlockers: Set<CellType> = DEFAULT_MOVEMENT_BLOCKERS,
    visionBlockers: Set<CellType> = DEFAULT_VISION_BLOCKERS
) {
    private val world = RefreshableLabyrinthWorld(
        initialMaze = initialMaze,
        configuredLevelId = levelId,
        movementBlockers = movementBlockers,
        visionBlockers = visionBlockers
    )
    private val simulation = StealthSimulation(world, guards)
    private var currentPlayerPosition: StealthPosition? = null
    private var echoPulseCounter: Int = 0

    /**
     * Advances the AI and returns a compact snapshot for UI/renderer code.
     * A missing frame does not imply that the player has been detected; inspect
     * StealthSessionFrame.playerDetected or the guard states in the returned frame.
     *
     * The optional weather maps the current CyberWeather onto perception factors
     * (WeatherPerception) and, for ECHOES, spawns deterministic phantom echo
     * pulses that lure ICE off their patrol routes.
     */
    fun update(
        deltaTimeSeconds: Float,
        gridX: Int,
        gridY: Int,
        isSneaking: Boolean = false,
        isInvisible: Boolean = false,
        isInCover: Boolean = false,
        noisePulses: List<NoisePulse> = emptyList(),
        weather: CyberWeather = CyberWeather.CLEAR
    ): StealthSessionFrame {
        val position = StealthPosition(
            x = gridX + 0.5f,
            y = gridY + 0.5f,
            levelId = levelId
        )
        currentPlayerPosition = position
        val player = StealthPlayer(
            position = position,
            isSneaking = isSneaking,
            isInvisible = isInvisible,
            isInCover = isInCover
        )
        val effectivePulses = if (weather == CyberWeather.ECHOES) {
            echoPulseCounter++
            val phantom = if (echoPulseCounter % ECHO_PULSE_INTERVAL_TICKS == 0) {
                phantomEchoPulse(position)
            } else {
                null
            }
            if (phantom != null) noisePulses + phantom else noisePulses
        } else {
            noisePulses
        }
        val events = simulation.update(
            deltaTimeSeconds = deltaTimeSeconds,
            player = player,
            noisePulses = effectivePulses,
            weather = WeatherPerception.forWeather(weather)
        )
        return snapshot(events)
    }

    /**
     * Deterministic phantom echo stimulus (ECHOES weather): a dead netrunner's
     * telemetry fragment flickering back into reality at a walkable cell away
     * from the runner. The cell is derived from the echo tick counter and the
     * level id alone - the same session state always replays the same
     * sequence, so seed determinism holds. ICE hears the remnant and drifts
     * off its patrol to investigate it.
     */
    private fun phantomEchoPulse(playerPosition: StealthPosition): NoisePulse? {
        val candidates = mutableListOf<StealthPosition>()
        for (y in 0 until world.height) {
            for (x in 0 until world.width) {
                if (!world.isWalkable(x, y)) continue
                val cellCenter = StealthPosition(x + 0.5f, y + 0.5f, levelId)
                if (cellCenter.distanceTo(playerPosition) <= ECHO_PLAYER_EXCLUSION_CELLS) continue
                candidates.add(cellCenter)
            }
        }
        if (candidates.isEmpty()) return null
        val hash = (echoPulseCounter.toLong() * 2654435761L + levelId * 40503L).toInt()
        val index = ((hash % candidates.size) + candidates.size) % candidates.size
        return NoisePulse(
            position = candidates[index],
            radius = ECHO_PULSE_RADIUS_CELLS,
            intensity = ECHO_PULSE_INTENSITY
        )
    }

    /**
     * Replaces the active floor/map snapshot. Path caches are invalidated so guards won't
     * continue along a route made invalid by changed walls or doors.
     */
    fun replaceMaze(maze: Array<Array<CellType>>) {
        world.replaceMaze(maze)
        simulation.invalidatePaths()
    }

    /**
     * Post-combat policy entry point: ALERTED guards de-escalate to
     * INVESTIGATING/SEARCHING while keeping the player's last known
     * position. Returns the frame with the emitted guard state changes so
     * the HUD/renderer can surface the de-escalation.
     */
    fun deescalateAfterCombat(): StealthSessionFrame {
        val events = simulation.deescalateAfterCombat()
        return snapshot(events)
    }

    /**
     * d8 slice 4: forces an ICE unit into DISABLED for durationSeconds
     * (the runner's node spike). While down the unit is blind, deaf,
     * immobile and cut off from the ICE net; it reboots into PATROLLING
     * when the spike expires. Returns the frame carrying the emitted
     * state-change events (empty events when the id is unknown or the
     * unit is already disabled).
     */
    fun disableGuard(guardId: String, durationSeconds: Float): StealthSessionFrame {
        val events = simulation.disableGuard(guardId, durationSeconds)
        return snapshot(events)
    }

    fun snapshot(events: List<StealthEvent> = emptyList()): StealthSessionFrame =
        StealthSessionFrame(
            playerPosition = currentPlayerPosition,
            events = events.toList(),
            guards = simulation.guards.map { guard ->
                StealthGuardSnapshot(
                    id = guard.id,
                    position = guard.position,
                    facingAngleRadians = guard.facingAngleRadians,
                    state = guard.state,
                    suspicion = guard.suspicion,
                    lastKnownPlayerPosition = guard.lastKnownPlayerPosition,
                    searchSecondsRemaining = guard.searchSecondsRemaining
                )
            }
        )

    companion object {
        /** One phantom echo per N session ticks (100 ms cadence -> 4 s). */
        const val ECHO_PULSE_INTERVAL_TICKS: Int = 40

        /** Phantom echoes are heard by ICE within this radius (cells). */
        const val ECHO_PULSE_RADIUS_CELLS: Float = 3.5f

        /** Investigation weight of a phantom echo (see NoisePulse.intensity). */
        const val ECHO_PULSE_INTENSITY: Float = 0.6f

        /** Echoes never spawn this close to the runner: they lure, not reveal. */
        const val ECHO_PLAYER_EXCLUSION_CELLS: Float = 2.5f

        val DEFAULT_MOVEMENT_BLOCKERS: Set<CellType> = setOf(
            CellType.WALL,
            CellType.SECRET_WALL,
            CellType.TERMINAL_DOOR,
            // d8 slice 7: ICE sweep units are too bulky for the crawl ducts -
            // service ventilation (VENT_TUNNEL / ALTERNATIVE_VENT) is the
            // runner's stealth network, never an ICE route. This is also the
            // walkable set the controller hashes patrol routes from, so no
            // patrol waypoint ever lands inside a duct.
            CellType.VENT_TUNNEL,
            CellType.ALTERNATIVE_VENT
        )

        val DEFAULT_VISION_BLOCKERS: Set<CellType> = setOf(
            CellType.WALL,
            CellType.SECRET_WALL,
            CellType.TERMINAL_DOOR
        )

        /**
         * Converts Labyrinth's cardinal direction to the angle convention used by stealth
         * perception: east=0, south=PI/2, west=PI, north=-PI/2 (screen/grid Y points down).
         */
        fun angleForDirection(direction: Direction): Float = when (direction) {
            Direction.EAST -> 0f
            Direction.SOUTH -> PI.toFloat() / 2f
            Direction.WEST -> PI.toFloat()
            Direction.NORTH -> -PI.toFloat() / 2f
        }
    }
}

private class RefreshableLabyrinthWorld(
    initialMaze: Array<Array<CellType>>,
    private val configuredLevelId: Int,
    private val movementBlockers: Set<CellType>,
    private val visionBlockers: Set<CellType>
) : StealthWorld {
    private var delegate = createAdapter(initialMaze)

    override val width: Int
        get() = delegate.width

    override val height: Int
        get() = delegate.height

    override val levelId: Int
        get() = configuredLevelId

    override fun isWalkable(x: Int, y: Int): Boolean = delegate.isWalkable(x, y)

    override fun blocksVision(x: Int, y: Int): Boolean = delegate.blocksVision(x, y)

    override fun findPath(start: GridCell, goal: GridCell): List<GridCell>? =
        delegate.findPath(start, goal)

    override fun hasLineOfSight(from: StealthPosition, to: StealthPosition): Boolean =
        delegate.hasLineOfSight(from, to)

    fun replaceMaze(maze: Array<Array<CellType>>) {
        delegate = createAdapter(maze)
    }

    private fun createAdapter(maze: Array<Array<CellType>>): LabyrinthWorldAdapter {
        val copied = Array(maze.size) { y -> maze[y].copyOf() }
        return LabyrinthWorldAdapter(
            cells = copied,
            levelId = configuredLevelId,
            movementBlockers = movementBlockers,
            visionBlockers = visionBlockers
        )
    }
}
