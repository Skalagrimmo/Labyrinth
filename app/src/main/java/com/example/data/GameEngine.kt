package com.example.data

import kotlin.math.abs
import kotlin.random.Random

object GameEngine {

    // Procedural Maze Generator
    // Returns a 2D Array of CellType of size width x height
    fun generateMaze(width: Int = 10, height: Int = 10, layer: Int = 1): Array<Array<CellType>> {
        val seedRandom = Random(System.currentTimeMillis() + layer * 123)
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

    private fun enrichMapWithSecretsAndBypasses(grid: Array<Array<com.example.data.CellType>>, random: kotlin.random.Random) {
        val height = grid.size
        val width = grid[0].size

        // 1. Find solid wall locations next to walkable paths and turn them into SECRET_WALL with hidden alcoves behind
        val candidateWallSpots = mutableListOf<Pair<Int, Int>>()
        for (y in 2 until height - 2) {
            for (x in 2 until width - 2) {
                if (grid[y][x] == com.example.data.CellType.WALL) {
                    val hasWalkableNeighbor = listOf(
                        grid[y - 1][x], grid[y + 1][x], grid[y][x - 1], grid[y][x + 1]
                    ).any { it != com.example.data.CellType.WALL && it != com.example.data.CellType.SECRET_WALL }

                    val hasDeepWallSpace = grid[y - 1][x] == com.example.data.CellType.WALL || grid[y + 1][x] == com.example.data.CellType.WALL ||
                            grid[y][x - 1] == com.example.data.CellType.WALL || grid[y][x + 1] == com.example.data.CellType.WALL

                    if (hasWalkableNeighbor && hasDeepWallSpace) {
                        candidateWallSpots.add(Pair(x, y))
                    }
                }
            }
        }
        candidateWallSpots.shuffle(random)

        val secretCount = minOf(8, candidateWallSpots.size / 4)
        for (i in 0 until secretCount) {
            val (swx, swy) = candidateWallSpots[i]
            grid[swy][swx] = com.example.data.CellType.SECRET_WALL

            // Carve small secret vault behind the secret wall
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = swx + dx
                    val ny = swy + dy
                    if (nx in 1 until width - 1 && ny in 1 until height - 1 && grid[ny][nx] == com.example.data.CellType.WALL) {
                        grid[ny][nx] = if (random.nextBoolean()) com.example.data.CellType.SCAN_CACHE else com.example.data.CellType.SECRET_CACHE
                    }
                }
            }
        }

        // 2. Add HACKABLE_TERMINAL & TERMINAL_DOOR gates blocking shortcut corridors
        val walkableCells = mutableListOf<Pair<Int, Int>>()
        for (y in 2 until height - 2) {
            for (x in 2 until width - 2) {
                if (grid[y][x] == com.example.data.CellType.PATH) {
                    walkableCells.add(Pair(x, y))
                }
            }
        }
        walkableCells.shuffle(random)

