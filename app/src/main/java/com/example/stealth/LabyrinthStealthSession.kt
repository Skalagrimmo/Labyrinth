package com.example.stealth

import com.example.data.CellType
import com.example.data.Direction
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

    /**
     * Advances the AI and returns a compact snapshot for UI/renderer code.
     * A missing frame does not imply that the player has been detected; inspect
     * StealthSessionFrame.playerDetected or the guard states in the returned frame.
     */
    fun update(
        deltaTimeSeconds: Float,
        gridX: Int,
        gridY: Int,
        isSneaking: Boolean = false,
        isInvisible: Boolean = false,
        isInCover: Boolean = false,
        noisePulses: List<NoisePulse> = emptyList()
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
        val events = simulation.update(deltaTimeSeconds, player, noisePulses)
        return snapshot(events)
    }

    /**
     * Replaces the active floor/map snapshot. Path caches are invalidated so guards won't
     * continue along a route made invalid by changed walls or doors.
     */
    fun replaceMaze(maze: Array<Array<CellType>>) {
        world.replaceMaze(maze)
        simulation.invalidatePaths()
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
        val DEFAULT_MOVEMENT_BLOCKERS: Set<CellType> = setOf(
            CellType.WALL,
            CellType.SECRET_WALL,
            CellType.TERMINAL_DOOR
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
