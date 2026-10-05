package com.example.ui

import com.example.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacySaveStateCodecTest {
    @Test
    fun `codec round trips legacy state fields and serialized maps`() {
        val storage = AndroidLegacySaveStorage(
            android.app.Application(),
            "legacy_codec_test"
        )
        val codec = LegacySaveStateCodec(
            storage = storage,
            programLookup = { id -> Program(id, id, "restored", ramCost = 1) },
            cyberwareLookup = { id -> Cyberware(id, id, "restored") }
        )
        val state = GameViewModel.GameUiState(
            runnerName = "Legacy Runner",
            runnerClass = NetrunnerClass.TECHIE,
            maxIntegrity = 120,
            integrity = 91,
            playerMaxShield = 60,
            playerShield = 27,
            maxRam = 16,
            ram = 9,
            ramRecoveryRate = 3,
            credits = 777,
            damageBonus = 4,
            defenseBonus = 5,
            characterLevel = 6,
            characterXp = 321,
            xpToNextLevel = 654,
            gridX = 4,
            gridY = 5,
            direction = Direction.NORTH,
            level = 7,
            currentZone = Zone.CITY,
            buildingFloor = 3,
            collectorsLevel = 4,
            cityDistrictIndex = 2,
            hasElevatorKeycard = true,
            inventory = listOf("NanoMed.sys", "RAMBoost.exe"),
            installedPrograms = listOf(Program("ice", "ICE", "test", ramCost = 2)),
            exploredCells = setOf(1 to 2, 3 to 4),
            activeWeather = CyberWeather.STORM,
            weatherTurnsLeft = 4,
            stepsSinceLastEvent = 8,
            nextEventSteps = 19,
            predictedWeather = CyberWeather.RAIN,
            nodesHackedCount = 11,
            totalCreditsEarned = 999,
            skillPoints = 3,
            unlockedSkills = setOf("scan", "breach"),
            tutorialStep = 4,
            tutorialActive = true,
            tutorialSeen = true,
            maze = arrayOf(
                arrayOf(CellType.SAFE_ZONE, CellType.WALL),
                arrayOf(CellType.PATH, CellType.EXIT)
            ),
            originalMaze = arrayOf(arrayOf(CellType.PATH)),
            buildingFloors = mapOf(1 to arrayOf(arrayOf(CellType.PATH))),
            buildingExplored = mapOf(1 to setOf(0 to 0)),
            collectorsLevels = mapOf(2 to arrayOf(arrayOf(CellType.WALL))),
            collectorsExplored = mapOf(2 to setOf(1 to 1)),
            cityDistricts = mapOf(3 to arrayOf(arrayOf(CellType.EXIT))),
            cityExplored = mapOf(3 to setOf(2 to 2)),
            gameState = GameState.COMBAT_START,
            logFeed = listOf(LogMessage("legacy", LogType.COMBAT, 1234L))
        )

        codec.save(state)
        val restored = codec.load(GameViewModel.GameUiState())!!

        assertTrue(storage.getBoolean("has_saved_game"))
        assertEquals(state.runnerName, restored.runnerName)
        assertEquals(state.runnerClass, restored.runnerClass)
        assertEquals(state.level, restored.level)
        assertEquals(state.currentZone, restored.currentZone)
        assertEquals(state.gridX, restored.gridX)
        assertEquals(state.gridY, restored.gridY)
        assertEquals(state.inventory, restored.inventory)
        assertEquals(state.exploredCells, restored.exploredCells)
        assertEquals(state.maze.map { it.toList() }, restored.maze.map { it.toList() })
        assertEquals(state.originalMaze?.map { it.toList() }, restored.originalMaze?.map { it.toList() })
        assertEquals(state.buildingFloors.keys, restored.buildingFloors.keys)
        assertEquals(state.collectorsLevels.keys, restored.collectorsLevels.keys)
        assertEquals(state.cityDistricts.keys, restored.cityDistricts.keys)
        assertEquals(state.gameState, restored.gameState)
        assertEquals(state.logFeed.single().text, restored.logFeed.single().text)
        assertEquals(state.logFeed.single().type, restored.logFeed.single().type)
        assertEquals(state.logFeed.single().timestamp, restored.logFeed.single().timestamp)
    }
}
