package com.example.data

/**
 * Persistence-independent obstacle snapshot used by turn and movement logic.
 *
 * Lives in :core:persistence because it is part of the persistence boundary's
 * domain vocabulary; the Room entity mapping (FloorObstacleEntity.toDomain)
 * stays in :app next to the Room entities.
 */
data class FloorObstacle(
    val id: Long = 0,
    val mapId: String,
    val saveSlotId: String = "current_save",
    val levelNumber: Int,
    val floorIndex: Int,
    val gridX: Int,
    val gridY: Int,
    val obstacleType: String,
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
    val createdTimestamp: Long = 0L
)
