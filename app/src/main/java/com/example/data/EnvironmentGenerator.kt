package com.example.data

/**
 * Specialized procedural environments used by the Labyrinth campaign.
 *
 * Kept behavior-identical to the former GameEngine implementations while the rebuild
 * separates generation from session/gameplay orchestration.
 */
object EnvironmentGenerator {
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
}
