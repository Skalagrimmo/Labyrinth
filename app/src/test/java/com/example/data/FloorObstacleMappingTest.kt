package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FloorObstacleMappingTest {
    @Test
    fun `Room obstacle maps to domain without changing persisted values`() {
        val entity = FloorObstacleEntity(
            id = 7, mapId = "L2F1", saveSlotId = "slot", levelNumber = 2, floorIndex = 1,
            gridX = 4, gridY = 5, obstacleType = "LASER_GRID", name = "Laser",
            description = "Security", isPassable = false, isDestructible = false,
            isHacked = true, isDisarmed = false, durability = 80, maxDurability = 120,
            hackDifficulty = 3, interactionPrompt = "Hack", rewardCredits = 25,
            rewardItem = "chip", createdTimestamp = 1234L
        )

        assertEquals(
            FloorObstacle(
                id = 7, mapId = "L2F1", saveSlotId = "slot", levelNumber = 2, floorIndex = 1,
                gridX = 4, gridY = 5, obstacleType = "LASER_GRID", name = "Laser",
                description = "Security", isPassable = false, isDestructible = false,
                isHacked = true, isDisarmed = false, durability = 80, maxDurability = 120,
                hackDifficulty = 3, interactionPrompt = "Hack", rewardCredits = 25,
                rewardItem = "chip", createdTimestamp = 1234L
            ),
            entity.toDomain()
        )
    }
}
