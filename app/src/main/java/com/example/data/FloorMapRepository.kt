package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository providing clean, reactive Room database persistence and querying
 * for 2D roguelike floor maps, obstacles, cell matrices, and player coordinates.
 */
class FloorMapRepository(
    private val floorMapDao: FloorMapDao,
    private val floorObstacleDao: FloorObstacleDao,
    private val playerMapPositionDao: PlayerMapPositionDao
) {

    // Reactive Flow Observables

    fun getAllFloorMaps(slotId: String = "current_save"): Flow<List<FloorMapEntity>> =
        floorMapDao.getAllFloorMaps(slotId)

    fun getFloorMapsForLevel(levelNumber: Int, slotId: String = "current_save"): Flow<List<FloorMapEntity>> =
        floorMapDao.getFloorMapsForLevel(slotId, levelNumber)

    fun getFloorMapById(mapId: String): Flow<FloorMapEntity?> =
        floorMapDao.getFloorMapById(mapId)

    fun getFloorMap(levelNumber: Int, floorIndex: Int, slotId: String = "current_save"): Flow<FloorMapEntity?> =
        floorMapDao.getFloorMap(slotId, levelNumber, floorIndex)

    fun getFloorMapWithObstacles(mapId: String): Flow<FloorMapWithObstacles?> =
        floorMapDao.getFloorMapWithObstacles(mapId)

    fun getLevelFloorMapsWithObstacles(levelNumber: Int, slotId: String = "current_save"): Flow<List<FloorMapWithObstacles>> =
        floorMapDao.getLevelFloorMapsWithObstacles(slotId, levelNumber)

    fun getObstaclesForMap(mapId: String): Flow<List<FloorObstacleEntity>> =
        floorObstacleDao.getObstaclesForMap(mapId)

    fun getObstaclesForFloor(levelNumber: Int, floorIndex: Int, slotId: String = "current_save"): Flow<List<FloorObstacleEntity>> =
        floorObstacleDao.getObstaclesForFloor(slotId, levelNumber, floorIndex)

    fun getObstacleAt(mapId: String, x: Int, y: Int): Flow<FloorObstacleEntity?> =
        floorObstacleDao.getObstacleAt(mapId, x, y)

    fun getPlayerPosition(slotId: String = "current_save"): Flow<PlayerMapPositionEntity?> =
        playerMapPositionDao.getPlayerPosition(slotId)

    // Synchronous / Suspend Operations

    suspend fun getFloorMapSync(levelNumber: Int, floorIndex: Int, slotId: String = "current_save"): FloorMapEntity? =
        withContext(Dispatchers.IO) {
            floorMapDao.getFloorMapSync(slotId, levelNumber, floorIndex)
        }

    suspend fun getFloorMapByIdSync(mapId: String): FloorMapEntity? =
        withContext(Dispatchers.IO) {
            floorMapDao.getFloorMapByIdSync(mapId)
        }

    suspend fun getFloorMapWithObstaclesSync(mapId: String): FloorMapWithObstacles? =
        withContext(Dispatchers.IO) {
            floorMapDao.getFloorMapWithObstaclesSync(mapId)
        }

    suspend fun getObstaclesForMapSync(mapId: String): List<FloorObstacleEntity> =
        withContext(Dispatchers.IO) {
            floorObstacleDao.getObstaclesForMapSync(mapId)
        }

    suspend fun getPlayerPositionSync(slotId: String = "current_save"): PlayerMapPositionEntity? =
        withContext(Dispatchers.IO) {
            playerMapPositionDao.getPlayerPositionSync(slotId)
        }

    suspend fun saveFloorMap(floorMap: FloorMapEntity) = withContext(Dispatchers.IO) {
        floorMapDao.insertFloorMap(floorMap)
    }

    suspend fun saveFloorMapWithObstacles(
        floorMap: FloorMapEntity,
        obstacles: List<FloorObstacleEntity>
    ) = withContext(Dispatchers.IO) {
        floorMapDao.insertFloorMap(floorMap)
        floorObstacleDao.deleteObstaclesForMap(floorMap.mapId)
        if (obstacles.isNotEmpty()) {
            floorObstacleDao.insertObstacles(obstacles)
        }
    }

    suspend fun saveAllFloorMaps(
        floorMaps: List<FloorMapEntity>,
        allObstacles: List<FloorObstacleEntity>
    ) = withContext(Dispatchers.IO) {
        if (floorMaps.isNotEmpty()) {
            floorMapDao.insertFloorMaps(floorMaps)
        }
        if (allObstacles.isNotEmpty()) {
            floorObstacleDao.insertObstacles(allObstacles)
        }
    }

    suspend fun updatePlayerCoordinates(
        mapId: String,
        playerX: Int,
        playerY: Int,
        playerFloor: Int,
        direction: String
    ) = withContext(Dispatchers.IO) {
        floorMapDao.updatePlayerCoordinates(
            mapId = mapId,
            playerX = playerX,
            playerY = playerY,
            playerFloor = playerFloor,
            direction = direction
        )
    }

    suspend fun updatePlayerPosition(
        slotId: String = "current_save",
        levelNumber: Int,
        floorIndex: Int,
        gridX: Int,
        gridY: Int,
        direction: String
    ) = withContext(Dispatchers.IO) {
        playerMapPositionDao.movePlayer(
            saveSlotId = slotId,
            levelNumber = levelNumber,
            floorIndex = floorIndex,
            gridX = gridX,
            gridY = gridY,
            direction = direction
        )
    }

    suspend fun savePlayerPosition(position: PlayerMapPositionEntity) = withContext(Dispatchers.IO) {
        playerMapPositionDao.insertPlayerPosition(position)
    }

    suspend fun markTileExplored(
        mapId: String,
        gridX: Int,
        gridY: Int
    ) = withContext(Dispatchers.IO) {
        val map = floorMapDao.getFloorMapByIdSync(mapId) ?: return@withContext
        val explored = deserializeExplored(map.exploredData, map.width, map.height)
        if (gridY in 0 until map.height && gridX in 0 until map.width) {
            if (!explored[gridY][gridX]) {
                explored[gridY][gridX] = true
                var count = 0
                for (r in explored) {
                    for (c in r) {
                        if (c) count++
                    }
                }
                val isAllExplored = count >= map.totalWalkableTiles
                val serialized = serializeExplored(explored)
                floorMapDao.updateExploration(
                    mapId = mapId,
                    exploredData = serialized,
                    exploredCount = count,
                    isFullyExplored = isAllExplored
                )
            }
        }
    }

    suspend fun interactOrDamageObstacle(
        obstacleId: Long,
        damage: Int = 0,
        hackSuccess: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val obstacles = floorObstacleDao.getObstaclesForMapSync("")
        // Fetch or update specific obstacle
        // Since obstacle has id:
        var updatedPassable = false
        // Update state based on damage or hack
        if (hackSuccess) {
            floorObstacleDao.updateObstacleState(
                id = obstacleId,
                isPassable = true,
                isHacked = true,
                isDisarmed = true,
                durability = 0
            )
            updatedPassable = true
        } else if (damage > 0) {
            // Find current durability
            floorObstacleDao.updateObstacleState(
                id = obstacleId,
                isPassable = true,
                isHacked = false,
                isDisarmed = true,
                durability = 0
            )
            updatedPassable = true
        }
        updatedPassable
    }

    suspend fun deleteObstacle(id: Long) = withContext(Dispatchers.IO) {
        floorObstacleDao.deleteObstacleById(id)
    }

    suspend fun setFloorCleared(mapId: String, isCleared: Boolean) = withContext(Dispatchers.IO) {
        floorMapDao.setFloorCleared(mapId, isCleared)
    }

    suspend fun clearLevelData(levelNumber: Int, slotId: String = "current_save") = withContext(Dispatchers.IO) {
        floorMapDao.deleteFloorMapsForLevel(slotId, levelNumber)
        floorObstacleDao.deleteObstaclesForLevel(slotId, levelNumber)
    }

    suspend fun clearAllFloorData(slotId: String = "current_save") = withContext(Dispatchers.IO) {
        floorMapDao.deleteFloorMapsForSlot(slotId)
        floorObstacleDao.deleteObstaclesForSlot(slotId)
        playerMapPositionDao.deletePlayerPosition(slotId)
    }

    /**
     * Bridges procedural multi-floor generation directly with Room database persistence.
     */
    suspend fun saveGeneratedMultiFloorLevel(
        level: MultiFloorGridLevel,
        slotId: String = "current_save",
        playerFloor: Int = 0,
        playerX: Int = 1,
        playerY: Int = 1,
        direction: String = "NORTH"
    ) = withContext(Dispatchers.IO) {
        val floorEntities = mutableListOf<FloorMapEntity>()
        val obstacleEntities = mutableListOf<FloorObstacleEntity>()

        level.floors.forEachIndexed { floorIdx, gridFloor ->
            val mapId = "${slotId}_L${level.levelNumber}_F${floorIdx}"
            val gridString = serializeGrid(gridFloor.grid)
            val walkableCount = countWalkableTiles(gridFloor.grid)
            val obstacles = extractObstaclesFromGrid(
                mapId = mapId,
                levelNumber = level.levelNumber,
                floorIndex = floorIdx,
                grid = gridFloor.grid,
                slotId = slotId
            )
            obstacleEntities.addAll(obstacles)

            val initialExplored = Array(gridFloor.height) { Array(gridFloor.width) { false } }
            // If player spawns on this floor, mark spawn tile explored
            if (floorIdx == playerFloor && playerX in 0 until gridFloor.width && playerY in 0 until gridFloor.height) {
                initialExplored[playerY][playerX] = true
            }

            val floorEntity = FloorMapEntity(
                mapId = mapId,
                saveSlotId = slotId,
                levelNumber = level.levelNumber,
                floorIndex = floorIdx,
                floorName = gridFloor.floorName,
                sectorName = level.sectorName,
                districtTheme = level.districtTheme.name,
                width = gridFloor.width,
                height = gridFloor.height,
                spawnFloor = level.spawnPoint.first,
                spawnX = level.spawnPoint.second,
                spawnY = level.spawnPoint.third,
                exitFloor = level.exitPoint.first,
                exitX = level.exitPoint.second,
                exitY = level.exitPoint.third,
                playerX = if (floorIdx == playerFloor) playerX else level.spawnPoint.second,
                playerY = if (floorIdx == playerFloor) playerY else level.spawnPoint.third,
                playerFloor = playerFloor,
                playerDirection = direction,
                gridData = gridString,
                exploredData = serializeExplored(initialExplored),
                securityLevel = gridFloor.securityLevel,
                isCleared = false,
                isFullyExplored = false,
                totalWalkableTiles = walkableCount,
                exploredTilesCount = if (floorIdx == playerFloor) 1 else 0,
                totalObstacles = obstacles.size,
                clearedObstacles = 0
            )
            floorEntities.add(floorEntity)
        }

        // Save maps and obstacles in transaction
        saveAllFloorMaps(floorEntities, obstacleEntities)

        // Save player position
        val playerPos = PlayerMapPositionEntity(
            saveSlotId = slotId,
            levelNumber = level.levelNumber,
            floorIndex = playerFloor,
            gridX = playerX,
            gridY = playerY,
            facingDirection = direction,
            currentZone = "BUILDING",
            stepsTaken = 0,
            totalFloorsExplored = 1,
            totalObstaclesBypassed = 0
        )
        savePlayerPosition(playerPos)
    }

    companion object {

        fun serializeGrid(grid: Array<Array<CellType>>): String {
            return grid.joinToString(";") { row ->
                row.joinToString(",") { it.name }
            }
        }

        fun deserializeGrid(gridData: String, width: Int, height: Int): Array<Array<CellType>> {
            if (gridData.isBlank()) {
                return Array(height) { Array(width) { CellType.WALL } }
            }
            val rows = gridData.split(";")
            return Array(height) { y ->
                val cells = rows.getOrNull(y)?.split(",") ?: emptyList()
                Array(width) { x ->
                    val cellName = cells.getOrNull(x) ?: "WALL"
                    try {
                        CellType.valueOf(cellName)
                    } catch (e: Exception) {
                        CellType.WALL
                    }
                }
            }
        }

        fun serializeExplored(explored: Array<Array<Boolean>>): String {
            return explored.joinToString(";") { row ->
                row.joinToString(",") { if (it) "1" else "0" }
            }
        }

        fun deserializeExplored(exploredData: String, width: Int, height: Int): Array<Array<Boolean>> {
            if (exploredData.isBlank()) {
                return Array(height) { Array(width) { false } }
            }
            val rows = exploredData.split(";")
            return Array(height) { y ->
                val cells = rows.getOrNull(y)?.split(",") ?: emptyList()
                Array(width) { x ->
                    cells.getOrNull(x) == "1"
                }
            }
        }

        fun countWalkableTiles(grid: Array<Array<CellType>>): Int {
            var count = 0
            for (row in grid) {
                for (cell in row) {
                    if (cell != CellType.WALL && cell != CellType.VIRUS_NODE) {
                        count++
                    }
                }
            }
            return count
        }

        fun extractObstaclesFromGrid(
            mapId: String,
            levelNumber: Int,
            floorIndex: Int,
            grid: Array<Array<CellType>>,
            slotId: String
        ): List<FloorObstacleEntity> {
            val list = mutableListOf<FloorObstacleEntity>()
            for (y in grid.indices) {
                for (x in grid[y].indices) {
                    val cell = grid[y][x]
                    val obstacle = when (cell) {
                        CellType.TERMINAL_DOOR -> FloorObstacleEntity(
                            mapId = mapId,
                            saveSlotId = slotId,
                            levelNumber = levelNumber,
                            floorIndex = floorIndex,
                            gridX = x,
                            gridY = y,
                            obstacleType = "SECURITY_GATE",
                            name = "Reinforced Security Gate",
                            description = "Heavy blast door requiring ICE decryption to bypass.",
                            isPassable = false,
                            isDestructible = true,
                            durability = 150,
                            maxDurability = 150,
                            hackDifficulty = 2,
                            interactionPrompt = "Decrypt Blast Gate"
                        )
                        CellType.HACKABLE_TERMINAL -> FloorObstacleEntity(
                            mapId = mapId,
                            saveSlotId = slotId,
                            levelNumber = levelNumber,
                            floorIndex = floorIndex,
                            gridX = x,
                            gridY = y,
                            obstacleType = "LOCKED_TERMINAL",
                            name = "Sub-Sector Access Terminal",
                            description = "Command node controlling local blast barriers and firewall filters.",
                            isPassable = false,
                            isDestructible = false,
                            durability = 100,
                            maxDurability = 100,
                            hackDifficulty = 1,
                            interactionPrompt = "Hack Terminal Matrix",
                            rewardCredits = 75
                        )
                        CellType.SECRET_WALL -> FloorObstacleEntity(
                            mapId = mapId,
                            saveSlotId = slotId,
                            levelNumber = levelNumber,
                            floorIndex = floorIndex,
                            gridX = x,
                            gridY = y,
                            obstacleType = "FIREWALL",
                            name = "Illusory Firewall Partition",
                            description = "Holographic decoy wall concealing a hidden room or cache.",
                            isPassable = false,
                            isDestructible = true,
                            durability = 50,
                            maxDurability = 50,
                            hackDifficulty = 1,
                            interactionPrompt = "Decompile Hologram"
                        )
                        CellType.VIRUS_NODE -> FloorObstacleEntity(
                            mapId = mapId,
                            saveSlotId = slotId,
                            levelNumber = levelNumber,
                            floorIndex = floorIndex,
                            gridX = x,
                            gridY = y,
                            obstacleType = "VIRUS_NODE",
                            name = "Active Black-ICE Node",
                            description = "Hostile virus cluster sending corrupted telemetry packets.",
                            isPassable = false,
                            isDestructible = true,
                            durability = 80,
                            maxDurability = 80,
                            hackDifficulty = 2,
                            interactionPrompt = "Purge Virus Node"
                        )
                        else -> null
                    }
                    if (obstacle != null) {
                        list.add(obstacle)
                    }
                }
            }
            return list
        }
    }
}
