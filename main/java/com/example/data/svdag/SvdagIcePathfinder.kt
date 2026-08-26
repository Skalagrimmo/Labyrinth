package com.example.data.svdag

import com.example.data.CellType
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Alert Levels for ICE (Intrusion Countermeasures Electronics) Entities.
 */
enum class IceAlertLevel(val label: String, val colorHex: Long) {
    PATROL("🟢 PATROL MODE", 0xFF10B981),
    SUSPICIOUS("🟡 SEARCHING / SUSPICIOUS", 0xFFF59E0B),
    HUNTING("🔴 HUNTING PLAYER", 0xFFEF4444)
}

/**
 * Types of ICE units with distinct speeds, vision ranges, and combat capabilities.
 */
enum class IceType(
    val typeName: String,
    val speed: Int,
    val detectionRadius: Int,
    val colorHex: Long,
    val description: String
) {
    TRACE_ICE("Trace ICE Unit", 1, 6, 0xFFFF3366, "Fast lightweight scanner process patrolling corridors."),
    PATROL_ICE("Heavy Patrol ICE", 1, 8, 0xFFFF0055, "Standard defense matrix unit executing automated hallway sweeps."),
    BLACK_ICE("Black-ICE Hunter", 2, 10, 0xFFDC2626, "High-lethal security daemon with dual-step pursuit engine.")
}

/**
 * State representation for an individual ICE entity patrolling SVDAG hallways.
 */
data class IceEntity(
    val id: String,
    val name: String,
    var x: Int,
    var y: Int,
    var z: Int,
    val type: IceType = IceType.PATROL_ICE,
    var alertLevel: IceAlertLevel = IceAlertLevel.PATROL,
    var patrolRoute: List<Triple<Int, Int, Int>> = emptyList(),
    var waypointIndex: Int = 0,
    var lastKnownPlayerPos: Triple<Int, Int, Int>? = null,
    var facingDx: Int = 1,
    var facingDy: Int = 0,
    var facingDz: Int = 0,
    var stepCount: Int = 0
)

/**
 * Status evaluating whether the player runner is currently hidden or exposed to ICE patrols.
 */
data class PlayerHideStatus(
    val isHidden: Boolean,
    val hideReason: String,
    val hidingVoxel: VoxelType
)

/**
 * Output outcome of an ICE pathfinding step.
 */
data class IcePathStepResult(
    val updatedIce: IceEntity,
    val actionMessage: String,
    val alertChanged: Boolean,
    val interceptedPlayer: Boolean,
    val pathTaken: List<Triple<Int, Int, Int>> = emptyList()
)

/**
 * Pathfinding node wrapper for A* algorithm.
 */
private data class PathNode(
    val pos: Triple<Int, Int, Int>,
    val gScore: Int,
    val fScore: Int,
    val parent: PathNode? = null
) : Comparable<PathNode> {
    override fun compareTo(other: PathNode): Int = this.fScore.compareTo(other.fScore)
}

/**
 * High-performance A* / BFS Pathfinding AI for ICE entities in SVDAG Hallways.
 */
object SvdagIcePathfinder {

    /**
     * Evaluates whether the player runner is currently hidden in a vent, safe zone, or behind cover.
     */
    fun evaluatePlayerHidingStatus(
        playerX: Int,
        playerY: Int,
        playerZ: Int,
        dag: SparseVoxelDag?,
        maze: Array<Array<CellType>>? = null
    ): PlayerHideStatus {
        val voxel = dag?.getVoxel(playerX, playerY, playerZ) ?: VoxelType.EMPTY

        if (voxel == VoxelType.SAFE_ZONE) {
            return PlayerHideStatus(
                isHidden = true,
                hideReason = "🛡️ HIDDEN: Inside Access Point Safe Zone (ICE Signal Blocked)",
                hidingVoxel = VoxelType.SAFE_ZONE
            )
        }

        if (voxel == VoxelType.ALTERNATIVE_VENT || voxel == VoxelType.VENT_TUNNEL) {
            return PlayerHideStatus(
                isHidden = true,
                hideReason = "🌬️ HIDDEN: Inside Sub-Conduit Ventilation Duct (Thermal Dampened)",
                hidingVoxel = VoxelType.ALTERNATIVE_VENT
            )
        }

        if (maze != null && playerY in maze.indices && playerX in maze[0].indices) {
            val cell = maze[playerY][playerX]
            if (cell == CellType.SAFE_ZONE) {
                return PlayerHideStatus(
                    isHidden = true,
                    hideReason = "🛡️ HIDDEN: Standing inside Safe Zone node",
                    hidingVoxel = VoxelType.SAFE_ZONE
                )
            }
            if (cell == CellType.ALTERNATIVE_VENT || cell == CellType.VENT_TUNNEL) {
                return PlayerHideStatus(
                    isHidden = true,
                    hideReason = "🌬️ HIDDEN: Inside ventilation duct corridor",
                    hidingVoxel = VoxelType.ALTERNATIVE_VENT
                )
            }
        }

        return PlayerHideStatus(
            isHidden = false,
            hideReason = "🚨 EXPOSED: In open SVDAG hallway (Visible to ICE sensors)",
            hidingVoxel = voxel
        )
    }

