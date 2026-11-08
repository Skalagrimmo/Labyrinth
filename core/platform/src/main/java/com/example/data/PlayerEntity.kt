package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing the Player's complete persistent state,
 * including health, stamina, energy pools, and 2D grid position coordinates
 * linked directly to the floor/grid database maps.
 */
@Entity(
    tableName = "players",
    indices = [
        Index(value = ["saveSlotId"], unique = true),
        Index(value = ["mapId"]),
        Index(value = ["gridX", "gridY"]),
        Index(value = ["saveSlotId", "levelNumber", "floorIndex"])
    ]
)
data class PlayerEntity(
    @PrimaryKey val playerId: String = "player_current_save",
    val saveSlotId: String = "current_save",
    val name: String = "V-Runner",
    val runnerClass: String = "NETRUNNER",

    // Vitals & Energy Pools
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stamina: Int = 100,
    val maxStamina: Int = 100,
    val staminaRecoveryRate: Int = 5,
    val shield: Int = 0,
    val maxShield: Int = 50,
    val ram: Int = 12,
    val maxRam: Int = 12,
    val ramRecoveryRate: Int = 2,

    // 2D Grid Coordinates & Spatial Anchors
    val mapId: String = "current_save_L1_F0",
    val levelNumber: Int = 1,
    val floorIndex: Int = 0,
    val gridX: Int = 1,
    val gridY: Int = 1,
    val facingDirection: String = "NORTH", // "NORTH", "SOUTH", "EAST", "WEST"
    val currentZone: String = "BUILDING",

    // Attributes & Combat Modifiers
    val level: Int = 1,
    val experience: Int = 0,
    val xpToNextLevel: Int = 100,
    val credits: Int = 150,
    val attackPower: Int = 15,
    val defense: Int = 5,
    val speed: Int = 1,
    val visionRadius: Int = 6,
    val isAlive: Boolean = true,

    // Movement & Gameplay Telemetry
    val stepsTaken: Int = 0,
    val turnsElapsed: Int = 0,
    val nodesHackedCount: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
