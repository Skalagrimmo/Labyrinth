package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Represents a single 2D grid-based floor map for a level in the roguelike system.
 */
@Entity(
    tableName = "floor_maps",
    indices = [
        Index(value = ["saveSlotId", "levelNumber", "floorIndex"], unique = true),
        Index(value = ["saveSlotId", "levelNumber"])
    ]
)
data class FloorMapEntity(
    @PrimaryKey val mapId: String, // e.g. "current_save_L1_F0"
    val saveSlotId: String = "current_save",
    val levelNumber: Int,
    val floorIndex: Int,
    val floorName: String,
    val sectorName: String,
    val districtTheme: String = "CORPORATE_TOWER",
    val width: Int = 15,
    val height: Int = 15,
    val spawnFloor: Int = 0,
    val spawnX: Int = 1,
    val spawnY: Int = 1,
    val exitFloor: Int = 0,
    val exitX: Int = 13,
    val exitY: Int = 13,
    val playerX: Int = 1,
    val playerY: Int = 1,
    val playerFloor: Int = 0,
    val playerDirection: String = "NORTH",
    val gridData: String, // Serialized cell types string (e.g. CSV or char matrix)
    val exploredData: String = "", // Serialized exploration boolean mask
    val securityLevel: Int = 1,
    val isCleared: Boolean = false,
    val isFullyExplored: Boolean = false,
    val totalWalkableTiles: Int = 0,
    val exploredTilesCount: Int = 0,
    val totalObstacles: Int = 0,
    val clearedObstacles: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Represents an obstacle or interactive barrier on a floor map.
 */
@Entity(
    tableName = "floor_obstacles",
    indices = [
        Index(value = ["mapId", "gridX", "gridY"]),
        Index(value = ["saveSlotId", "levelNumber", "floorIndex"])
    ]
)
data class FloorObstacleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mapId: String,
    val saveSlotId: String = "current_save",
    val levelNumber: Int,
    val floorIndex: Int,
    val gridX: Int,
    val gridY: Int,
    val obstacleType: String, // FIREWALL, LASER_GRID, SECURITY_GATE, DEBRIS, VIRUS_NODE, LOCKED_TERMINAL, STEAM_VENT, CRYPTO_LOCKER
    val name: String,
    val description: String = "",
    val isPassable: Boolean = false,
    val isDestructible: Boolean = true,
    val isHacked: Boolean = false,
    val isDisarmed: Boolean = false,
    val durability: Int = 100,
    val maxDurability: Int = 100,
    val hackDifficulty: Int = 1,
    val interactionPrompt: String = "Hack Security Barrier",
    val rewardCredits: Int = 0,
    val rewardItem: String? = null,
    val createdTimestamp: Long = System.currentTimeMillis()
)

/**
 * Represents the persistent tracking of player coordinates and movement state across floors.
 */
@Entity(
    tableName = "player_map_positions",
    indices = [
        Index(value = ["saveSlotId"])
    ]
)
data class PlayerMapPositionEntity(
    @PrimaryKey val saveSlotId: String = "current_save",
    val levelNumber: Int = 1,
    val floorIndex: Int = 0,
    val gridX: Int = 1,
    val gridY: Int = 1,
    val facingDirection: String = "NORTH",
    val currentZone: String = "BUILDING",
    val stepsTaken: Int = 0,
    val totalFloorsExplored: Int = 1,
    val totalObstaclesBypassed: Int = 0,
    val exploredTilesHistoryCsv: String = "",
    val lastMovedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Relational Room composite pairing a Floor Map with all its contained Obstacles.
 */
data class FloorMapWithObstacles(
    @Embedded val floorMap: FloorMapEntity,
    @Relation(
        parentColumn = "mapId",
        entityColumn = "mapId"
    )
    val obstacles: List<FloorObstacleEntity>
)