    /**
     * Checks if direct line-of-sight exists between ICE unit and player voxel.
     */
    fun hasLineOfSight(
        iceX: Int, iceY: Int, iceZ: Int,
        pX: Int, pY: Int, pZ: Int,
        dag: SparseVoxelDag
    ): Boolean {
        val dx = pX - iceX
        val dy = pY - iceY
        val dz = pZ - iceZ
        val steps = max(max(abs(dx), abs(dy)), abs(dz))
        if (steps == 0) return true

        val stepX = dx.toDouble() / steps
        val stepY = dy.toDouble() / steps
        val stepZ = dz.toDouble() / steps

        var currX = iceX.toDouble() + 0.5
        var currY = iceY.toDouble() + 0.5
        var currZ = iceZ.toDouble() + 0.5

        for (i in 1..steps) {
            currX += stepX
            currY += stepY
            currZ += stepZ

            val vx = currX.toInt()
            val vy = currY.toInt()
            val vz = currZ.toInt()

            if (vx == pX && vy == pY && vz == pZ) return true

            val voxel = dag.getVoxel(vx, vy, vz)
            if (voxel.isSolid) {
                return false // Solid firewall wall blocks ICE line of sight!
            }
        }
        return true
    }

    /**
     * Is the voxel navigable for ICE entity pathfinding along hallways?
     */
    fun isNavigableVoxel(voxel: VoxelType): Boolean {
        return when (voxel) {
            VoxelType.PATH,
            VoxelType.GRAND_HALL,
            VoxelType.STAIRS,
            VoxelType.ELEVATOR,
            VoxelType.GRAVITY_SLOPE,
            VoxelType.ELEVATED_BALCONY,
            VoxelType.BLACK_ICE,
            VoxelType.ICE_PATROL,
            VoxelType.SAFE_ZONE -> true

            // Vents are restricted to player stealth bypasses! ICE cannot enter tight vents!
            else -> false
        }
    }

    /**
     * A* 3D Pathfinding algorithm for navigating SVDAG hallway networks.
     */
    fun findHallwayPath(
        start: Triple<Int, Int, Int>,
        goal: Triple<Int, Int, Int>,
        dag: SparseVoxelDag,
        maxSearchNodes: Int = 300
    ): List<Triple<Int, Int, Int>> {
        if (start == goal) return listOf(start)

        val openSet = PriorityQueue<PathNode>()
        val gScores = mutableMapOf<Triple<Int, Int, Int>, Int>()
        val closedSet = mutableSetOf<Triple<Int, Int, Int>>()

        fun heuristic(p1: Triple<Int, Int, Int>, p2: Triple<Int, Int, Int>): Int {
            return abs(p1.first - p2.first) + abs(p1.second - p2.second) + abs(p1.third - p2.third)
        }

        val startNode = PathNode(start, 0, heuristic(start, goal))
        openSet.add(startNode)
        gScores[start] = 0

        var nodesSearched = 0

        val neighbors = listOf(
            Triple(-1, 0, 0), Triple(1, 0, 0),
            Triple(0, -1, 0), Triple(0, 1, 0),
            Triple(0, 0, -1), Triple(0, 0, 1)
        )

        while (openSet.isNotEmpty() && nodesSearched < maxSearchNodes) {
            nodesSearched++
            val current = openSet.poll() ?: break

            if (current.pos == goal) {
                val path = mutableListOf<Triple<Int, Int, Int>>()
                var temp: PathNode? = current
                while (temp != null) {
                    path.add(temp.pos)
                    temp = temp.parent
                }
                return path.reversed()
            }

            closedSet.add(current.pos)

            for (dir in neighbors) {
                val nx = current.pos.first + dir.first
                val ny = current.pos.second + dir.second
                val nz = current.pos.third + dir.third
                val neighborPos = Triple(nx, ny, nz)

                if (nx !in 0 until dag.gridSize || ny !in 0 until dag.gridSize || nz !in 0 until dag.gridSize) continue
                if (closedSet.contains(neighborPos)) continue

                val voxel = dag.getVoxel(nx, ny, nz)
                if (!isNavigableVoxel(voxel)) continue

                val tentativeG = current.gScore + 1
                val existingG = gScores[neighborPos] ?: Int.MAX_VALUE

                if (tentativeG < existingG) {
                    gScores[neighborPos] = tentativeG
                    val f = tentativeG + heuristic(neighborPos, goal)
                    val neighborNode = PathNode(neighborPos, tentativeG, f, current)
                    openSet.add(neighborNode)
                }
            }
        }

        // Return empty path if unreachable
        return emptyList()
    }