        if (walkableCells.size >= 10) {
            val gateCount = minOf(6, walkableCells.size / 15)
            for (g in 0 until gateCount) {
                val (px, py) = walkableCells[g * 2]
                grid[py][px] = com.example.data.CellType.HACKABLE_TERMINAL

                for (dir in listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))) {
                    val gx = px + dir.first
                    val gy = py + dir.second
                    if (gx in 1 until width - 1 && gy in 1 until height - 1 && grid[gy][gx] == com.example.data.CellType.PATH) {
                        grid[gy][gx] = com.example.data.CellType.TERMINAL_DOOR
                        break
                    }
                }
            }
        }

        // 3. Add ALTERNATIVE_VENT sub-conduit bypass routes
        for (i in 0 until minOf(10, walkableCells.size / 15)) {
            val (vx, vy) = walkableCells[walkableCells.size - 1 - i]
            grid[vy][vx] = com.example.data.CellType.ALTERNATIVE_VENT
        }
    }

    // Specialized procedural generator for corporate building floors
    fun generateBuildingFloor(floor: Int, seed: Long = System.currentTimeMillis()): Array<Array<com.example.data.CellType>> {
        val random = kotlin.random.Random(seed + floor * 100)
        val width = 64
        val height = 64
        val grid = Array(height) { Array(width) { com.example.data.CellType.WALL } }

        fun carveCell(x: Int, y: Int, type: com.example.data.CellType) {
            if (x in 1 until width - 1 && y in 1 until height - 1) {
                grid[y][x] = type
            }
        }

        val cx = width / 2 // 17
        val cy = height / 2 // 17

        // 1. Core Structure: Central staircase/elevator shaft connecting all floors.
        val lobbyCellType = when (floor) {
            1 -> com.example.data.CellType.GRAND_HALL // Residential lobby
            2 -> com.example.data.CellType.GRAND_HALL // Office lobby
            3 -> com.example.data.CellType.DOME_CHAMBER // Technical hub
            4 -> com.example.data.CellType.VENT_TUNNEL // Storage center
            else -> com.example.data.CellType.GRAND_HALL
        }

        // Carve central core lobby (5x5 room)
        for (ry in (cy - 2)..(cy + 2)) {
            for (rx in (cx - 2)..(cx + 2)) {
                carveCell(rx, ry, lobbyCellType)
            }
        }

        // Center is the Elevator column
        grid[cy][cx] = com.example.data.CellType.ELEVATOR

        // Stairs Up / Portal to next Zone
        if (floor < 4) {
            grid[cy][cx - 1] = com.example.data.CellType.STAIRS_UP
        } else {
            // Top floor (Floor 4) has exit portal connecting to the underground Collectors!
            grid[cy][cx - 1] = com.example.data.CellType.ENCRYPTED_PORTAL
        }

        // Stairs Down
        if (floor > 1) {
            grid[cy][cx + 1] = com.example.data.CellType.STAIRS_DOWN
        }

        // 2. Entrance: Always on the ground floor (Floor 1), leading to a lobby / hall.
        if (floor == 1) {
            // Spawn entrance at (1, 1) and carve safe lobby entrance
            for (y in 1..2) {
                for (x in 1..2) {
                    carveCell(x, y, com.example.data.CellType.PATH)
                }
            }
            grid[1][1] = com.example.data.CellType.SAFE_ZONE

            // Connect entrance (1, 1) to central lobby core (cx, cy)
            for (y in 1..cy) {
                carveCell(1, y, com.example.data.CellType.PATH)
            }
            for (x in 1..(cx - 2)) {
                carveCell(x, cy, com.example.data.CellType.PATH)
            }
        }

        // 3. Corridors: Extend from core in 4 directions with random lengths (6-9 tiles)
        val directions = listOf(
            Pair(-1, 0), // Left (West)
            Pair(1, 0),  // Right (East)
            Pair(0, -1), // Up (North)
            Pair(0, 1)   // Down (South)
        )

        data class RoomSpec(val x: Int, val y: Int, val w: Int, val h: Int, val cellType: com.example.data.CellType)
        val rooms = mutableListOf<RoomSpec>()

        for (dir in directions) {
            val length = random.nextInt(4) + 6 // 6 to 9 tiles
            val startX = when {
                dir.first < 0 -> cx - 2
                dir.first > 0 -> cx + 2
                else -> cx
            }
            val startY = when {
                dir.second < 0 -> cy - 2
                dir.second > 0 -> cy + 2
                else -> cy
            }

            // Carve main corridor tiles
            for (i in 1..length) {
                val tx = startX + dir.first * i
                val ty = startY + dir.second * i
                carveCell(tx, ty, com.example.data.CellType.PATH)
            }

            // End position of corridor
            val endX = startX + dir.first * length
            val endY = startY + dir.second * length

            // 4. Branching Sub-corridors running perpendicular from the end of corridors (Adds paths!)
            val perpDir = Pair(-dir.second, dir.first)
            val branchLengths = listOf(4, 5, 6)

            for (sideSign in listOf(-1, 1)) {
                val branchLen = branchLengths[random.nextInt(branchLengths.size)]
                var bx = endX
                var by = endY
                // Carve perpendicular branch
                for (i in 1..branchLen) {
                    bx = endX + perpDir.first * sideSign * i
                    by = endY + perpDir.second * sideSign * i
                    carveCell(bx, by, com.example.data.CellType.PATH)
                }

                // Place more varied types of rooms at the end of each sub-corridor branch!
                val roomCellType = when (floor) {
                    1 -> {
                        val choices = listOf(
                            com.example.data.CellType.ELEVATED_BALCONY, // Private Deck Apt
                            com.example.data.CellType.GRAND_HALL,        // Atrium
                            com.example.data.CellType.VENT_TUNNEL        // Service room
                        )
                        choices[random.nextInt(choices.size)]
                    }
                    2 -> {
                        val choices = listOf(
                            com.example.data.CellType.GRAND_HALL,        // Boardroom
                            com.example.data.CellType.DOME_CHAMBER,      // Strategy Vault
                            com.example.data.CellType.ELEVATED_BALCONY   // Sky Garden Breakroom
                        )
                        choices[random.nextInt(choices.size)]
                    }
                    3 -> {
                        val choices = listOf(
                            com.example.data.CellType.DOME_CHAMBER,      // Mainframe Center
                            com.example.data.CellType.GRAVITY_SLOPE,     // Magnetic Cooler
                            com.example.data.CellType.GRAND_HALL         // Tech Control Suite
                        )
                        choices[random.nextInt(choices.size)]
                    }
                    4 -> {
                        val choices = listOf(
                            com.example.data.CellType.VENT_TUNNEL,       // High-density Storage
                            com.example.data.CellType.ELEVATED_BALCONY,  // Cargo Platform
                            com.example.data.CellType.GRAND_HALL,        // Distribution Hub
                            com.example.data.CellType.DOME_CHAMBER       // Secure Archives
                        )
                        choices[random.nextInt(choices.size)]
                    }
                    else -> com.example.data.CellType.PATH
                }

                val rw = random.nextInt(3) + 3 // 3x3 to 5x5
                val rh = random.nextInt(3) + 3
                val rx = (bx - rw / 2).coerceIn(1, width - 1 - rw)
                val ry = (by - rh / 2).coerceIn(1, height - 1 - rh)

                // Check collision with Central Core area to prevent clipping elevator/stairs
                var intersectsCore = false
                for (y in ry until ry + rh) {
                    for (x in rx until rx + rw) {
                        if (x in (cx - 3)..(cx + 3) && y in (cy - 3)..(cy + 3)) {
                            intersectsCore = true
                        }
                    }
                }

                if (!intersectsCore) {
                    rooms.add(RoomSpec(rx, ry, rw, rh, roomCellType))
                }
            }

            // 5. Rooms: Placed along main corridors, 2-3 per corridor.
            // Each room has a random size (3x3 to 5x5) and type based on floor theme.
            val numRooms = random.nextInt(2) + 2 // 2 to 3 rooms
            val roomCellType = when (floor) {
                1 -> com.example.data.CellType.ELEVATED_BALCONY // Residential: Apartments
                2 -> com.example.data.CellType.GRAND_HALL // Office: Office rooms
                3 -> com.example.data.CellType.DOME_CHAMBER // Technical: Servers
                4 -> com.example.data.CellType.VENT_TUNNEL // Storage: Utility closets
                else -> com.example.data.CellType.PATH
            }

            for (rIndex in 0 until numRooms) {
                val step = if (numRooms == 2) {
                    if (rIndex == 0) 2 else length - 2
                } else {
                    if (rIndex == 0) 2 else if (rIndex == 1) length / 2 else length - 2
                }

                val corridorX = startX + dir.first * step
                val corridorY = startY + dir.second * step

                val rw = random.nextInt(3) + 3 // 3x3 to 5x5
                val rh = random.nextInt(3) + 3

                val sideSign = if (random.nextBoolean()) 1 else -1
                val branchX = perpDir.first * sideSign
                val branchY = perpDir.second * sideSign

                val roomX = if (branchX < 0) {
                    corridorX - rw
                } else if (branchX > 0) {
                    corridorX + 1
                } else {
                    corridorX - rw / 2
                }

                val roomY = if (branchY < 0) {
                    corridorY - rh
                } else if (branchY > 0) {
                    corridorY + 1
                } else {
                    corridorY - rh / 2
                }

                val adjustedX = roomX.coerceIn(1, width - 1 - rw)
                val adjustedY = roomY.coerceIn(1, height - 1 - rh)

                // Check collision with Central Core area to prevent clipping elevator/stairs
                var intersectsCore = false
                for (y in adjustedY until adjustedY + rh) {
                    for (x in adjustedX until adjustedX + rw) {
                        if (x in (cx - 3)..(cx + 3) && y in (cy - 3)..(cy + 3)) {
                            intersectsCore = true
                        }
                    }
                }

                if (!intersectsCore) {
                    rooms.add(RoomSpec(adjustedX, adjustedY, rw, rh, roomCellType))
                    carveCell(corridorX, corridorY, com.example.data.CellType.PATH)
                }
            }
        }

        // Render rooms on grid
        for (room in rooms) {
            for (ry in room.y until room.y + room.h) {
                for (rx in room.x until room.x + room.w) {
                    carveCell(rx, ry, room.cellType)
                }
            }
        }

        // 6. Gather walkable spaces for virus process nodes and data stores
        val walkable = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (grid[y][x] != com.example.data.CellType.WALL &&
                    grid[y][x] != com.example.data.CellType.STAIRS_UP &&
                    grid[y][x] != com.example.data.CellType.STAIRS_DOWN &&
                    grid[y][x] != com.example.data.CellType.ELEVATOR &&
                    grid[y][x] != com.example.data.CellType.ENCRYPTED_PORTAL &&
                    grid[y][x] != com.example.data.CellType.SAFE_ZONE) {
                    walkable.add(Pair(x, y))
                }
            }
        }
        walkable.shuffle(random)

        // Keycard required for Elevator is hidden on Floor 2!
        if (floor == 2 && walkable.isNotEmpty()) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.SECRET_CACHE
        }

        val dsCount = when (floor) {
            1 -> 3
            2 -> 5
            3 -> 6
            else -> 4
        }
        for (i in 0 until minOf(dsCount, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.DATA_STORE
        }

        val vCount = when (floor) {
            1 -> 4
            2 -> 6
            3 -> 9
            else -> 5
        }
        for (i in 0 until minOf(vCount, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.VIRUS_NODE
        }

        val cCount = when (floor) {
            1 -> 2
            2 -> 3
            3 -> 4
            else -> 4
        }
        for (i in 0 until minOf(cCount, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.SECRET_CACHE
        }

        enrichMapWithSecretsAndBypasses(grid, random)
        return grid
    }

    // Specialized procedural generator for underground collector tunnels
    fun generateCollectorTunnels(level: Int, seed: Long = System.currentTimeMillis()): Array<Array<com.example.data.CellType>> {
        val random = kotlin.random.Random(seed + level * 50)
        val width = 64
        val height = 64
        val grid = Array(height) { Array(width) { com.example.data.CellType.WALL } }

        fun carve(x: Int, y: Int, type: com.example.data.CellType) {
            if (x in 1 until width - 1 && y in 1 until height - 1) {
                grid[y][x] = type
            }
        }

        val activeNodes = mutableListOf<Pair<Int, Int>>()
        for (i in 1..4) {
            var cx = random.nextInt(5, width - 5)
            var cy = random.nextInt(5, height - 5)
            activeNodes.add(Pair(cx, cy))
            
            for (steps in 0..80) {
                carve(cx, cy, if (random.nextBoolean()) com.example.data.CellType.VENT_TUNNEL else com.example.data.CellType.GRAVITY_SLOPE)
                val dir = random.nextInt(4)
                cx += if (dir == 0) 1 else if (dir == 1) -1 else 0
                cy += if (dir == 2) 1 else if (dir == 3) -1 else 0
                cx = cx.coerceIn(2, width - 3)
                cy = cy.coerceIn(2, height - 3)
            }
        }

        for (y in 1..3) {
            for (x in 1..3) {
                carve(x, y, com.example.data.CellType.PATH)
            }
        }
        grid[1][1] = com.example.data.CellType.SAFE_ZONE

        for (i in 0 until activeNodes.size - 1) {
            val (x1, y1) = activeNodes[i]
            val (x2, y2) = activeNodes[i + 1]
            var cx = x1
            var cy = y1
            while (cx != x2) {
                carve(cx, cy, com.example.data.CellType.PATH)
                cx += if (x2 > cx) 1 else -1
            }
            while (cy != y2) {
                carve(cx, cy, com.example.data.CellType.PATH)
                cy += if (y2 > cy) 1 else -1
            }
        }

        val exitCell = activeNodes.last()
        grid[exitCell.second][exitCell.first] = if (level == 2) com.example.data.CellType.ENCRYPTED_PORTAL else com.example.data.CellType.STAIRS_UP

        val walkable = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (grid[y][x] != com.example.data.CellType.WALL && 
                    grid[y][x] != com.example.data.CellType.STAIRS_UP && 
                    grid[y][x] != com.example.data.CellType.ENCRYPTED_PORTAL && 
                    grid[y][x] != com.example.data.CellType.SAFE_ZONE) {
                    walkable.add(Pair(x, y))
                }
            }
        }
        walkable.shuffle(random)

        for (i in 0 until minOf(3, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.DATA_STORE
        }
        for (i in 0 until minOf(5, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.VIRUS_NODE
        }
        for (i in 0 until minOf(2, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.SECRET_CACHE
        }

        enrichMapWithSecretsAndBypasses(grid, random)
        return grid
    }

    // Specialized procedural generator for dynamic open metropolitan Cyber-City sectors
    fun generateCitySector(districtIndex: Int, seed: Long = System.currentTimeMillis()): Array<Array<com.example.data.CellType>> {
        val random = kotlin.random.Random(seed + districtIndex * 70)
        val width = 64
        val height = 64
        val grid = Array(height) { Array(width) { com.example.data.CellType.WALL } }

        fun carve(x: Int, y: Int, type: com.example.data.CellType) {
            if (x in 1 until width - 1 && y in 1 until height - 1) {
                grid[y][x] = type
            }
        }

        // Grand plaza center core
        for (y in 12..22) {
            for (x in 12..22) {
                val isPillar = (x == 15 || x == 19) && (y == 15 || y == 19)
                carve(x, y, if (isPillar) com.example.data.CellType.WALL else com.example.data.CellType.GRAND_HALL)
            }
        }

        // Broad outdoor avenues
        for (x in 2 until width - 2) {
            carve(x, 17, com.example.data.CellType.PATH)
            carve(x, 7, com.example.data.CellType.PATH)
            carve(x, 27, com.example.data.CellType.PATH)
        }
        for (y in 2 until height - 2) {
            carve(17, y, com.example.data.CellType.PATH)
            carve(7, y, com.example.data.CellType.PATH)
            carve(27, y, com.example.data.CellType.PATH)
        }

        // High rise corporate districts
        for (by in listOf(2, 9, 20, 28)) {
            for (bx in listOf(2, 9, 20, 28)) {
                if (bx == 20 && by == 20) continue
                val shape = random.nextInt(3)
                for (y in by until by + 5) {
                    for (x in bx until bx + 5) {
                        if (x in 1 until width - 1 && y in 1 until height - 1) {
                            if (shape == 0) carve(x, y, com.example.data.CellType.ELEVATED_BALCONY)
                            else if (shape == 1) carve(x, y, com.example.data.CellType.DOME_CHAMBER)
                            else carve(x, y, com.example.data.CellType.PATH)
                        }
                    }
                }
            }
        }

        for (y in 1..3) {
            for (x in 1..3) {
                carve(x, y, com.example.data.CellType.PATH)
            }
        }
        grid[1][1] = com.example.data.CellType.SAFE_ZONE

        // Ultimate sector gate at the corner
        grid[height - 3][width - 3] = com.example.data.CellType.ENCRYPTED_PORTAL

        val walkable = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (grid[y][x] != com.example.data.CellType.WALL && 
                    grid[y][x] != com.example.data.CellType.ENCRYPTED_PORTAL && 
                    grid[y][x] != com.example.data.CellType.SAFE_ZONE) {
                    walkable.add(Pair(x, y))
                }
            }
        }
        walkable.shuffle(random)

        for (i in 0 until minOf(5, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.DATA_STORE
        }
        for (i in 0 until minOf(7, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.VIRUS_NODE
        }
        for (i in 0 until minOf(4, walkable.size)) {
            val cell = walkable.removeAt(0)
            grid[cell.second][cell.first] = com.example.data.CellType.SECRET_CACHE
        }

        enrichMapWithSecretsAndBypasses(grid, random)
        return grid
    }

    private fun generateFallbackMaze(width: Int, height: Int): Array<Array<CellType>> {
        val grid = Array(height) { Array(width) { CellType.WALL } }
        val roomsCount = 4
        
        // Define centers of rooms along the diagonal
        val centers = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until roomsCount) {
            val t = i.toFloat() / (roomsCount - 1)
            val cx = (1 + t * (width - 3)).toInt().coerceIn(1, width - 2)
            val cy = (1 + t * (height - 3)).toInt().coerceIn(1, height - 2)
            centers.add(Pair(cx, cy))
        }
        
        // Carve rooms (9x9 size to require 9 steps to cross)
        for ((cx, cy) in centers) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val rx = cx + dx
                    val ry = cy + dy
                    if (rx in 1 until width - 1 && ry in 1 until height - 1) {
                        grid[ry][rx] = CellType.PATH
                    }
                }
            }
        }
        
        // Connect rooms linearly
        for (i in 0 until centers.size - 1) {
            val (x1, y1) = centers[i]
            val (x2, y2) = centers[i + 1]
            var cx = x1
            var cy = y1
            while (cx != x2) {
                if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                    grid[cy][cx] = CellType.PATH
                }
                cx += if (x2 > cx) 1 else -1
            }
            while (cy != y2) {
                if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                    grid[cy][cx] = CellType.PATH
                }
                cy += if (y2 > cy) 1 else -1
            }
        }
        
        // Place critical elements
        grid[1][1] = CellType.SAFE_ZONE
        
        val exitX = centers.last().first
        val exitY = centers.last().second
        grid[exitY][exitX] = CellType.ENCRYPTED_PORTAL
        
        // Place some items in other room centers
        if (centers.size > 2) {
            val (dx1, dy1) = centers[1]
            grid[dy1][dx1] = CellType.DATA_STORE
            
            val (dx2, dy2) = centers[2]
            grid[dy2][dx2] = CellType.VIRUS_NODE
        }
        
        return grid
    }

    // Canvas-style 3D ASCII Perspective Wireframe Drawer
    fun render3DPerspective(
        grid: Array<Array<CellType>>,
        px: Int,
        py: Int,
        dir: Direction,
        activeWeather: CyberWeather = CyberWeather.CLEAR
    ): String {
        val canvas = CharCanvas(11, 31)

        // Define rendering coordinates for depths 0, 1, 2, 3
        val tl_c = intArrayOf(0, 6, 11, 13)
        val tl_r = intArrayOf(0, 2, 3, 4)
        val bl_c = intArrayOf(0, 6, 11, 13)
        val bl_r = intArrayOf(10, 8, 7, 6)

        val tr_c = intArrayOf(30, 24, 19, 17)
        val tr_r = intArrayOf(0, 2, 3, 4)
        val br_c = intArrayOf(30, 24, 19, 17)
        val br_r = intArrayOf(10, 8, 7, 6)

        // Check view distance up to 3 cells
        if (grid.isEmpty() || grid[0].isEmpty()) {
            return ""
        }
        val width = grid[0].size
        val height = grid.size

        // Let's gather the layout of cells ahead of us
        val cellTypes = Array(4) { CellType.WALL }
        val cellCoords = Array(4) { Pair(-1, -1) }

        for (d in 0..3) {
            val cx = px + d * dir.dx
            val cy = py + d * dir.dy
            if (cx in 0 until width && cy in 0 until height) {
                cellTypes[d] = grid[cy][cx]
                cellCoords[d] = Pair(cx, cy)
            } else {
                cellTypes[d] = CellType.WALL
                cellCoords[d] = Pair(cx, cy)
            }
        }

        // We check side walls at each depth d=0, 1, 2
        val leftWallAt = BooleanArray(3) { true }
        val rightWallAt = BooleanArray(3) { true }

        val leftDir = dir.turnLeft()
        val rightDir = dir.turnRight()

        for (d in 0..2) {
            val cc = cellCoords[d]
            if (cc.first != -1) {
                // Left neighbor at depth d
                val lx = cc.first + leftDir.dx
                val ly = cc.second + leftDir.dy
                if (lx in 0 until width && ly in 0 until height) {
                    leftWallAt[d] = grid[ly][lx] == CellType.WALL
                }

                // Right neighbor at depth d
                val rx = cc.first + rightDir.dx
                val ry = cc.second + rightDir.dy
                if (rx in 0 until width && ry in 0 until height) {
                    rightWallAt[d] = grid[ry][rx] == CellType.WALL
                }
            }
        }

        // Find the first blocking wall straight ahead
        var maxVisibleDepth = if (activeWeather == CyberWeather.DATA_STORM) 1 else 3
        if (activeWeather != CyberWeather.DATA_STORM) {
            for (d in 1..3) {
                if (cellTypes[d] == CellType.WALL) {
                    maxVisibleDepth = d
                    break
                }
            }
        }

        // --- Helper: High-Density Box Drawing ---
        fun drawBox(rStart: Int, cStart: Int, rEnd: Int, cEnd: Int) {
            for (col in (cStart + 1) until cEnd) {
                canvas.set(rStart, col, '━')
                canvas.set(rEnd, col, '━')
            }
            for (row in (rStart + 1) until rEnd) {
                canvas.set(row, cStart, '┃')
                canvas.set(row, cEnd, '┃')
            }
            canvas.set(rStart, cStart, '┏')
            canvas.set(rStart, cEnd, '┓')
            canvas.set(rEnd, cStart, '┗')
            canvas.set(rEnd, cEnd, '┛')
        }

        // --- Helper: Procedural Wall Filling with Linear Interpolation ---
        fun fillLeftWall(d: Int, char: Char) {
            val startCol = tl_c[d]
            val endCol = tl_c[d+1]
            val rTopStart = tl_r[d]
            val rTopEnd = tl_r[d+1]
            val rBotStart = bl_r[d]
            val rBotEnd = bl_r[d+1]

            for (col in startCol..endCol) {
                val colWidth = endCol - startCol
                val ratio = if (colWidth > 0) (col - startCol).toFloat() / colWidth else 0f
                val rTop = Math.round(rTopStart + ratio * (rTopEnd - rTopStart))
                val rBot = Math.round(rBotStart + ratio * (rBotEnd - rBotStart))
                for (row in rTop..rBot) {
                    canvas.set(row, col, char)
                }
            }
        }

        fun fillRightWall(d: Int, char: Char) {
            val startCol = tr_c[d+1]
            val endCol = tr_c[d]
            val rTopStart = tr_r[d+1]
            val rTopEnd = tr_r[d]
            val rBotStart = br_r[d+1]
            val rBotEnd = br_r[d]

            for (col in startCol..endCol) {
                val colWidth = endCol - startCol
                val ratio = if (colWidth > 0) (col - startCol).toFloat() / colWidth else 0f
                val rTop = Math.round(rTopStart + ratio * (rTopEnd - rTopStart))
                val rBot = Math.round(rBotStart + ratio * (rBotEnd - rBotStart))
                for (row in rTop..rBot) {
                    canvas.set(row, col, char)
                }
            }
        }

        // --- 1. Procedural Floor and Ceiling Dot Grid Generation ---
        // Pre-calculate top and bottom row limits for each column to texture background empty space
        val rTopLimits = IntArray(31) { 5 }
        val rBotLimits = IntArray(31) { 5 }

        for (col in 0..30) {
            var found = false
            for (d in 0..2) {
                if (col >= tl_c[d] && col <= tl_c[d+1]) {
                    val ratio = (col - tl_c[d]).toFloat() / (tl_c[d+1] - tl_c[d])
                    rTopLimits[col] = Math.round(tl_r[d] + ratio * (tl_r[d+1] - tl_r[d]))
                    rBotLimits[col] = Math.round(bl_r[d] + ratio * (bl_r[d+1] - bl_r[d]))
                    found = true
                    break
                }
            }
            if (!found) {
                for (d in 0..2) {
                    if (col >= tr_c[d+1] && col <= tr_c[d]) {
                        val ratio = (col - tr_c[d+1]).toFloat() / (tr_c[d] - tr_c[d+1])
                        rTopLimits[col] = Math.round(tr_r[d+1] + ratio * (tr_r[d] - tr_r[d+1]))
                        rBotLimits[col] = Math.round(br_r[d+1] + ratio * (br_r[d] - br_r[d+1]))
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                val d = maxVisibleDepth.coerceAtMost(3)
                rTopLimits[col] = tl_r[d]
                rBotLimits[col] = bl_r[d]
            }
        }

        // Fill procedural dot grids
        for (col in 1..29) {
            val tLimit = rTopLimits[col]
            val bLimit = rBotLimits[col]
            
            // Ceiling Dot Grid
            for (row in 1 until tLimit) {
                if ((col + row * 2) % 4 == 0) {
                    canvas.set(row, col, '·')
                }
            }
            
            // Floor Dot Grid (converging perspective-like texture)
            for (row in (bLimit + 1)..9) {
                if ((col - row) % 4 == 0) {
                    canvas.set(row, col, '·')
                }
            }
        }

        // --- 2. Draw Front-Facing Wall (at blocking depth) ---
        if (maxVisibleDepth <= 3) {
            val d = maxVisibleDepth
            val rStart = tl_r[d]
            val rEnd = bl_r[d]
            val cStart = tl_c[d]
            val cEnd = tr_c[d]
            val frontShade = when (d) {
                1 -> '█' // Closest: solid bulkhead
                2 -> '▓' // Medium: dark block
                3 -> '▒' // Far: medium block
                else -> '░'
            }
            for (row in rStart..rEnd) {
                for (col in cStart..cEnd) {
                    canvas.set(row, col, frontShade)
                }
            }
            // Draw neat high-density box boundary around it
            drawBox(rStart, cStart, rEnd, cEnd)

            // Centered Bulkhead details
            if (d == 1) {
                val label = "[ SYSTEM BLK ]"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            } else if (d == 2) {
                val label = "LOCKED"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            }
        } else {
            // Draw very distant horizon at depth 3
            drawBox(tl_r[3], tl_c[3], bl_r[3], br_c[3])
            canvas.set(5, 15, '·') // Faint horizon vanishing point
        }

        // --- 3. Draw Side Walls from Back to Front ---
        val leftRightShades = charArrayOf('▓', '▒', '░')
        for (d in (maxVisibleDepth - 1) downTo 0) {
            // Draw Left Wall at depth d
            if (leftWallAt[d]) {
                val shadeChar = leftRightShades[d.coerceIn(0, 2)]
                fillLeftWall(d, shadeChar)

                // Define perspective diagonals
                canvas.drawLine(tl_r[d], tl_c[d], tl_r[d+1], tl_c[d+1], '\\')
                canvas.drawLine(bl_r[d], bl_c[d], bl_r[d+1], bl_c[d+1], '/')
            } else {
                // Open branch side opening ceiling & floor lines
                for (col in tl_c[d]..tl_c[d+1]) {
                    canvas.set(tl_r[d+1], col, '━')
                    canvas.set(bl_r[d+1], col, '━')
                }
                // Vertical structural pillar
                for (row in tl_r[d+1]..bl_r[d+1]) {
                    canvas.set(row, tl_c[d+1], '┃')
                }
            }

            // Draw Right Wall at depth d
            if (rightWallAt[d]) {
                val shadeChar = leftRightShades[d.coerceIn(0, 2)]
                fillRightWall(d, shadeChar)

                canvas.drawLine(tr_r[d], tr_c[d], tr_r[d+1], tr_c[d+1], '/')
                canvas.drawLine(br_r[d], br_c[d], br_r[d+1], br_c[d+1], '\\')
            } else {
                // Open branch side opening ceiling & floor lines
                for (col in tr_c[d+1]..tr_c[d]) {
                    canvas.set(tr_r[d+1], col, '━')
                    canvas.set(br_r[d+1], col, '━')
                }
                // Vertical structural pillar
                for (row in tr_r[d+1]..br_r[d+1]) {
                    canvas.set(row, tr_c[d+1], '┃')
                }
            }
        }

        // --- 4. Overlay Central Special Assets ---
        val primaryNode = cellTypes[1]
        if (primaryNode == CellType.VIRUS_NODE) {
            // High-density Virus icon using crisp Unicode elements
            canvas.set(3, 13, '▲')
            canvas.set(3, 17, '▲')
            
            canvas.set(4, 11, '◀')
            canvas.set(4, 13, '█')
            canvas.set(4, 14, '▄')
            canvas.set(4, 15, '▄')
            canvas.set(4, 16, '█')
            canvas.set(4, 18, '▶')
            
            canvas.set(5, 12, '╱')
            canvas.set(5, 13, '█')
            canvas.set(5, 14, '▀')
            canvas.set(5, 15, '█')
            canvas.set(5, 16, '╲')
            
            val label = "[VIRUS]"
            val startCol = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(6, startCol + i, label[i])
            }
        } else if (primaryNode == CellType.DATA_STORE) {
            // High-density Data Store terminal icon
            canvas.set(3, 11, '╔')
            for (c in 12..18) canvas.set(3, c, '═')
            canvas.set(3, 19, '╗')
            
            canvas.set(4, 11, '║')
            canvas.set(4, 13, 'D')
            canvas.set(4, 14, 'A')
            canvas.set(4, 15, 'T')
            canvas.set(4, 16, 'A')
            canvas.set(4, 19, '║')
            
            canvas.set(5, 11, '╚')
            for (c in 12..18) canvas.set(5, c, '═')
            canvas.set(5, 19, '╝')
            
            for (c in 10..20) canvas.set(6, c, '▒')
        } else if (primaryNode == CellType.ENCRYPTED_PORTAL) {
            // High-density Encrypted Portal vortex icon
            canvas.set(3, 12, '◢')
            canvas.set(3, 13, '█')
            canvas.set(3, 14, '█')
            canvas.set(3, 15, '█')
            canvas.set(3, 16, '█')
            canvas.set(3, 17, '█')
            canvas.set(3, 18, '◣')
            
            canvas.set(4, 11, '█')
            canvas.set(4, 13, 'P')
            canvas.set(4, 14, 'O')
            canvas.set(4, 15, 'R')
            canvas.set(4, 16, 'T')
            canvas.set(4, 18, '█')
            
            canvas.set(5, 12, '◥')
            canvas.set(5, 13, '█')
            canvas.set(5, 14, '█')
            canvas.set(5, 15, '█')
            canvas.set(5, 16, '█')
            canvas.set(5, 17, '█')
            canvas.set(5, 18, '◤')
            
            for (c in 11..19) canvas.set(6, c, '▒')
        } else if (primaryNode == CellType.SECRET_CACHE) {
            // Quantum Crypt-Cache floating cube icon
            canvas.set(3, 13, '╭')
            for (c in 14..16) canvas.set(3, c, '─')
            canvas.set(3, 17, '╮')
            
            canvas.set(4, 12, '│')
            canvas.set(4, 14, 'S')
            canvas.set(4, 15, 'E')
            canvas.set(4, 16, 'C')
            canvas.set(4, 18, '│')
            
            canvas.set(5, 13, '╰')
            for (c in 14..16) canvas.set(5, c, '─')
            canvas.set(5, 17, '╯')
            
            for (c in 12..18) canvas.set(6, c, '░')
        } else if (primaryNode == CellType.GRAND_HALL) {
            // Monumental pillars on the left and right sides
            for (r in 2..8) {
                canvas.set(r, 9, '┃')
                canvas.set(r, 10, '█')
                canvas.set(r, 20, '█')
                canvas.set(r, 21, '┃')
            }
            canvas.set(1, 9, '╔')
            canvas.set(1, 10, '╤')
            canvas.set(9, 9, '╚')
            canvas.set(9, 10, '╧')
            canvas.set(1, 20, '╤')
            canvas.set(1, 21, '╗')
            canvas.set(9, 20, '╧')
            canvas.set(9, 21, '╝')

            val label = "GRAND HALL"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(5, colStart + i, label[i])
            }
        } else if (primaryNode == CellType.DOME_CHAMBER) {
            // Curved arched rib lines of high-tech dome ceiling
            canvas.drawLine(1, 6, 3, 15, '╭')
            canvas.drawLine(1, 24, 3, 15, '╮')
            canvas.set(3, 15, '◎')
            canvas.drawLine(9, 6, 7, 15, '╰')
            canvas.drawLine(9, 24, 7, 15, '╯')

            val label = "DOME VAULT"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(5, colStart + i, label[i])
            }
        } else if (primaryNode == CellType.VENT_TUNNEL) {
            // Low-ceiling vent tunnel structure
            for (c in 6..24) {
                canvas.set(2, c, '▄')
                canvas.set(3, c, '█')
            }
            val label = "TUNNEL CONDUIT"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(5, colStart + i, label[i])
            }
        } else if (primaryNode == CellType.ELEVATED_BALCONY) {
            // Raised balcony handrails
            for (c in 7..23) {
                canvas.set(6, c, '╦')
                canvas.set(7, c, '║')
                if (c % 2 == 0) canvas.set(8, c, '▒')
            }
            val label = "BALCONY LEDGE"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(4, colStart + i, label[i])
            }
        } else if (primaryNode == CellType.STAIRS_UP) {
            // Upward steps wireframe
            canvas.set(4, 11, '╭')
            for (c in 12..18) canvas.set(4, c, '─')
            canvas.set(4, 19, '╮')
            canvas.set(5, 10, '┌')
            for (c in 11..19) canvas.set(5, c, '─')
            canvas.set(5, 20, '┐')
            canvas.set(6, 9, '┌')
            for (c in 10..20) canvas.set(6, c, '─')
            canvas.set(6, 21, '┐')
            canvas.set(7, 8, '┌')
            for (c in 9..21) canvas.set(7, c, '─')
            canvas.set(7, 22, '┐')

            val label = "STAIRS UP"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(2, colStart + i, label[i])
            }
        } else if (primaryNode == CellType.STAIRS_DOWN) {
            // Downward descending steps wireframe
            canvas.set(8, 11, '╰')
            for (c in 12..18) canvas.set(8, c, '─')
            canvas.set(8, 19, '╯')
            canvas.set(7, 10, '└')
            for (c in 11..19) canvas.set(7, c, '─')
            canvas.set(7, 20, '┘')
            canvas.set(6, 9, '└')
            for (c in 10..20) canvas.set(6, c, '─')
            canvas.set(6, 21, '┘')

            val label = "STAIRS DOWN"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(4, colStart + i, label[i])
            }
        } else if (primaryNode == CellType.GRAVITY_SLOPE) {
            // Slanting slope lines
            canvas.drawLine(8, 10, 4, 20, '/')
            canvas.drawLine(9, 11, 5, 21, '/')
            canvas.drawLine(7, 9, 3, 19, '/')

            val label = "GRAVITY SLOPE"
            val colStart = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(5, colStart + i, label[i])
            }
        }

        // --- 5. Draw Outer Border Frame ---
        drawBox(0, 0, 10, 30)

        return canvas.render()
    }

    // Default starting cyberwares list
    fun getStoreCyberware(): List<Cyberware> {
        return listOf(
            Cyberware("cpu_oc", "CPU Overclocker", "+2 RAM Recovery Rate", 200, recoveryBonus = 2),
            Cyberware("mem_exp", "RAM Rig Extension", "+4 Max RAM Allocation", 250, ramBonus = 4),
            Cyberware("armor_plt", "Sub-Dermal Firewall", "+30 System Integrity", 180, integrityBonus = 30),
            Cyberware("dmg_mod", "Payload Amplifier", "+5 Attack Damage output", 300, damageBonus = 5),
            Cyberware("def_mod", "Defensive Buffer", "+10% Armor Defense", 220, defenseBonus = 2)
        )
    }

    // Starting programs list
    fun getStartingPrograms(runnerClass: NetrunnerClass): List<Program> {
        val base = mutableListOf(
            Program("ping", "ping.exe", "Scan enemy process. Deals 10 payload damage.", ramCost = 1, damage = 10),
            Program("firewall", "firewall.sh", "Harden defences. Restore 25 shield points.", ramCost = 2, shield = 25),
            Program("corrode", "acid_corrode.sh", "Inject malware. Deals 12 dmg + Corrodes target (8 DPS x 3 turns).", ramCost = 2, damage = 12, statusEffectToApply = StatusEffectType.POISONED, statusEffectTurns = 3, statusEffectMagnitude = 8),
            Program("stun_pulse", "stun_pulse.exe", "High-voltage surge. Deals 10 dmg + Stuns target for 1 turn.", ramCost = 3, damage = 10, statusEffectToApply = StatusEffectType.STUNNED, statusEffectTurns = 1),
            Program("overclock", "overclock.sys", "Overclock system core. Grants Overclocked buff (+50% attack dmg for 2 turns).", ramCost = 3, statusEffectToApply = StatusEffectType.BUFFED, statusEffectTurns = 2, statusEffectTargetSelf = true),
            Program("glitch", "glitch_payload.bin", "Scramble target sensors. Glitches target (-50% damage output for 2 turns).", ramCost = 2, statusEffectToApply = StatusEffectType.WEAKENED, statusEffectTurns = 2)
        )
        when (runnerClass) {
            NetrunnerClass.NETRUNNER -> {
                base.add(Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true))
                base.add(Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35))
            }
            NetrunnerClass.STREET_SAMURAI -> {
                base.add(Program("katana_strike", "katana_slash.exe", "Lethal blade strike. Deals 40 physical payload damage.", ramCost = 3, damage = 40))
            }
            NetrunnerClass.TECHIE -> {
                base.add(Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40))
                base.add(Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15))
            }
            NetrunnerClass.CODE_SLASHER -> {
                base.add(Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35))
            }
            NetrunnerClass.CYBER_SHIELD -> {
                base.add(Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40))
            }
            NetrunnerClass.BUFFER_OVERFLOW -> {
                base.add(Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true))
            }
            NetrunnerClass.SCRIPT_KIDDIE -> {
                base.add(Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15))
            }
        }
        return base
    }

    // Generates a fully loaded enemy depending on the cyberspace layer
    /**
     * Enemy archetype catalog. Each archetype defines a distinct combat profile
     * (tank, glass-cannon, shield-heavy, healer, debuffer...) with stat multipliers,
     * optional starting status effects, and a unique ASCII portrait.
     */
    data class EnemyArchetype(
        val name: String,
        val description: String,
        val asciiArt: String,
        val minTier: Int,          // minimum depth/layer at which this enemy can appear
        val hpMult: Float = 1.0f,
        val shieldMult: Float = 1.0f,
        val dmgMult: Float = 1.0f,
        val armorBonus: Int = 0,
        val bountyMult: Float = 1.0f,
        val statusEffects: List<Pair<StatusEffectType, Int>> = emptyList() // (type, turns)
    )

    private val ENEMY_ARCHETYPES = listOf(
        // ---------- TIER 1 : Corporate Lobby Daemons (Building floors 1-2) ----------
        EnemyArchetype(
            name = "Worm.exe",
            description = "A fast-replicating data worm gnawing through transmission channels. Weak but persistent.",
            asciiArt = "  ~o~~~~~o~~\n (  o  _  o )\n  ~~o~~~~~o~",
            minTier = 1, dmgMult = 1.15f, bountyMult = 0.9f
        ),
        EnemyArchetype(
            name = "Spyware.dll",
            description = "Stealthy surveillance daemon that siphons RAM and exposes system weaknesses.",
            asciiArt = "  /-------\\\n < (o) (o) >\n  \\_  ^  _/",
            minTier = 1, shieldMult = 0.8f, dmgMult = 1.1f,
            statusEffects = listOf(StatusEffectType.WEAKENED to 2)
        ),
        EnemyArchetype(
            name = "Trojan.Horse",
            description = "A disguised intrusion script that appears harmless until deep inside the network.",
            asciiArt = "  ,_____\n /_ _ _ \\\n |o|   |o|\n |_______|",
            minTier = 1, dmgMult = 1.25f, armorBonus = 2
        ),
        EnemyArchetype(
            name = "ScriptKiddie.Bot",
            description = "An amateur automated script, clumsy but erratic and unpredictable.",
            asciiArt = "  (>_<)\n  (o o)\n  (_|_)",
            minTier = 1, hpMult = 0.8f, dmgMult = 1.0f, bountyMult = 1.1f
        ),

        // ---------- TIER 2 : Building Core / Reactor ----------
        EnemyArchetype(
            name = "LogicBomb.sh",
            description = "A dormant payload rigged to detonate with devastating cascading corruption.",
            asciiArt = "   _\\|/_\n  ( o_o )\n  (_____) ",
            minTier = 2, dmgMult = 1.4f,
            statusEffects = listOf(StatusEffectType.POISONED to 3)
        ),
        EnemyArchetype(
            name = "Ransomware.crypt",
            description = "Locks your core files and demands credits. Armor-tough encryption shell.",
            asciiArt = "  [Locked]\n  [ O_O  ]\n  [=====_]",
            minTier = 2, armorBonus = 3, hpMult = 1.2f, bountyMult = 1.2f
        ),
        EnemyArchetype(
            name = "Rootkit.sys",
            description = "Hides deep in the operating system and erodes your defenses from within.",
            asciiArt = "   /\\_/\\\n  ( >.< )\n   =(I)=",
            minTier = 2, shieldMult = 1.3f, dmgMult = 1.15f,
            statusEffects = listOf(StatusEffectType.WEAKENED to 1)
        ),
        EnemyArchetype(
            name = "Firewall Guardian",
            description = "A sentry program reinforced with heavy subdermal-plated shielding.",
            asciiArt = "  [GUARD]\n  |=o o=|\n  |_____|",
            minTier = 2, shieldMult = 1.6f, hpMult = 1.3f, armorBonus = 2, dmgMult = 0.9f
        ),

        // ---------- TIER 3 : Collector Sub-Grid ----------
        EnemyArchetype(
            name = "ZombieBot.bin",
            description = "A thrall daemon under external control, relentless but mentally fragmented.",
            asciiArt = "  [Z][Z]\n  ( x_x )\n  (_|_|_)",
            minTier = 3, hpMult = 1.4f, dmgMult = 1.2f,
            statusEffects = listOf(StatusEffectType.BUFFED to 2)
        ),
        EnemyArchetype(
            name = "VampirePacket.sys",
            description = "Drains your RAM reserves to fuel its own corrupted data stream.",
            asciiArt = "  \\\\____//\n   ( o_o )\n  __|___|__",
            minTier = 3, hpMult = 0.9f, dmgMult = 1.25f, shieldMult = 1.2f
        ),
        EnemyArchetype(
            name = "Adware Construct",
            description = "A bloated, loud daemon that hammers you with overwhelming corrupt spam.",
            asciiArt = "  [AD!]\n  ( O_o )\n  |>_<|",
            minTier = 3, hpMult = 1.3f, dmgMult = 1.35f, bountyMult = 1.3f
        ),
        EnemyArchetype(
            name = "Scav-Killer.exe",
            description = "A hardened hunter that preys on weaker black-ice, relentless and fast.",
            asciiArt = "   ,___,\n  < o o >\n   \\_|_/",
            minTier = 3, dmgMult = 1.5f, armorBonus = 2, hpMult = 1.1f
        ),

        // ---------- TIER 4 : City Metro Districts ----------
        EnemyArchetype(
            name = "Cryptolocker.Baron",
            description = "An elite ransomware general that enslaves your files for massive ransoms.",
            asciiArt = " [BARON]\n [  ]|[ ]\n |_____|",
            minTier = 4, hpMult = 1.6f, armorBonus = 4, bountyMult = 1.5f
        ),
        EnemyArchetype(
            name = "IceWyrm.sys",
            description = "A colossal serpentine ICE construct that coils and strikes with venomous payloads.",
            asciiArt = " ~~~~~~\n<((( )))>\n ~~~^~~~",
            minTier = 4, hpMult = 1.5f, dmgMult = 1.4f, shieldMult = 1.2f,
            statusEffects = listOf(StatusEffectType.POISONED to 4)
        ),
        EnemyArchetype(
            name = "Overlord Lieutenant",
            description = "A Daemon-Overlord sub-commandant that buffs any black-ice around it.",
            asciiArt = " [LIEUT]\n  ( O,O )\n  |_____|",
            minTier = 4, hpMult = 1.7f, shieldMult = 1.5f, dmgMult = 1.3f,
            statusEffects = listOf(StatusEffectType.BUFFED to 3)
        ),
        EnemyArchetype(
            name = "Synthwraith.exe",
            description = "A fragment of a long-dead netrunner, glitching between dimensions and stacking curses.",
            asciiArt = "  ( ~ ~ )\n  <  o  >\n   /|_|\\",
            minTier = 4, hpMult = 0.8f, dmgMult = 1.6f, shieldMult = 1.4f,
            statusEffects = listOf(StatusEffectType.WEAKENED to 3, StatusEffectType.POISONED to 2)
        ),
        EnemyArchetype(
            name = "BlackICE Berserker",
            description = "A rage-maddened ICE unit that forgoes defense for pure, overwhelming offense.",
            asciiArt = "  [ RAGE ]\n  ( >_< )\n  ((:=))",
            minTier = 4, dmgMult = 1.8f, hpMult = 1.2f, shieldMult = 0.6f,
            statusEffects = listOf(StatusEffectType.BUFFED to 2)
        )
    )

    /**
     * Mod-registered enemy archetypes dynamically added at runtime.
     * Populated by [ContentRegistry] when a mod document is loaded.
     */
    val registeredEnemyArchetypes = mutableListOf<EnemyArchetype>()

    /** Registers additional enemy archetypes from mods. */
    fun registerEnemyArchetypes(archetypes: List<EnemyArchetype>) {
        registeredEnemyArchetypes.addAll(archetypes)
    }

    /**
     * Spawns a procedurally chosen enemy scaled to the current depth/layer.
     * Higher tiers (and tougher archetypes) unlock as the player descends.
     * Mod-registered archetypes (via [registerEnemyArchetypes]) are merged in.
     */
    fun spawnEnemy(layer: Int): Enemy {
        val random = Random(System.currentTimeMillis())
        val effectiveLayer = layer.coerceAtLeast(1)

        // Merge hardcoded catalog with any mod-registered archetypes.
        val pool = ENEMY_ARCHETYPES + registeredEnemyArchetypes

        // Only spawn archetypes whose minTier <= effectiveLayer; weight by staleness to
        // favour newer/threatening archetypes while keeping variety.
        val candidates = pool.filter { it.minTier <= effectiveLayer }
        val chosen = if (candidates.isEmpty()) pool.first()
        else {
            val weighted = mutableListOf<EnemyArchetype>()
            for (c in candidates) {
                val weight = 2 + (effectiveLayer - c.minTier)
                repeat(weight) { weighted.add(c) }
            }
            weighted[random.nextInt(weighted.size)]
        }

        val integrity = ((40 + (effectiveLayer * 15)) * chosen.hpMult).toInt() + random.nextInt(15)
        val shield = ((15 + (effectiveLayer * 10)) * chosen.shieldMult).toInt() + random.nextInt(10)
        val damage = ((8 + (effectiveLayer * 4)) * chosen.dmgMult).toInt() + random.nextInt(5)
        val armor = (effectiveLayer + chosen.armorBonus) + random.nextInt(2)
        val bounty = ((50 + (effectiveLayer * 25)) * chosen.bountyMult).toInt() + random.nextInt(30)

        val startingEffects = chosen.statusEffects.map { (type, turns) ->
            ActiveStatusEffect(type = type, turnsRemaining = turns, sourceName = chosen.name)
        }

        return Enemy(
            id = "enemy_${System.currentTimeMillis()}_${random.nextInt(10000)}",
            name = chosen.name,
            maxIntegrity = integrity,
            integrity = integrity,
            maxShield = shield,
            shield = shield,
            damage = damage,
            armor = armor,
            iconAscii = chosen.asciiArt,
            bountyCredits = bounty,
            description = chosen.description,
            statusEffects = startingEffects.toMutableList()
        )
    }

    fun spawnBoss(bossType: BossType, level: Int): Enemy {
        return when (bossType) {
            BossType.FIREWALL_SENTINEL -> Enemy(
                id = "boss_sentinel_${System.currentTimeMillis()}",
                name = "Firewall Sentinel",
                maxIntegrity = 250 + (level * 20),
                integrity = 250 + (level * 20),
                maxShield = 100 + (level * 15),
                shield = 100 + (level * 15),
                damage = 22 + (level * 3),
                armor = 8 + level,
                iconAscii = "   _______\n  | SENT |\n  |inel._|\n  |_______|\n  /||\\ ||\\\n / ||\\ || \\",
                bountyCredits = 400 + (level * 60),
                description = "Ancient defensive sub-routine guarding the deepest corporate firewalls. Regenerates shields and locks down systems.",
                isBoss = true,
                bossType = BossType.FIREWALL_SENTINEL
            )
            BossType.DAEMON_OVERLORD -> Enemy(
                id = "boss_overlord_${System.currentTimeMillis()}",
                name = "Daemon Overlord",
                maxIntegrity = 350 + (level * 25),
                integrity = 350 + (level * 25),
                maxShield = 80 + (level * 10),
                shield = 80 + (level * 10),
                damage = 30 + (level * 4),
                armor = 6 + level,
                iconAscii = "   .d8888.\n  d88' '88b\n  88     88\n  Y8b   d8P\n   Y8888P'\n    '||'\n    [OVERLORD]",
                bountyCredits = 600 + (level * 80),
                description = "Supreme daemon ruling the collector sub-grid. Summons lesser daemons and drains neural resources.",
                isBoss = true,
                bossType = BossType.DAEMON_OVERLORD
            )
            BossType.BLACK_ICE_COLOSSUS -> Enemy(
                id = "boss_colossus_${System.currentTimeMillis()}",
                name = "Black ICE Colossus",
                maxIntegrity = 500 + (level * 30),
                integrity = 500 + (level * 30),
                maxShield = 150 + (level * 20),
                shield = 150 + (level * 20),
                damage = 42 + (level * 5),
                armor = 12 + (level * 2),
                iconAscii = "  _________\n |  BLACK  |\n |   ICE   |\n | COLOSSUS|\n |_________|\n  |||   |||\n  |||   |||\n  ===   ===",
                bountyCredits = 1000 + (level * 100),
                description = "Apex security construct of the Metro Core. Adapts defenses and unleashes devastating neural storms.",
                isBoss = true,
                bossType = BossType.BLACK_ICE_COLOSSUS
            )
        }
    }

    // Hacking puzzle matrix generator
    fun generateHackingPuzzle(difficulty: Int): HackingPuzzle {
        val random = Random(System.currentTimeMillis())
        val hexPool = listOf("1C", "E9", "55", "BD", "7A", "FF")
        val size = 5

        // Fill grid
        val grid = Array(size) { Array(size) { hexPool[random.nextInt(hexPool.size)] } }

        // Generate a valid solution of length (difficulty + 2)
        val solutionLength = difficulty + 2
        val path = mutableListOf<Pair<Int, Int>>()

        var curRow = 0
        var curCol = random.nextInt(size)
        path.add(Pair(curRow, curCol))

        var isHorizontal = false // Horizontal is next since we chose a column in row 0.

        for (step in 1 until solutionLength) {
            if (isHorizontal) {
                // Next step in the same row, select a column
                val availableCols = (0 until size).filter { col -> !path.contains(Pair(curRow, col)) }
                if (availableCols.isEmpty()) break
                curCol = availableCols[random.nextInt(availableCols.size)]
                path.add(Pair(curRow, curCol))
            } else {
                // Next step in the same column, select a row
                val availableRows = (0 until size).filter { row -> !path.contains(Pair(row, curCol)) }
                if (availableRows.isEmpty()) break
                curRow = availableRows[random.nextInt(availableRows.size)]
                path.add(Pair(curRow, curCol))
            }
            isHorizontal = !isHorizontal
        }

        // Target sequence is the characters at the path
        val targetSequence = path.map { grid[it.first][it.second] }

        return HackingPuzzle(
            grid = grid,
            targetSequence = targetSequence,
            bufferLimit = 5 + difficulty
        )
    }
}

