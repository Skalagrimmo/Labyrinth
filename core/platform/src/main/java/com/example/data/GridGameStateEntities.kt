package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