    /**
     * Executes one AI tick turn for an ICE entity.
     */
    fun tickIceEntity(
        ice: IceEntity,
        playerPos: Triple<Int, Int, Int>,
        dag: SparseVoxelDag,
        maze: Array<Array<CellType>>? = null
    ): IcePathStepResult {
        val hideStatus = evaluatePlayerHidingStatus(playerPos.first, playerPos.second, playerPos.third, dag, maze)

        val icePos = Triple(ice.x, ice.y, ice.z)
        val distToPlayer = abs(ice.x - playerPos.first) + abs(ice.y - playerPos.second) + abs(ice.z - playerPos.third)

        var newAlertLevel = ice.alertLevel
        var alertChanged = false
        var actionLog = ""
        var path: List<Triple<Int, Int, Int>> = emptyList()

        val canSeePlayer = !hideStatus.isHidden &&
                distToPlayer <= ice.type.detectionRadius &&
                hasLineOfSight(ice.x, ice.y, ice.z, playerPos.first, playerPos.second, playerPos.third, dag)

        // 1. STATE TRANSITION DECISION
        if (canSeePlayer) {
            if (ice.alertLevel != IceAlertLevel.HUNTING) {
                newAlertLevel = IceAlertLevel.HUNTING
                alertChanged = true
            }
            ice.lastKnownPlayerPos = playerPos
        } else if (hideStatus.isHidden) {
            if (ice.alertLevel == IceAlertLevel.HUNTING) {
                newAlertLevel = IceAlertLevel.SUSPICIOUS
                alertChanged = true
                actionLog = "🙈 ICE LOST LOCK: Player concealed in vent/safe zone! Investigating last known hallway position."
            }
        }

        ice.alertLevel = newAlertLevel

        // 2. MOVEMENT & PATHFINDING BEHAVIOR
        when (ice.alertLevel) {
            IceAlertLevel.HUNTING -> {
                // Hunt player directly along hallway A* path
                path = findHallwayPath(icePos, playerPos, dag)
                if (path.size > 1) {
                    val stepsToTake = min(ice.type.speed, path.size - 1)
                    val nextPos = path[stepsToTake]
                    ice.facingDx = nextPos.first - ice.x
                    ice.facingDy = nextPos.second - ice.y
                    ice.facingDz = nextPos.third - ice.z
                    ice.x = nextPos.first
                    ice.y = nextPos.second
                    ice.z = nextPos.third
                    ice.stepCount += stepsToTake
                    actionLog = "🚨 ICE [${ice.id}] HUNTING: Pursuing player along hallway path (Step ${ice.stepCount}, Dist: $distToPlayer voxels)"
                } else {
                    actionLog = "🚨 ICE [${ice.id}] LOCK ON: Player in direct vicinity!"
                }
            }

            IceAlertLevel.SUSPICIOUS -> {
                val target = ice.lastKnownPlayerPos
                if (target != null && target != icePos) {
                    path = findHallwayPath(icePos, target, dag)
                    if (path.size > 1) {
                        val nextPos = path[1]
                        ice.x = nextPos.first
                        ice.y = nextPos.second
                        ice.z = nextPos.third
                        actionLog = "🟡 ICE [${ice.id}] SEARCHING: Sweeping last known hallway sector at (${target.first}, ${target.second})"
                    } else {
                        // Reached last known position, reset to patrol!
                        ice.lastKnownPlayerPos = null
                        ice.alertLevel = IceAlertLevel.PATROL
                        alertChanged = true
                        actionLog = "🟢 ICE [${ice.id}] SEARCH COMPLETE: Clear sector. Resuming hallway patrol route."
                    }
                } else {
                    ice.lastKnownPlayerPos = null
                    ice.alertLevel = IceAlertLevel.PATROL
                    alertChanged = true
                    actionLog = "🟢 ICE [${ice.id}]: Resuming normal patrol route."
                }
            }

            IceAlertLevel.PATROL -> {
                if (ice.patrolRoute.isNotEmpty()) {
                    var targetWaypoint = ice.patrolRoute[ice.waypointIndex]
                    if (Triple(ice.x, ice.y, ice.z) == targetWaypoint) {
                        ice.waypointIndex = (ice.waypointIndex + 1) % ice.patrolRoute.size
                        targetWaypoint = ice.patrolRoute[ice.waypointIndex]
                    }

                    path = findHallwayPath(icePos, targetWaypoint, dag)
                    if (path.size > 1) {
                        val nextPos = path[1]
                        ice.facingDx = nextPos.first - ice.x
                        ice.facingDy = nextPos.second - ice.y
                        ice.facingDz = nextPos.third - ice.z
                        ice.x = nextPos.first
                        ice.y = nextPos.second
                        ice.z = nextPos.third
                        ice.stepCount++
                        actionLog = "🟢 ICE [${ice.id}] PATROLLING: Moving toward Waypoint #${ice.waypointIndex} (${targetWaypoint.first}, ${targetWaypoint.second})"
                    }
                } else {
                    actionLog = "🟢 ICE [${ice.id}] PATROLLING: Standing guard at (${ice.x}, ${ice.y}, ${ice.z})"
                }
            }
        }

        val intercepted = (ice.x == playerPos.first && ice.y == playerPos.second && ice.z == playerPos.third)

        return IcePathStepResult(
            updatedIce = ice,
            actionMessage = actionLog,
            alertChanged = alertChanged,
            interceptedPlayer = intercepted,
            pathTaken = path
        )
    }