class CharCanvas(val rows: Int, val cols: Int) {
    private val buffer = Array(rows) { CharArray(cols) { ' ' } }

    fun set(r: Int, c: Int, ch: Char) {
        if (r in 0 until rows && c in 0 until cols) {
            buffer[r][c] = ch
        }
    }

    fun drawLine(r1: Int, c1: Int, r2: Int, c2: Int, ch: Char) {
        val dr = abs(r2 - r1)
        val dc = abs(c2 - c1)
        val sr = if (r1 < r2) 1 else -1
        val sc = if (c1 < c2) 1 else -1
        var err = dr - dc
        var r = r1
        var c = c1
        while (true) {
            set(r, c, ch)
            if (r == r2 && c == c2) break
            val e2 = 2 * err
            if (e2 > -dc) {
                err -= dc
                r += sr
            }
            if (e2 < dr) {
                err += dr
                c += sc
            }
        }
    }

    fun render(): String {
        return buffer.joinToString("\n") { String(it) }
    }
}

data class HackingPuzzle(
    val grid: Array<Array<String>>,
    val targetSequence: List<String>,
    val bufferLimit: Int,
    val selectedIndices: List<Pair<Int, Int>> = emptyList(),
    val currentBuffer: List<String> = emptyList(),
    var isSolved: Boolean = false,
    var isFailed: Boolean = false,
    var highlightedRow: Int? = 0, // Starts at row 0 highlighted
    var highlightedCol: Int? = null
)
