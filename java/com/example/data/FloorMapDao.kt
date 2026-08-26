package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorMapDao {

    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId ORDER BY levelNumber ASC, floorIndex ASC")
    fun getAllFloorMaps(saveSlotId: String = "current_save"): Flow<List<FloorMapEntity>>

    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber ORDER BY floorIndex ASC")
    fun getFloorMapsForLevel(saveSlotId: String = "current_save", levelNumber: Int): Flow<List<FloorMapEntity>>

    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber ORDER BY floorIndex ASC")
    suspend fun getFloorMapsForLevelSync(saveSlotId: String = "current_save", levelNumber: Int): List<FloorMapEntity>

    @Query("SELECT * FROM floor_maps WHERE mapId = :mapId LIMIT 1")
    fun getFloorMapById(mapId: String): Flow<FloorMapEntity?>

    @Query("SELECT * FROM floor_maps WHERE mapId = :mapId LIMIT 1")
    suspend fun getFloorMapByIdSync(mapId: String): FloorMapEntity?

    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber AND floorIndex = :floorIndex LIMIT 1")
    fun getFloorMap(saveSlotId: String = "current_save", levelNumber: Int, floorIndex: Int): Flow<FloorMapEntity?>

    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber AND floorIndex = :floorIndex LIMIT 1")
    suspend fun getFloorMapSync(saveSlotId: String = "current_save", levelNumber: Int, floorIndex: Int): FloorMapEntity?

    @Transaction
    @Query("SELECT * FROM floor_maps WHERE mapId = :mapId LIMIT 1")
    fun getFloorMapWithObstacles(mapId: String): Flow<FloorMapWithObstacles?>

    @Transaction
    @Query("SELECT * FROM floor_maps WHERE mapId = :mapId LIMIT 1")
    suspend fun getFloorMapWithObstaclesSync(mapId: String): FloorMapWithObstacles?

    @Transaction
    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber ORDER BY floorIndex ASC")
    fun getLevelFloorMapsWithObstacles(saveSlotId: String = "current_save", levelNumber: Int): Flow<List<FloorMapWithObstacles>>

    @Transaction
    @Query("SELECT * FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber ORDER BY floorIndex ASC")
    suspend fun getLevelFloorMapsWithObstaclesSync(saveSlotId: String = "current_save", levelNumber: Int): List<FloorMapWithObstacles>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFloorMap(floorMap: FloorMapEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFloorMaps(floorMaps: List<FloorMapEntity>)

    @Update
    suspend fun updateFloorMap(floorMap: FloorMapEntity)

    @Query("UPDATE floor_maps SET playerX = :playerX, playerY = :playerY, playerFloor = :playerFloor, playerDirection = :direction, lastUpdatedTimestamp = :timestamp WHERE mapId = :mapId")
    suspend fun updatePlayerCoordinates(
        mapId: String,
        playerX: Int,
        playerY: Int,
        playerFloor: Int,
        direction: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE floor_maps SET exploredData = :exploredData, exploredTilesCount = :exploredCount, isFullyExplored = :isFullyExplored, lastUpdatedTimestamp = :timestamp WHERE mapId = :mapId")
    suspend fun updateExploration(
        mapId: String,
        exploredData: String,
        exploredCount: Int,
        isFullyExplored: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE floor_maps SET isCleared = :isCleared, lastUpdatedTimestamp = :timestamp WHERE mapId = :mapId")
    suspend fun setFloorCleared(mapId: String, isCleared: Boolean, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteFloorMap(floorMap: FloorMapEntity)

    @Query("DELETE FROM floor_maps WHERE saveSlotId = :saveSlotId")
    suspend fun deleteFloorMapsForSlot(saveSlotId: String = "current_save")

    @Query("DELETE FROM floor_maps WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber")
    suspend fun deleteFloorMapsForLevel(saveSlotId: String = "current_save", levelNumber: Int)
}

@Dao
interface FloorObstacleDao {

    @Query("SELECT * FROM floor_obstacles WHERE mapId = :mapId ORDER BY gridY ASC, gridX ASC")
    fun getObstaclesForMap(mapId: String): Flow<List<FloorObstacleEntity>>

    @Query("SELECT * FROM floor_obstacles WHERE mapId = :mapId ORDER BY gridY ASC, gridX ASC")
    suspend fun getObstaclesForMapSync(mapId: String): List<FloorObstacleEntity>

    @Query("SELECT * FROM floor_obstacles WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber AND floorIndex = :floorIndex ORDER BY gridY ASC, gridX ASC")
    fun getObstaclesForFloor(saveSlotId: String = "current_save", levelNumber: Int, floorIndex: Int): Flow<List<FloorObstacleEntity>>

    @Query("SELECT * FROM floor_obstacles WHERE mapId = :mapId AND gridX = :x AND gridY = :y LIMIT 1")
    fun getObstacleAt(mapId: String, x: Int, y: Int): Flow<FloorObstacleEntity?>

    @Query("SELECT * FROM floor_obstacles WHERE mapId = :mapId AND gridX = :x AND gridY = :y LIMIT 1")
    suspend fun getObstacleAtSync(mapId: String, x: Int, y: Int): FloorObstacleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObstacle(obstacle: FloorObstacleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObstacles(obstacles: List<FloorObstacleEntity>)

    @Update
    suspend fun updateObstacle(obstacle: FloorObstacleEntity)

    @Query("UPDATE floor_obstacles SET isPassable = :isPassable, isHacked = :isHacked, isDisarmed = :isDisarmed, durability = :durability WHERE id = :id")
    suspend fun updateObstacleState(
        id: Long,
        isPassable: Boolean,
        isHacked: Boolean,
        isDisarmed: Boolean,
        durability: Int
    )

    @Delete
    suspend fun deleteObstacle(obstacle: FloorObstacleEntity)

    @Query("DELETE FROM floor_obstacles WHERE id = :id")
    suspend fun deleteObstacleById(id: Long)

    @Query("DELETE FROM floor_obstacles WHERE mapId = :mapId")
    suspend fun deleteObstaclesForMap(mapId: String)

    @Query("DELETE FROM floor_obstacles WHERE saveSlotId = :saveSlotId")
    suspend fun deleteObstaclesForSlot(saveSlotId: String = "current_save")

    @Query("DELETE FROM floor_obstacles WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber")
    suspend fun deleteObstaclesForLevel(saveSlotId: String = "current_save", levelNumber: Int)
}

@Dao
interface PlayerMapPositionDao {

    @Query("SELECT * FROM player_map_positions WHERE saveSlotId = :saveSlotId LIMIT 1")
    fun getPlayerPosition(saveSlotId: String = "current_save"): Flow<PlayerMapPositionEntity?>

    @Query("SELECT * FROM player_map_positions WHERE saveSlotId = :saveSlotId LIMIT 1")
    suspend fun getPlayerPositionSync(saveSlotId: String = "current_save"): PlayerMapPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayerPosition(position: PlayerMapPositionEntity)

    @Update
    suspend fun updatePlayerPosition(position: PlayerMapPositionEntity)

    @Query("UPDATE player_map_positions SET levelNumber = :levelNumber, floorIndex = :floorIndex, gridX = :gridX, gridY = :gridY, facingDirection = :direction, stepsTaken = stepsTaken + 1, lastMovedTimestamp = :timestamp WHERE saveSlotId = :saveSlotId")
    suspend fun movePlayer(
        saveSlotId: String = "current_save",
        levelNumber: Int,
        floorIndex: Int,
        gridX: Int,
        gridY: Int,
        direction: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM player_map_positions WHERE saveSlotId = :saveSlotId")
    suspend fun deletePlayerPosition(saveSlotId: String = "current_save")
}
