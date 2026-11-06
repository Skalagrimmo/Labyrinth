package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Coordinate data container pairing Player coordinates with live NPC coordinates on a floor.
 */
data class FloorEntitiesCoordinates(
    val playerX: Int,
    val playerY: Int,
    val playerFacing: String,
    val npcs: List<GridEntityCoordinateEntity>
)

/**
 * Room Data Access Object (DAO) specifically managing retrieval and updates
 * for both Player coordinates and NPC/enemy coordinates across 2D floor maps.
 */
@Dao
interface PlayerNpcCoordinatesDao {

    // --- Player Coordinate Retrieval ---

    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    fun observePlayerCoordinates(playerId: String = "player_current_save"): Flow<PlayerEntity?>

    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    suspend fun getPlayerCoordinates(playerId: String = "player_current_save"): PlayerEntity?

    @Query("SELECT * FROM players WHERE saveSlotId = :saveSlotId LIMIT 1")
    suspend fun getPlayerCoordinatesBySlot(saveSlotId: String = "current_save"): PlayerEntity?

    // --- NPC / Entity Coordinate Retrieval ---

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId AND isAliveOrActive = 1 ORDER BY entityName ASC")
    fun observeNpcCoordinates(mapId: String): Flow<List<GridEntityCoordinateEntity>>

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId AND isAliveOrActive = 1 ORDER BY entityName ASC")
    suspend fun getNpcCoordinates(mapId: String): List<GridEntityCoordinateEntity>

    @Query("SELECT * FROM grid_entity_coordinates WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber AND floorIndex = :floorIndex AND isAliveOrActive = 1")
    suspend fun getNpcsForFloor(saveSlotId: String = "current_save", levelNumber: Int, floorIndex: Int): List<GridEntityCoordinateEntity>

    @Query("SELECT * FROM grid_entity_coordinates WHERE entityId = :entityId LIMIT 1")
    suspend fun getNpcById(entityId: String): GridEntityCoordinateEntity?

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId AND coordinateX = :x AND coordinateY = :y AND isAliveOrActive = 1")
    suspend fun getNpcsAtCoordinate(mapId: String, x: Int, y: Int): List<GridEntityCoordinateEntity>

    // --- Combined Floor Coordinates Retrieval ---

    @Transaction
    suspend fun getFloorEntitiesCoordinates(
        mapId: String,
        playerId: String = "player_current_save"
    ): FloorEntitiesCoordinates {
        val player = getPlayerCoordinates(playerId)
        val npcs = getNpcCoordinates(mapId)
        return FloorEntitiesCoordinates(
            playerX = player?.gridX ?: 1,
            playerY = player?.gridY ?: 1,
            playerFacing = player?.facingDirection ?: "NORTH",
            npcs = npcs
        )
    }

    // --- Player Coordinate Updates ---

    @Query("UPDATE players SET gridX = :newX, gridY = :newY, facingDirection = :facingDirection, stepsTaken = stepsTaken + 1, turnsElapsed = turnsElapsed + 1, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun updatePlayerPosition(
        playerId: String = "player_current_save",
        newX: Int,
        newY: Int,
        facingDirection: String = "NORTH",
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE players SET gridX = :newX, gridY = :newY, mapId = :mapId, levelNumber = :levelNumber, floorIndex = :floorIndex, facingDirection = :facingDirection, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun transferPlayerToFloor(
        playerId: String = "player_current_save",
        mapId: String,
        levelNumber: Int,
        floorIndex: Int,
        newX: Int,
        newY: Int,
        facingDirection: String = "NORTH",
        timestamp: Long = System.currentTimeMillis()
    )

    // --- NPC / Entity Coordinate Updates ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNpc(npc: GridEntityCoordinateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNpcs(npcs: List<GridEntityCoordinateEntity>)

    @Update
    suspend fun updateNpc(npc: GridEntityCoordinateEntity)

    @Query("UPDATE grid_entity_coordinates SET coordinateX = :newX, coordinateY = :newY, facingDirection = :facingDirection, lastMovedTimestamp = :timestamp WHERE entityId = :entityId")
    suspend fun updateNpcPosition(
        entityId: String,
        newX: Int,
        newY: Int,
        facingDirection: String = "NORTH",
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE grid_entity_coordinates SET coordinateX = :newX, coordinateY = :newY, alertLevel = :alertLevel, facingDirection = :facingDirection, lastMovedTimestamp = :timestamp WHERE entityId = :entityId")
    suspend fun updateNpcMovementAndAlert(
        entityId: String,
        newX: Int,
        newY: Int,
        alertLevel: String,
        facingDirection: String = "NORTH",
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE grid_entity_coordinates SET isAliveOrActive = :isActive, health = :health, lastMovedTimestamp = :timestamp WHERE entityId = :entityId")
    suspend fun updateNpcStatus(
        entityId: String,
        isActive: Boolean,
        health: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteNpc(npc: GridEntityCoordinateEntity)

    @Query("DELETE FROM grid_entity_coordinates WHERE entityId = :entityId")
    suspend fun deleteNpcById(entityId: String)

    @Query("DELETE FROM grid_entity_coordinates WHERE mapId = :mapId AND entityCategory != 'PLAYER'")
    suspend fun clearNpcsForMap(mapId: String)
}