    /**
     * Generates 3-4 initial ICE entities with pre-calculated hallway patrol routes across SVDAG volume.
     */
    fun generateDefaultPatrolEntities(dag: SparseVoxelDag): List<IceEntity> {
        val entities = mutableListOf<IceEntity>()
        val size = dag.gridSize
        val midZ = size / 2

        val hallwayPositions = mutableListOf<Triple<Int, Int, Int>>()
        for (y in 2 until size - 2 step 2) {
            for (x in 2 until size - 2 step 2) {
                if (dag.getVoxel(x, y, midZ) == VoxelType.PATH) {
                    hallwayPositions.add(Triple(x, y, midZ))
                }
            }
        }

        if (hallwayPositions.size >= 4) {
            val route1 = listOf(
                hallwayPositions[0],
                hallwayPositions[1],
                hallwayPositions[0]
            )
            entities.add(
                IceEntity(
                    id = "ICE-01",
                    name = "Alpha Security Unit",
                    x = hallwayPositions[0].first,
                    y = hallwayPositions[0].second,
                    z = hallwayPositions[0].third,
                    type = IceType.PATROL_ICE,
                    patrolRoute = route1
                )
            )

            val route2 = listOf(
                hallwayPositions[hallwayPositions.size - 1],
                hallwayPositions[hallwayPositions.size - 2],
                hallwayPositions[hallwayPositions.size - 1]
            )
            entities.add(
                IceEntity(
                    id = "ICE-02",
                    name = "Beta Hunter Daemon",
                    x = hallwayPositions[hallwayPositions.size - 1].first,
                    y = hallwayPositions[hallwayPositions.size - 1].second,
                    z = hallwayPositions[hallwayPositions.size - 1].third,
                    type = IceType.BLACK_ICE,
                    patrolRoute = route2
                )
            )
        } else {
            val defaultPos = Triple(size / 4, size / 4, midZ)
            val defaultGoal = Triple(3 * size / 4, 3 * size / 4, midZ)
            entities.add(
                IceEntity(
                    id = "ICE-01",
                    name = "Corridor Trace Daemon",
                    x = defaultPos.first,
                    y = defaultPos.second,
                    z = defaultPos.third,
                    type = IceType.PATROL_ICE,
                    patrolRoute = listOf(defaultPos, defaultGoal, defaultPos)
                )
            )
        }

        return entities
    }
}
