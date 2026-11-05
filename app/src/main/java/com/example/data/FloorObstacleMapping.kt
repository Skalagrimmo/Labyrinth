package com.example.data

/**
 * Maps the Room obstacle entity to the persistence-independent FloorObstacle
 * snapshot owned by :core:persistence. The mapping stays in :app because the
 * entity lives here next to the Room database.
 */
fun FloorObstacleEntity.toDomain(): FloorObstacle = FloorObstacle(
    id, mapId, saveSlotId, levelNumber, floorIndex, gridX, gridY, obstacleType, name,
    description, isPassable, isDestructible, isHacked, isDisarmed, durability,
    maxDurability, hackDifficulty, interactionPrompt, rewardCredits, rewardItem, createdTimestamp
)
