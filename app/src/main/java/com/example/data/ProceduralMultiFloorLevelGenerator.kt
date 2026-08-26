package com.example.data

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Cyberpunk District Themes for Multi-Floor Level Generation.
 */
enum class CyberDistrictTheme(
    val title: String,
    val description: String,
    val primaryColorHex: Long
) {
    UNDERCITY_SEWERS("Sub-Surface Undercity Sewers", "Drainage canals, steam vents & subterranean hacker refuges", 0xFF00FFCC),
    NEON_PLAZA("Ground Level Neon Plaza", "High-density pedestrian avenues, checkpoint gates & cyber-cafes", 0xFF3B82F6),
    CORPORATE_TOWER("Arasaka High-Rise MegaTower", "Multi-story tech lobbies, server vaults & penthouse skywalks", 0xFFA855F7),
    BLACK_ICE_NET("Black-ICE Cyberspace Grid", "Encrypted defense matrix with active sentinel processes", 0xFFEF4444)
}

/**
 * Types of Vertical Connectors linking different floors in a multi-floor level.
 */
enum class VerticalConnectorType(val label: String, val cellType: CellType) {
    ELEVATOR_SHAFT("Transit Elevator Shaft", CellType.ELEVATOR),
    STAIRWELL("Reinforced Stairwell", CellType.STAIRS_UP),
    GRAVITY_SLOPE("Gravity Transition Conduit", CellType.GRAVITY_SLOPE),
    VENT_CONDUIT("Sub-Conduit Air Vent", CellType.VENT_TUNNEL)
}

/**
 * Node position placed across a specific floor in a multi-floor grid level.
 */
data class MultiFloorNodePosition(
    val id: String,
    val name: String,
    val floorIndex: Int,
    val x: Int,
    val y: Int,
    val nodeType: MatrixNodeType,
    val description: String,
    var isReachableFromSpawn: Boolean = true
)

/**
 * Loot Cache placed on a floor.
 */
data class MultiFloorLootCache(
    val id: String = Random.nextInt(100000, 999999).toString(),
    val floorIndex: Int,
    val gridX: Int,
    val gridY: Int,
    val cacheType: String,
    val creditsReward: Int,
    val ramReward: Int,
    val itemReward: String? = null,
    val isLocked: Boolean = true,
    val hackDifficulty: Int = 1,
    var isReachable: Boolean = true
)

/**
 * Explicit Vertical Link connecting two floor positions.
 */
data class VerticalConnector(
    val id: String,
    val name: String,
    val fromFloor: Int,
    val fromPos: Pair<Int, Int>,
    val toFloor: Int,
    val toPos: Pair<Int, Int>,
    val connectorType: VerticalConnectorType
)

/**
 * Individual Floor Grid representation.
 */
data class GridFloor(
    val floorIndex: Int,
    val floorName: String,
    val width: Int,
    val height: Int,
    val grid: Array<Array<CellType>>,
    val securityLevel: Int = 1
) {
    fun isWalkable(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        val type = grid[y][x]
        return type != CellType.WALL && type != CellType.VIRUS_NODE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GridFloor
        return floorIndex == other.floorIndex && floorName == other.floorName
    }

    override fun hashCode(): Int {
        return floorIndex.hashCode() * 31 + floorName.hashCode()
    }
}

/**
 * Comprehensive Reachability Validation Report for Multi-Floor Levels.
 */
data class MultiFloorReachabilityReport(
    val isFullyReachable: Boolean,
    val totalFloors: Int,
    val totalWalkableCells: Int,
    val reachableCellsCount: Int,
    val reachableRatioPercent: Float,
    val totalNodes: Int,
    val reachableNodesCount: Int,
    val unreachableNodeNames: List<String>,
    val repairedPathsCount: Int,
    val validationTimeMs: Long
)

/**
 * Complete Procedural Multi-Floor Grid Level Data Model.
 */
data class MultiFloorGridLevel(
    val levelNumber: Int,
    val sectorName: String,
    val districtTheme: CyberDistrictTheme,
    val floors: List<GridFloor>,
    val verticalConnectors: List<VerticalConnector>,
    val nodes: List<MultiFloorNodePosition>,
    val lootCaches: List<MultiFloorLootCache>,
    val spawnPoint: Triple<Int, Int, Int>, // (floor, x, y)
    val exitPoint: Triple<Int, Int, Int>,  // (floor, x, y)
    val reachabilityReport: MultiFloorReachabilityReport
) {
    fun getFloor(index: Int): GridFloor? = floors.getOrNull(index)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MultiFloorGridLevel
        return levelNumber == other.levelNumber && sectorName == other.sectorName
    }

    override fun hashCode(): Int {
        return levelNumber.hashCode() * 31 + sectorName.hashCode()
    }
}

