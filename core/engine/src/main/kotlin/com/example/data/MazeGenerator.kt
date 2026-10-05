package com.example.data

import kotlin.math.abs
import kotlin.random.Random

/**
 * Deterministic procedural generator for the primary Labyrinth map.
 *
 * This is intentionally a mechanical extraction from GameEngine. Algorithm cleanup belongs
 * in later commits so seeded map behavior remains stable during the architecture migration.
 */
object MazeGenerator {
    fun generate(
            width: Int = 10,
            height: Int = 10,
            layer: Int = 1,
            seed: Long = System.currentTimeMillis() + layer * 123L
        ): Array<Array<CellType>> {
            val seedRandom = Random(seed)
            var grid = Array(height) { Array(width) { CellType.WALL } }
            for (attempt in 1..3) {
                grid = Array(height) { Array(width) { CellType.WALL } }
                val random = Random(seedRandom.nextInt())
                val walkableCells = mutableSetOf<Pair<Int, Int>>()
    
                // Helper to safely carve cells
                fun carveCell(x: Int, y: Int, type: CellType) {
                    if (x in 1 until width - 1 && y in 1 until height - 1) {
                        grid[y][x] = type
                        if (type != CellType.WALL) {
                            walkableCells.add(Pair(x, y))
                        } else {
                            walkableCells.remove(Pair(x, y))
                        }
                    }
                }
    
                // Keep track of placed architectural block rooms
                data class DungeonBlock(
                    val x: Int,
                    val y: Int,
                    val w: Int,
                    val h: Int,
                    val type: String,
                    val carvedCells: MutableList<Pair<Int, Int>> = mutableListOf()
                ) {
                    val centerX get() = x + w / 2
                    val centerY get() = y + h / 2
                }
                val blocks = mutableListOf<DungeonBlock>()
    
                // 1. Generate diverse architectural forms (Spacious rooms requiring 8-12 steps to cross!)
                val numAttempts = 12 + (width * height) / 150
                for (i in 0 until numAttempts) {
                    val maxBw = minOf(12, width - 2).coerceAtLeast(8)
                    val maxBh = minOf(12, height - 2).coerceAtLeast(8)
                    val bw = if (maxBw > 8) 8 + random.nextInt(maxBw - 7) else 8
                    val bh = if (maxBh > 8) 8 + random.nextInt(maxBh - 7) else 8
                    val xRange = width - bw - 1
                    val yRange = height - bh - 1
                    val bx = 1 + (if (xRange > 0) random.nextInt(xRange) else 0)
                    val by = 1 + (if (yRange > 0) random.nextInt(yRange) else 0)
    
                    val blockType = when (random.nextInt(6)) {
                        0 -> "GRAND_HALL"
                        1 -> "DOME_CHAMBER"
                        2 -> "ELEVATED_BALCONY"
                        3 -> "VENT_TUNNEL"
                        4 -> "GRAVITY_SLOPE_ROOM"
                        else -> "STAIRCASE_HUB"
                    }
    
                    val block = DungeonBlock(bx, by, bw, bh, blockType)
                    blocks.add(block)
    
                    // Carve specifically designed architectural forms
                    for (y in by until by + bh) {
                        for (x in bx until bx + bw) {
                            when (blockType) {
                                "GRAND_HALL" -> {
                                    // Grand hall has vertical structural pillars (walls) in a grid pattern
                                    val isPillar = (x - bx) % 2 == 1 && (y - by) % 2 == 1
                                    if (isPillar) {
                                        carveCell(x, y, CellType.WALL)
                                    } else {
                                        carveCell(x, y, CellType.GRAND_HALL)
                                        block.carvedCells.add(Pair(x, y))
                                    }
                                }
                                "DOME_CHAMBER" -> {
                                    // Circular dome vault (shaved off corners)
                                    val cx = bx + bw / 2.0
                                    val cy = by + bh / 2.0
                                    val dist = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                                    val maxRad = minOf(bw, bh) / 2.0
                                    if (dist <= maxRad * maxRad) {
                                        carveCell(x, y, CellType.DOME_CHAMBER)
                                        block.carvedCells.add(Pair(x, y))
                                    } else {
                                        carveCell(x, y, CellType.WALL)
                                    }
                                }
                                "ELEVATED_BALCONY" -> {
                                    carveCell(x, y, CellType.ELEVATED_BALCONY)
                                    block.carvedCells.add(Pair(x, y))
                                }
                                "VENT_TUNNEL" -> {
                                    carveCell(x, y, CellType.VENT_TUNNEL)
                                    block.carvedCells.add(Pair(x, y))
                                }
                                "GRAVITY_SLOPE_ROOM" -> {
                                    carveCell(x, y, CellType.GRAVITY_SLOPE)
                                    block.carvedCells.add(Pair(x, y))
                                }
                                "STAIRCASE_HUB" -> {
                                    val isUp = (x + y) % 2 == 0
                                    val type = if (isUp) CellType.STAIRS_UP else CellType.STAIRS_DOWN
                                    carveCell(x, y, type)
                                    block.carvedCells.add(Pair(x, y))
                                }
                            }
                        }
                    }
                }
    
                val activeBlocks = blocks.filter { it.carvedCells.isNotEmpty() }
    
                // Always guarantee starting position at (1,1) is secure
                carveCell(1, 1, CellType.SAFE_ZONE)
                carveCell(1, 2, CellType.PATH)
                carveCell(2, 1, CellType.PATH)
    
                // 2. Interconnect the architectural blocks with non-linear hallways to ensure loops/branches
                for (idx in activeBlocks.indices) {
                    val b1 = activeBlocks[idx]
                    // Connect to the two closest blocks to create a highly connected network with loops
                    val connections = activeBlocks.indices
                        .filter { it != idx }
                        .sortedBy { targetIdx ->
                            val b2 = activeBlocks[targetIdx]
                            val dx = b1.centerX - b2.centerX
                            val dy = b1.centerY - b2.centerY
                            dx * dx + dy * dy
                        }
                        .take(2)
    
                    for (targetIdx in connections) {
                        val b2 = activeBlocks[targetIdx]
                        var cx = b1.centerX
                        var cy = b1.centerY
                        val tx = b2.centerX
                        val ty = b2.centerY
    
                        // Corridor style can vary along the connection
                        val corridorType = when (random.nextInt(6)) {
                            0 -> CellType.STAIRS_UP
                            1 -> CellType.STAIRS_DOWN
                            2 -> CellType.GRAVITY_SLOPE
                            3 -> CellType.VENT_TUNNEL
                            else -> CellType.PATH
                        }
    
                        while (cx != tx) {
                            carveCell(cx, cy, corridorType)
                            cx += if (tx > cx) 1 else -1
                        }
                        while (cy != ty) {
                            carveCell(cx, cy, corridorType)
                            cy += if (ty > cy) 1 else -1
                        }
                    }
                }
    
                // 3. Maze Braiding (add alternative channels / loops by removing dead-ends or linking walls)
                for (y in 2 until height - 2) {
                    for (x in 2 until width - 2) {
                        if (grid[y][x] == CellType.WALL) {
                            val horizSep = grid[y][x - 1] != CellType.WALL && grid[y][x + 1] != CellType.WALL
                            val vertSep = grid[y - 1][x] != CellType.WALL && grid[y + 1][x] != CellType.WALL
                            if ((horizSep || vertSep) && random.nextFloat() < 0.25f) {
                                val braidType = when (random.nextInt(5)) {
                                    0 -> CellType.GRAVITY_SLOPE
                                    1 -> CellType.VENT_TUNNEL
                                    2 -> CellType.ELEVATED_BALCONY
                                    else -> CellType.PATH
                                }
                                carveCell(x, y, braidType)
                            }
                        }
                    }
                }
    
                // BFS Connectivity Check & Forced Connections
                fun getReachableCells(): Set<Pair<Int, Int>> {
                    val visited = mutableSetOf<Pair<Int, Int>>()
                    val queue = java.util.ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(1, 1))
                    visited.add(Pair(1, 1))
                    while (!queue.isEmpty()) {
                        val (cx, cy) = queue.poll()!!
                        for ((dx, dy) in listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))) {
                            val nx = cx + dx
                            val ny = cy + dy
                            if (nx in 1 until width - 1 && ny in 1 until height - 1) {
                                if (grid[ny][nx] != CellType.WALL && !visited.contains(Pair(nx, ny))) {
                                    visited.add(Pair(nx, ny))
                                    queue.add(Pair(nx, ny))
                                }
                            }
                        }
                    }
                    return visited
                }
    
                var reachable = getReachableCells()
    
                // Forced Connections: Connect isolated rooms to the nearest reachable room
                var connectAttempts = 0
                while (connectAttempts < 50) {
                    val (connectedBlocks, isolatedBlocks) = activeBlocks.partition { block ->
                        block.carvedCells.any { cell -> reachable.contains(cell) }
                    }
    
                    if (isolatedBlocks.isEmpty()) {
                        break
                    }
    
                    val b1 = isolatedBlocks.first()
    
                    if (connectedBlocks.isEmpty()) {
                        // Connect directly to starting point (1, 1)
                        var cx = b1.centerX
                        var cy = b1.centerY
                        val tx = 1
                        val ty = 1
                        while (cx != tx) {
                            carveCell(cx, cy, CellType.PATH)
                            cx += if (tx > cx) 1 else -1
                        }
                        while (cy != ty) {
                            carveCell(cx, cy, CellType.PATH)
                            cy += if (ty > cy) 1 else -1
                        }
                    } else {
                        // Find the nearest connected block
                        val b2 = connectedBlocks.minByOrNull { bConn ->
                            val dx = b1.centerX - bConn.centerX
                            val dy = b1.centerY - bConn.centerY
                            dx * dx + dy * dy
                        }!!
    
                        var cx = b1.centerX
                        var cy = b1.centerY
                        val tx = b2.centerX
                        val ty = b2.centerY
    
                        val corridorType = when (random.nextInt(4)) {
                            0 -> CellType.STAIRS_UP
                            1 -> CellType.STAIRS_DOWN
                            2 -> CellType.GRAVITY_SLOPE
                            else -> CellType.PATH
                        }
    
                        while (cx != tx) {
                            carveCell(cx, cy, corridorType)
                            cx += if (tx > cx) 1 else -1
                        }
                        while (cy != ty) {
                            carveCell(cx, cy, corridorType)
                            cy += if (ty > cy) 1 else -1
                        }
                    }
    
                    reachable = getReachableCells()
                    connectAttempts++
                }
    
                // Ensure surrounding border walls are fully solid for security
                for (x in 0 until width) {
                    grid[0][x] = CellType.WALL
                    grid[height - 1][x] = CellType.WALL
                }
                for (y in 0 until height) {
                    grid[y][0] = CellType.WALL
                    grid[y][width - 1] = CellType.WALL
                }
    
                val reachableWalkable = reachable.filter { (x, y) ->
                    (x > 2 || y > 2) && grid[y][x] != CellType.WALL
                }.toMutableList()
    
                if (reachableWalkable.size < 5) {
                    continue
                }
    
                // 4. Place critical mission items & entities
                // Exit Placement: Ensure the exit room is always placed in a room that has at least one connection to the main path.
                // The exit must never be in an isolated dead-end.
                var maxDist = -1
                var exitCell = Pair(width - 2, height - 2)
                for (cell in reachableWalkable) {
                    val dist = abs(cell.first - 1) + abs(cell.second - 1)
                    if (dist > maxDist) {
                        maxDist = dist
                        exitCell = cell
                    }
                }
                grid[exitCell.second][exitCell.first] = CellType.ENCRYPTED_PORTAL
                reachableWalkable.remove(exitCell)
    
                reachableWalkable.shuffle(random)
    
                // Data Stores (Hacking terminals) - Scaled with map grid size
                val dataStoreCount = 2 + random.nextInt(2) + (layer / 3) + (width * height) / 500
                val placedDataStoreCount = minOf(dataStoreCount, reachableWalkable.size)
                for (i in 0 until placedDataStoreCount) {
                    val cell = reachableWalkable[i]
                    grid[cell.second][cell.first] = CellType.DATA_STORE
                }
                reachableWalkable.removeAll(reachableWalkable.take(placedDataStoreCount))
    
                // Virus Nodes (Active hostile processes) - Scaled with map grid size
                val virusCount = 4 + random.nextInt(3) + (layer / 2) + (width * height) / 350
                val placedVirusCount = minOf(virusCount, reachableWalkable.size)
                for (i in 0 until placedVirusCount) {
                    val cell = reachableWalkable[i]
                    grid[cell.second][cell.first] = CellType.VIRUS_NODE
                }
                reachableWalkable.removeAll(reachableWalkable.take(placedVirusCount))
    
                // Classified Crypt-Caches - Scaled with map grid size
                val secretCount = 3 + random.nextInt(3) + (width * height) / 600
                val placedSecretCount = minOf(secretCount, reachableWalkable.size)
                for (i in 0 until placedSecretCount) {
                    val cell = reachableWalkable[i]
                    grid[cell.second][cell.first] = CellType.SECRET_CACHE
                }
                reachableWalkable.removeAll(reachableWalkable.take(placedSecretCount))
    
                // Additional healing/safety Access Points - Scaled with map grid size
                val extraAccessCount = 1 + random.nextInt(2) + (width * height) / 800
                val placedAccessCount = minOf(extraAccessCount, reachableWalkable.size)
                for (i in 0 until placedAccessCount) {
                    val cell = reachableWalkable[i]
                    grid[cell.second][cell.first] = CellType.SAFE_ZONE
                }
                reachableWalkable.removeAll(reachableWalkable.take(placedAccessCount))
    
                // Validate everything is connected
                val finalReachable = getReachableCells()
                val allRoomsConnected = activeBlocks.all { block ->
                    block.carvedCells.any { cell -> finalReachable.contains(cell) }
                }
                val exitAccessible = finalReachable.contains(exitCell)
    
                if (allRoomsConnected && exitAccessible) {
                    return grid
                }
            }
    
            return grid
        }
}
