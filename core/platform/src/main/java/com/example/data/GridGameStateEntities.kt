package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Represents the 2D grid dimensions, boundaries, and spatial configuration of the active game map.
 */
@Entity(
    tableName = "grid_map_state",
    indices = [
        Index(value = ["saveSlotId", "levelNumber", "floorIndex"], unique = true)
    ]
)
data class GridMapStateEntity(
    @PrimaryKey val mapId: String, // e.g. "slot_1_L1_F0"
    val saveSlotId: String = "current_save",
    val levelNumber: Int = 1,
    val floorIndex: Int = 0,
    val gridWidth: Int = 15,
    val gridHeight: Int = 15,
    val minBoundX: Int = 0,
    val minBoundY: Int = 0,
    val maxBoundX: Int = 14,
    val maxBoundY: Int = 14,
    val defaultTileType: String = "WALL",
    val theme: String = "CYBERPUNK_CORRIDOR",
    val spawnX: Int = 1,
    val spawnY: Int = 1,
    val exitX: Int = 13,
    val exitY: Int = 13,
    val totalTiles: Int = 225,
    val walkableTilesCount: Int = 0,
    val serializedGridTiles: String = "",
    val serializedExploredMask: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Represents discrete live spatial entity coordinates on the 2D grid
 * (e.g. Player, Enemies, Security Turrets, NPCs, Loot Caches, Obstacles).
 */
@Entity(
    tableName = "grid_entity_coordinates",
    indices = [
        Index(value = ["mapId", "coordinateX", "coordinateY"]),
        Index(value = ["mapId", "entityCategory"]),
        Index(value = ["saveSlotId", "entityId"], unique = true)
    ]
)
data class GridEntityCoordinateEntity(
    @PrimaryKey val entityId: String, // e.g. "player_current_save", "enemy_L1_F0_drone_1"
    val mapId: String,
    val saveSlotId: String = "current_save",
    val levelNumber: Int = 1,
    val floorIndex: Int = 0,
    val entityName: String,
    val entityCategory: String, // "PLAYER", "ENEMY", "NPC", "OBSTACLE", "TERMINAL", "LOOT_CACHE", "HAZARD"
    val coordinateX: Int,
    val coordinateY: Int,
    val facingDirection: String = "NORTH", // "NORTH", "SOUTH", "EAST", "WEST"
    val isPassable: Boolean = false,
    val isVisibleToPlayer: Boolean = true,
    val isAliveOrActive: Boolean = true,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val speed: Int = 1,
    val visionRadius: Int = 5,
    val alertLevel: String = "UNALERTED", // "UNALERTED", "SUSPICIOUS", "HOSTILE_ENGAGED"
    val metadataPayload: String = "",
    val lastMovedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Composite relational query pairing a grid map's dimensions with all live entity coordinates on that grid.
 */
data class GridMapWithEntities(
    @Embedded val gridMap: GridMapStateEntity,
    @Relation(
        parentColumn = "mapId",
        entityColumn = "mapId"
    )
    val entities: List<GridEntityCoordinateEntity>
)

/**
 * Data Access Object for querying and mutating grid dimensions and spatial boundaries.
 */
@Dao
interface GridMapStateDao {

    @Query("SELECT * FROM grid_map_state WHERE saveSlotId = :saveSlotId ORDER BY levelNumber ASC, floorIndex ASC")
    fun getAllGridMaps(saveSlotId: String = "current_save"): Flow<List<GridMapStateEntity>>

    @Query("SELECT * FROM grid_map_state WHERE mapId = :mapId LIMIT 1")
    fun getGridMapById(mapId: String): Flow<GridMapStateEntity?>

    @Query("SELECT * FROM grid_map_state WHERE mapId = :mapId LIMIT 1")
    suspend fun getGridMapByIdSync(mapId: String): GridMapStateEntity?

    @Query("SELECT * FROM grid_map_state WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber AND floorIndex = :floorIndex LIMIT 1")
    fun getGridMap(saveSlotId: String = "current_save", levelNumber: Int, floorIndex: Int): Flow<GridMapStateEntity?>

    @Query("SELECT * FROM grid_map_state WHERE saveSlotId = :saveSlotId AND levelNumber = :levelNumber AND floorIndex = :floorIndex LIMIT 1")
    suspend fun getGridMapSync(saveSlotId: String = "current_save", levelNumber: Int, floorIndex: Int): GridMapStateEntity?

    @Transaction
    @Query("SELECT * FROM grid_map_state WHERE mapId = :mapId LIMIT 1")
    fun getGridMapWithEntities(mapId: String): Flow<GridMapWithEntities?>

    @Transaction
    @Query("SELECT * FROM grid_map_state WHERE mapId = :mapId LIMIT 1")
    suspend fun getGridMapWithEntitiesSync(mapId: String): GridMapWithEntities?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGridMap(gridMap: GridMapStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGridMaps(gridMaps: List<GridMapStateEntity>)

    @Update
    suspend fun updateGridMap(gridMap: GridMapStateEntity)

    @Query("UPDATE grid_map_state SET gridWidth = :width, gridHeight = :height, maxBoundX = :width - 1, maxBoundY = :height - 1, totalTiles = :width * :height, updatedTimestamp = :timestamp WHERE mapId = :mapId")
    suspend fun updateGridDimensions(
        mapId: String,
        width: Int,
        height: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteGridMap(gridMap: GridMapStateEntity)

    @Query("DELETE FROM grid_map_state WHERE saveSlotId = :saveSlotId")
    suspend fun deleteGridMapsForSlot(saveSlotId: String = "current_save")
}

/**
 * Data Access Object for managing real-time entity coordinates on the 2D grid.
 */
@Dao
interface GridEntityCoordinateDao {

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId AND isAliveOrActive = 1 ORDER BY entityCategory ASC, entityName ASC")
    fun getActiveEntitiesForMap(mapId: String): Flow<List<GridEntityCoordinateEntity>>

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId ORDER BY entityCategory ASC, entityName ASC")
    suspend fun getAllEntitiesForMapSync(mapId: String): List<GridEntityCoordinateEntity>

    @Query("SELECT * FROM grid_entity_coordinates WHERE entityId = :entityId LIMIT 1")
    fun getEntityById(entityId: String): Flow<GridEntityCoordinateEntity?>

    @Query("SELECT * FROM grid_entity_coordinates WHERE entityId = :entityId LIMIT 1")
    suspend fun getEntityByIdSync(entityId: String): GridEntityCoordinateEntity?

    @Query("SELECT * FROM grid_entity_coordinates WHERE saveSlotId = :saveSlotId AND entityCategory = 'PLAYER' LIMIT 1")
    fun getPlayerEntity(saveSlotId: String = "current_save"): Flow<GridEntityCoordinateEntity?>

    @Query("SELECT * FROM grid_entity_coordinates WHERE saveSlotId = :saveSlotId AND entityCategory = 'PLAYER' LIMIT 1")
    suspend fun getPlayerEntitySync(saveSlotId: String = "current_save"): GridEntityCoordinateEntity?

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId AND coordinateX = :x AND coordinateY = :y AND isAliveOrActive = 1")
    fun getEntitiesAtCoordinate(mapId: String, x: Int, y: Int): Flow<List<GridEntityCoordinateEntity>>

    @Query("SELECT * FROM grid_entity_coordinates WHERE mapId = :mapId AND coordinateX = :x AND coordinateY = :y AND isAliveOrActive = 1")
    suspend fun getEntitiesAtCoordinateSync(mapId: String, x: Int, y: Int): List<GridEntityCoordinateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(entity: GridEntityCoordinateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntities(entities: List<GridEntityCoordinateEntity>)

    @Update
    suspend fun updateEntity(entity: GridEntityCoordinateEntity)

    @Query("UPDATE grid_entity_coordinates SET coordinateX = :newX, coordinateY = :newY, facingDirection = :direction, lastMovedTimestamp = :timestamp WHERE entityId = :entityId")
    suspend fun updateEntityCoordinates(
        entityId: String,
        newX: Int,
        newY: Int,
        direction: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE grid_entity_coordinates SET health = :health, isAliveOrActive = :isAlive, lastMovedTimestamp = :timestamp WHERE entityId = :entityId")
    suspend fun updateEntityStatus(
        entityId: String,
        health: Int,
        isAlive: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE grid_entity_coordinates SET alertLevel = :alertLevel WHERE entityId = :entityId")
    suspend fun updateEntityAlertLevel(entityId: String, alertLevel: String)

    @Delete
    suspend fun deleteEntity(entity: GridEntityCoordinateEntity)

    @Query("DELETE FROM grid_entity_coordinates WHERE entityId = :entityId")
    suspend fun deleteEntityById(entityId: String)

    @Query("DELETE FROM grid_entity_coordinates WHERE mapId = :mapId")
    suspend fun deleteEntitiesForMap(mapId: String)

    @Query("DELETE FROM grid_entity_coordinates WHERE saveSlotId = :saveSlotId")
    suspend fun deleteEntitiesForSlot(saveSlotId: String = "current_save")
}