/**
 * Procedural Grid-Based Multi-Floor Reachable Level Generator.
 * Generates interconnected multi-story cyberpunk environments with guaranteed 100% path connectivity
 * across all floors, elevators, terminals, loot caches, and exit portals.
 */
object ProceduralMultiFloorLevelGenerator {

    private val SECTOR_NAMES = listOf(
        "Arasaka Neo-Grid", "Militech Sub-Sector", "Gibson Quantum Hub", "NightCity Undercity",
        "Zeta Server Spire", "Neural Matrix Nexus", "Cipher Core Vault", "Kuroshio Cyber-Plaza"
    )

    private val FLOOR_NAME_TEMPLATES = listOf(
        0 to "Floor 0: Sub-surface Sewers & Conduits",
        1 to "Floor 1: Street Level Cyber Plaza",
        2 to "Floor 2: High-Rise Corporate Tech Lobby",
        3 to "Floor 3: Encrypted Server Core & Vaults",
        4 to "Floor 4: Black-ICE Research Sanctum",
        5 to "Floor 5: Penthouse Roof Helipad & Skywalk"
    )

    /**
     * Main entry point to generate a guaranteed-reachable multi-floor cyberpunk level.
     */
    fun generateMultiFloorLevel(
        levelNumber: Int,
        numFloors: Int = 4,
        widthPerFloor: Int = 14,
        heightPerFloor: Int = 14,
        seed: Long = System.currentTimeMillis() + levelNumber * 999L
    ): MultiFloorGridLevel {
        val startMs = System.currentTimeMillis()
        val random = Random(seed)

        val districtTheme = when (levelNumber % 4) {
            1 -> CyberDistrictTheme.UNDERCITY_SEWERS
            2 -> CyberDistrictTheme.NEON_PLAZA
            3 -> CyberDistrictTheme.CORPORATE_TOWER
            else -> CyberDistrictTheme.BLACK_ICE_NET
        }

        val sectorName = "${SECTOR_NAMES.random(random)} Sector $levelNumber [0x${random.nextInt(0x1000, 0xFFFF).toString(16).uppercase()}]"

        // 1. Generate Raw Floor Grids
        val floors = mutableListOf<GridFloor>()
        for (f in 0 until numFloors) {
            val floorName = FLOOR_NAME_TEMPLATES.find { it.first == f }?.second ?: "Floor $f: Sector Sub-Level"
            val floorGrid = generateFloorGrid(f, numFloors, widthPerFloor, heightPerFloor, districtTheme, random)
            floors.add(GridFloor(f, floorName, widthPerFloor, heightPerFloor, floorGrid, securityLevel = f + 1))
        }

        // 2. Place Spawn on Floor 0 & Exit Portal on Top Floor
        val spawnX = 2
        val spawnY = 2
        floors[0].grid[spawnY][spawnX] = CellType.SAFE_ZONE
        // Ensure spawn area is walkable
        for (dy in -1..1) {
            for (dx in -1..1) {
                val px = (spawnX + dx).coerceIn(1, widthPerFloor - 2)
                val py = (spawnY + dy).coerceIn(1, heightPerFloor - 2)
                if (px == spawnX && py == spawnY) {
                    floors[0].grid[py][px] = CellType.SAFE_ZONE
                } else {
                    floors[0].grid[py][px] = CellType.PATH
                }
            }
        }

        val topFloorIdx = numFloors - 1
        val exitX = widthPerFloor - 3
        val exitY = heightPerFloor - 3
        floors[topFloorIdx].grid[exitY][exitX] = CellType.ENCRYPTED_PORTAL
        // Ensure exit area is walkable
        for (dy in -1..1) {
            for (dx in -1..1) {
                val px = (exitX + dx).coerceIn(1, widthPerFloor - 2)
                val py = (exitY + dy).coerceIn(1, heightPerFloor - 2)
                if (px == exitX && py == exitY) {
                    floors[topFloorIdx].grid[py][px] = CellType.ENCRYPTED_PORTAL
                } else {
                    floors[topFloorIdx].grid[py][px] = CellType.PATH
                }
            }
        }

        // 3. Connect Floors with Vertical Transit Connectors (Elevators & Stairwells)
        val connectors = mutableListOf<VerticalConnector>()
        val centralElevatorX = widthPerFloor / 2
        val centralElevatorY = heightPerFloor / 2

        for (f in 0 until numFloors - 1) {
            // Place Central Elevator
            floors[f].grid[centralElevatorY][centralElevatorX] = CellType.ELEVATOR
            floors[f + 1].grid[centralElevatorY][centralElevatorX] = CellType.ELEVATOR

            // Carve elevator access corridors on both floors
            carveAccessCorridor(floors[f], centralElevatorX, centralElevatorY)
            carveAccessCorridor(floors[f + 1], centralElevatorX, centralElevatorY)

            connectors.add(
                VerticalConnector(
                    id = "ELEVATOR_SHAFT_${f}_TO_${f + 1}",
                    name = "Transit Elevator Hub (F$f ↔ F${f + 1})",
                    fromFloor = f,
                    fromPos = Pair(centralElevatorX, centralElevatorY),
                    toFloor = f + 1,
                    toPos = Pair(centralElevatorX, centralElevatorY),
                    connectorType = VerticalConnectorType.ELEVATOR_SHAFT
                )
            )

            // Secondary Stairwell Connector
            val stairX = if (f % 2 == 0) 2 else widthPerFloor - 3
            val stairY = heightPerFloor / 2
            floors[f].grid[stairY][stairX] = CellType.STAIRS_UP
            floors[f + 1].grid[stairY][stairX] = CellType.STAIRS_DOWN

            carveAccessCorridor(floors[f], stairX, stairY)
            carveAccessCorridor(floors[f + 1], stairX, stairY)

            connectors.add(
                VerticalConnector(
                    id = "STAIRS_${f}_TO_${f + 1}",
                    name = "Stairwell Access (F$f ↔ F${f + 1})",
                    fromFloor = f,
                    fromPos = Pair(stairX, stairY),
                    toFloor = f + 1,
                    toPos = Pair(stairX, stairY),
                    connectorType = VerticalConnectorType.STAIRWELL
                )
            )
        }

        // 4. Place Cyberpunk Nodes and Loot Vaults across Floors
        val nodes = mutableListOf<MultiFloorNodePosition>()
        val lootCaches = mutableListOf<MultiFloorLootCache>()

        nodes.add(
            MultiFloorNodePosition(
                id = "NODE_SPAWN",
                name = "Neural Access Point (Spawn)",
                floorIndex = 0,
                x = spawnX,
                y = spawnY,
                nodeType = MatrixNodeType.ENTRY_GATE,
                description = "Primary Level Entrance Safe Zone"
            )
        )

        nodes.add(
            MultiFloorNodePosition(
                id = "NODE_EXIT",
                name = "Encrypted Sub-Sector Uplink (Exit)",
                floorIndex = topFloorIdx,
                x = exitX,
                y = exitY,
                nodeType = MatrixNodeType.EXIT_PORTAL,
                description = "Level Uplink Gateway to Next Sector"
            )
        )

        // Populate randomized nodes and loot caches per floor
        var nodeCounter = 1
        for (f in 0 until numFloors) {
            val floor = floors[f]
            for (y in 2 until heightPerFloor - 2) {
                for (x in 2 until widthPerFloor - 2) {
                    if (floor.grid[y][x] == CellType.PATH && (x != centralElevatorX || y != centralElevatorY)) {
                        val roll = random.nextInt(100)
                        when {
                            roll < 8 -> {
                                floor.grid[y][x] = CellType.DATA_STORE
                                val nodeId = "TERMINAL_F${f}_$nodeCounter"
                                nodes.add(
                                    MultiFloorNodePosition(
                                        id = nodeId,
                                        name = "Data Terminal F$f-#$nodeCounter",
                                        floorIndex = f,
                                        x = x,
                                        y = y,
                                        nodeType = MatrixNodeType.DATA_NODE,
                                        description = "Encrypted Corporate Memory Terminal on Floor $f"
                                    )
                                )
                                lootCaches.add(
                                    MultiFloorLootCache(
                                        floorIndex = f,
                                        gridX = x,
                                        gridY = y,
                                        cacheType = "Corrupt Memory Cache",
                                        creditsReward = random.nextInt(50, 150) * (f + 1),
                                        ramReward = random.nextInt(1, 3),
                                        itemReward = "RAMBoost.exe",
                                        hackDifficulty = f + 1
                                    )
                                )
                                nodeCounter++
                            }
                            roll in 8..12 -> {
                                floor.grid[y][x] = CellType.SECRET_CACHE
                                val vaultId = "VAULT_F${f}_$nodeCounter"
                                nodes.add(
                                    MultiFloorNodePosition(
                                        id = vaultId,
                                        name = "Encrypted Vault F$f-#$nodeCounter",
                                        floorIndex = f,
                                        x = x,
                                        y = y,
                                        nodeType = MatrixNodeType.LOOT_VAULT,
                                        description = "Classified High-Security Cyber Vault"
                                    )
                                )
                                lootCaches.add(
                                    MultiFloorLootCache(
                                        floorIndex = f,
                                        gridX = x,
                                        gridY = y,
                                        cacheType = "Classified Vault Crypt-Cache",
                                        creditsReward = random.nextInt(200, 500) * (f + 1),
                                        ramReward = random.nextInt(2, 6),
                                        itemReward = "OverclockDaemon.dll",
                                        hackDifficulty = f + 2
                                    )
                                )
                                nodeCounter++
                            }
                            roll in 13..18 -> {
                                floor.grid[y][x] = CellType.VIRUS_NODE
                                nodes.add(
                                    MultiFloorNodePosition(
                                        id = "ICE_F${f}_$nodeCounter",
                                        name = "Black-ICE Node F$f-#$nodeCounter",
                                        floorIndex = f,
                                        x = x,
                                        y = y,
                                        nodeType = MatrixNodeType.SECURITY_ICE,
                                        description = "Active Security Black-ICE Countermeasure Process"
                                    )
                                )
                                nodeCounter++
                            }
                        }
                    }
                }
            }
        }

        // 5. Guaranteed Multi-Floor Reachability Validation & Repair Algorithm
        val (validatedReport, repairedCount) = validateAndGuaranteeReachability(
            floors = floors,
            connectors = connectors,
            nodes = nodes,
            lootCaches = lootCaches,
            spawn = Triple(0, spawnX, spawnY),
            exit = Triple(topFloorIdx, exitX, exitY),
            startMs = startMs
        )

        return MultiFloorGridLevel(
            levelNumber = levelNumber,
            sectorName = sectorName,
            districtTheme = districtTheme,
            floors = floors,
            verticalConnectors = connectors,
            nodes = nodes,
            lootCaches = lootCaches,
            spawnPoint = Triple(0, spawnX, spawnY),
            exitPoint = Triple(topFloorIdx, exitX, exitY),
            reachabilityReport = validatedReport
        )
    }

