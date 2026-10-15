package com.example.stealth

import com.example.data.CellType
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Position used by the real-time stealth simulation.
 *
 * x/y are continuous map coordinates; levelId identifies the map layer.
 * A position at (2.5, 4.5) is the centre of cell (2, 4).
 */
data class StealthPosition(
    val x: Float,
    val y: Float,
    val levelId: Int = 0
) {
    fun cell(): GridCell = GridCell(floor(x).toInt(), floor(y).toInt(), levelId)

    fun distanceTo(other: StealthPosition): Float {
        if (levelId != other.levelId) return Float.POSITIVE_INFINITY
        return hypot(other.x - x, other.y - y)
    }
}

/** Integer cell coordinate in a single map layer. */
data class GridCell(val x: Int, val y: Int, val levelId: Int = 0) {
    fun center(): StealthPosition = StealthPosition(x + 0.5f, y + 0.5f, levelId)
}

/**
 * Minimal contract required by stealth AI.
 *
 * This deliberately does not expose Android, Compose, rendering or persistence types,
 * so the simulation can be tested on the JVM and adapted to another map representation.
 */
interface StealthWorld {
    val width: Int
    val height: Int
    val levelId: Int

    fun isWalkable(x: Int, y: Int): Boolean
    fun blocksVision(x: Int, y: Int): Boolean

    fun isWalkable(cell: GridCell): Boolean =
        cell.levelId == levelId && isWalkable(cell.x, cell.y)

    fun findPath(start: GridCell, goal: GridCell): List<GridCell>? =
        StealthPathfinder.findPath(this, start, goal)

    fun hasLineOfSight(from: StealthPosition, to: StealthPosition): Boolean {
        if (from.levelId != levelId || to.levelId != levelId) return false
        if (from.x < 0f || from.y < 0f || to.x < 0f || to.y < 0f) return false
        if (from.x >= width || from.y >= height || to.x >= width || to.y >= height) return false

        var x = from.cell().x
        var y = from.cell().y
        val targetX = to.cell().x
        val targetY = to.cell().y
        if (x == targetX && y == targetY) return true

        val dx = abs(targetX - x)
        val dy = abs(targetY - y)
        val stepX = if (x < targetX) 1 else -1
        val stepY = if (y < targetY) 1 else -1
        var error = dx - dy

        while (x != targetX || y != targetY) {
            val doubledError = 2 * error
            if (doubledError > -dy) {
                error -= dy
                x += stepX
            }
            if (doubledError < dx) {
                error += dx
                y += stepY
            }
            if (blocksVision(x, y)) return false
        }
        return true
    }
}

/**
 * Adapter for Labyrinth's row-major maze representation: cells[y][x].
 *
 * Blocked tile sets are configurable because a locked door, secret wall or security node
 * may become traversable depending on the active mission rules. Defaults are conservative
 * for stealth navigation and can be changed by the caller when a door is opened.
 */
class LabyrinthWorldAdapter(
    private val cells: Array<Array<CellType>>,
    override val levelId: Int = 0,
    private val movementBlockers: Set<CellType> = setOf(
        CellType.WALL,
        CellType.SECRET_WALL,
        CellType.TERMINAL_DOOR
    ),
    private val visionBlockers: Set<CellType> = setOf(
        CellType.WALL,
        CellType.SECRET_WALL,
        CellType.TERMINAL_DOOR
    )
) : StealthWorld {
    override val height: Int = cells.size
    override val width: Int = cells.firstOrNull()?.size ?: 0

    init {
        require(height > 0 && width > 0) { "Stealth map must not be empty" }
        require(cells.all { it.size == width }) { "Stealth map rows must have equal width" }
    }

    fun tileAt(x: Int, y: Int): CellType? =
        if (x in 0 until width && y in 0 until height) cells[y][x] else null

    override fun isWalkable(x: Int, y: Int): Boolean {
        val tile = tileAt(x, y) ?: return false
        return tile !in movementBlockers
    }

    override fun blocksVision(x: Int, y: Int): Boolean {
        val tile = tileAt(x, y) ?: return true
        return tile in visionBlockers
    }
}

private object StealthPathfinder {
    private data class Node(val cell: GridCell, val gCost: Int, val fCost: Int)

    private val directions = arrayOf(
        1 to 0,
        -1 to 0,
        0 to 1,
        0 to -1
    )

    fun findPath(world: StealthWorld, start: GridCell, goal: GridCell): List<GridCell>? {
        if (start.levelId != world.levelId || goal.levelId != world.levelId) return null
        if (!world.isWalkable(start) || !world.isWalkable(goal)) return null
        if (start == goal) return emptyList()

        val open = PriorityQueue(compareBy<Node> { it.fCost }.thenBy { it.gCost })
        val cameFrom = mutableMapOf<GridCell, GridCell>()
        val bestCost = mutableMapOf(start to 0)
        val closed = mutableSetOf<GridCell>()

        open.add(Node(start, 0, heuristic(start, goal)))

        while (open.isNotEmpty()) {
            val current = open.poll()
            if (current.cell in closed) continue
            if (current.gCost != bestCost[current.cell]) continue

            if (current.cell == goal) {
                val reversedPath = mutableListOf<GridCell>()
                var cursor = goal
                while (cursor != start) {
                    reversedPath.add(cursor)
                    cursor = cameFrom[cursor] ?: return null
                }
                reversedPath.reverse()
                return reversedPath
            }

            closed.add(current.cell)
            for ((dx, dy) in directions) {
                val neighbour = GridCell(
                    current.cell.x + dx,
                    current.cell.y + dy,
                    world.levelId
                )
                if (!world.isWalkable(neighbour) || neighbour in closed) continue

                val tentativeCost = current.gCost + 1
                if (tentativeCost < (bestCost[neighbour] ?: Int.MAX_VALUE)) {
                    cameFrom[neighbour] = current.cell
                    bestCost[neighbour] = tentativeCost
                    open.add(
                        Node(
                            cell = neighbour,
                            gCost = tentativeCost,
                            fCost = tentativeCost + heuristic(neighbour, goal)
                        )
                    )
                }
            }
        }
        return null
    }

    private fun heuristic(a: GridCell, b: GridCell): Int =
        abs(a.x - b.x) + abs(a.y - b.y)
}