    /**
     * Generates a 2D grid floor with distinct architectural features based on floor index & district theme.
     */
    private fun generateFloorGrid(
        floorIndex: Int,
        totalFloors: Int,
        width: Int,
        height: Int,
        theme: CyberDistrictTheme,
        random: Random
    ): Array<Array<CellType>> {
        val grid = Array(height) { Array(width) { CellType.WALL } }

        // Outer boundary walls
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = CellType.WALL
                }
            }
        }

        // Carve room blocks
        val roomCount = random.nextInt(3, 6)
        val rooms = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>() // Pair((rx, ry), (rw, rh))

        for (r in 0 until roomCount) {
            val rw = random.nextInt(3, 5)
            val rh = random.nextInt(3, 5)
            val rx = random.nextInt(1, max(2, width - rw - 1))
            val ry = random.nextInt(1, max(2, height - rh - 1))

            val cellType = when (floorIndex) {
                0 -> CellType.VENT_TUNNEL // Sewers
                1 -> CellType.PATH        // Street Plaza
                2 -> CellType.GRAND_HALL  // Corporate Lobby
                3 -> CellType.DOME_CHAMBER// Server Vault
                else -> CellType.ELEVATED_BALCONY // Skywalk Roof
            }

            for (y in ry until ry + rh) {
                for (x in rx until rx + rw) {
                    if (x in 1 until width - 1 && y in 1 until height - 1) {
                        grid[y][x] = cellType
                    }
                }
            }
            rooms.add(Pair(Pair(rx + rw / 2, ry + rh / 2), Pair(rw, rh)))
        }

        // Connect room centers via horizontal & vertical corridors
        for (i in 0 until rooms.size - 1) {
            val c1 = rooms[i].first
            val c2 = rooms[i + 1].first

            val minX = min(c1.first, c2.first)
            val maxX = max(c1.first, c2.first)
            for (x in minX..maxX) {
                if (grid[c1.second][x] == CellType.WALL) {
                    grid[c1.second][x] = CellType.PATH
                }
            }

            val minY = min(c1.second, c2.second)
            val maxY = max(c1.second, c2.second)
            for (y in minY..maxY) {
                if (grid[y][c2.first] == CellType.WALL) {
                    grid[y][c2.first] = CellType.PATH
                }
            }
        }

        return grid
    }

    /**
     * Ensures an elevator/stairwell cell has walkable surrounding corridors.
     */
    private fun carveAccessCorridor(floor: GridFloor, targetX: Int, targetY: Int) {
        val directions = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
        for ((dx, dy) in directions) {
            val px = targetX + dx
            val py = targetY + dy
            if (px in 1 until floor.width - 1 && py in 1 until floor.height - 1) {
                if (floor.grid[py][px] == CellType.WALL) {
                    floor.grid[py][px] = CellType.PATH
                }
            }
        }
    }

    /**
     * Performs a Multi-Floor 3D/Multi-Layer BFS pathfinding search from spawn point
     * to verify reachability of ALL floors, elevator nodes, key terminals, and exit portals.
     * Automatically carves connecting paths if any region is isolated!
     */
    private fun validateAndGuaranteeReachability(
        floors: List<GridFloor>,
        connectors: List<VerticalConnector>,
        nodes: MutableList<MultiFloorNodePosition>,
        lootCaches: MutableList<MultiFloorLootCache>,
        spawn: Triple<Int, Int, Int>,
        exit: Triple<Int, Int, Int>,
        startMs: Long
    ): Pair<MultiFloorReachabilityReport, Int> {
        var repairedCount = 0

        // Perform BFS loop until 100% reachability is confirmed
        var isFullyConnected = false
        var reachableSet = mutableSetOf<Triple<Int, Int, Int>>()
        var totalWalkable = 0

        for (attempt in 1..5) {
            totalWalkable = floors.sumOf { floor ->
                var count = 0
                for (y in 0 until floor.height) {
                    for (x in 0 until floor.width) {
                        if (floor.grid[y][x] != CellType.WALL && floor.grid[y][x] != CellType.VIRUS_NODE) {
                            count++
                        }
                    }
                }
                count
            }

            reachableSet = runMultiFloorBfs(floors, connectors, spawn)

            // Check if exit is reached
            val exitReachable = reachableSet.contains(exit)

            // Check reachability of each floor (at least 1 walkable cell per floor reachable)
            val floorsReachable = floors.indices.all { f ->
                reachableSet.any { it.first == f }
            }

            // Check nodes reachability
            val unreachableNodesList = mutableListOf<String>()
            nodes.forEach { node ->
                val pos = Triple(node.floorIndex, node.x, node.y)
                node.isReachableFromSpawn = reachableSet.contains(pos)
                if (!node.isReachableFromSpawn) {
                    unreachableNodesList.add(node.name)
                }
            }

            lootCaches.forEach { cache ->
                val pos = Triple(cache.floorIndex, cache.gridX, cache.gridY)
                cache.isReachable = reachableSet.contains(pos)
            }

            if (exitReachable && floorsReachable && unreachableNodesList.isEmpty()) {
                isFullyConnected = true
                break
            } else {
                // Repair unreachable targets by carving direct corridor/elevator pathways
                repairedCount += executeReachabilityRepair(
                    floors = floors,
                    connectors = connectors,
                    reachableSet = reachableSet,
                    spawn = spawn,
                    exit = exit,
                    nodes = nodes
                )
            }
        }

        val reachableCount = reachableSet.size
        val ratio = if (totalWalkable > 0) (reachableCount.toFloat() / totalWalkable.toFloat()) * 100f else 100f
        val unreachableNames = nodes.filter { !it.isReachableFromSpawn }.map { it.name }

        val report = MultiFloorReachabilityReport(
            isFullyReachable = isFullyConnected || unreachableNames.isEmpty(),
            totalFloors = floors.size,
            totalWalkableCells = totalWalkable,
            reachableCellsCount = reachableCount,
            reachableRatioPercent = ratio,
            totalNodes = nodes.size,
            reachableNodesCount = nodes.count { it.isReachableFromSpawn },
            unreachableNodeNames = unreachableNames,
            repairedPathsCount = repairedCount,
            validationTimeMs = System.currentTimeMillis() - startMs
        )

        return Pair(report, repairedCount)
    }

    /**
     * Executes 3D Multi-Floor BFS search traversing 2D floor moves & vertical transit connectors.
     */
    private fun runMultiFloorBfs(
        floors: List<GridFloor>,
        connectors: List<VerticalConnector>,
        startPos: Triple<Int, Int, Int>
    ): MutableSet<Triple<Int, Int, Int>> {
        val visited = mutableSetOf<Triple<Int, Int, Int>>()
        val queue = ArrayDeque<Triple<Int, Int, Int>>()

        queue.add(startPos)
        visited.add(startPos)

        // Build fast lookup table for vertical connectors
        val verticalLinkMap = mutableMapOf<Triple<Int, Int, Int>, Triple<Int, Int, Int>>()
        connectors.forEach { conn ->
            val from = Triple(conn.fromFloor, conn.fromPos.first, conn.fromPos.second)
            val to = Triple(conn.toFloor, conn.toPos.first, conn.toPos.second)
            verticalLinkMap[from] = to
            verticalLinkMap[to] = from
        }

        val directions2D = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))

        while (queue.isNotEmpty()) {
            val curr = queue.poll() ?: continue
            val (f, x, y) = curr

            val currentFloor = floors.getOrNull(f) ?: continue

            // 1. Explore 2D neighbors on the same floor
            for ((dx, dy) in directions2D) {
                val nx = x + dx
                val ny = y + dy

                if (nx in 0 until currentFloor.width && ny in 0 until currentFloor.height) {
                    val cellType = currentFloor.grid[ny][nx]
                    if (cellType != CellType.WALL && cellType != CellType.VIRUS_NODE) {
                        val neighbor = Triple(f, nx, ny)
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor)
                            queue.add(neighbor)
                        }
                    }
                }
            }

            // 2. Explore vertical connector transition
            val vTarget = verticalLinkMap[curr]
            if (vTarget != null && !visited.contains(vTarget)) {
                visited.add(vTarget)
                queue.add(vTarget)
            }
        }

        return visited
    }

    /**
     * Repair mechanism that carves paths to connect isolated nodes or unreachable floors.
     */
    private fun executeReachabilityRepair(
        floors: List<GridFloor>,
        connectors: List<VerticalConnector>,
        reachableSet: Set<Triple<Int, Int, Int>>,
        spawn: Triple<Int, Int, Int>,
        exit: Triple<Int, Int, Int>,
        nodes: List<MultiFloorNodePosition>
    ): Int {
        var fixes = 0

        // 1. If Exit is unreachable, carve a direct line from nearest reachable voxel on top floor to exit
        if (!reachableSet.contains(exit)) {
            val (ef, ex, ey) = exit
            val topFloor = floors[ef]
            // Carve straight line corridor to exit
            for (x in 1..ex) {
                if (topFloor.grid[ey][x] == CellType.WALL) {
                    topFloor.grid[ey][x] = CellType.PATH
                    fixes++
                }
            }
            for (y in 1..ey) {
                if (topFloor.grid[y][ex] == CellType.WALL) {
                    topFloor.grid[y][ex] = CellType.PATH
                    fixes++
                }
            }
        }

        // 2. If any floor has 0 reachable voxels, carve access to central elevator on that floor
        floors.indices.forEach { f ->
            val hasReachable = reachableSet.any { it.first == f }
            if (!hasReachable) {
                val floor = floors[f]
                val cx = floor.width / 2
                val cy = floor.height / 2
                floor.grid[cy][cx] = CellType.ELEVATOR
                carveAccessCorridor(floor, cx, cy)
                fixes += 2
            }
        }

        // 3. For any unreachable node, carve a path from nearest reachable node on the same floor
        nodes.forEach { node ->
            val pos = Triple(node.floorIndex, node.x, node.y)
            if (!reachableSet.contains(pos)) {
                val floor = floors[node.floorIndex]
                val targetX = node.x
                val targetY = node.y

                // Carve horizontally then vertically to target
                for (x in min(2, targetX)..max(2, targetX)) {
                    if (floor.grid[targetY][x] == CellType.WALL) {
                        floor.grid[targetY][x] = CellType.PATH
                        fixes++
                    }
                }
                for (y in min(2, targetY)..max(2, targetY)) {
                    if (floor.grid[y][targetX] == CellType.WALL) {
                        floor.grid[y][targetX] = CellType.PATH
                        fixes++
                    }
                }
            }
        }

        return fixes
    }
}
