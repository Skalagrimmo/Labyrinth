package com.example.ui

import com.example.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableSaveStateMapperTest {
    @Test
    fun `state mapper preserves portable exploration state`() {
        val state = GameViewModel.GameUiState(
            runnerName = "Mapper", runnerClass = NetrunnerClass.TECHIE,
            level = 4, integrity = 77, credits = 345, gridX = 2, gridY = 3,
            direction = Direction.NORTH, currentZone = Zone.CITY,
            maze = arrayOf(arrayOf(CellType.SAFE_ZONE, CellType.PATH)),
            exploredCells = linkedSetOf(0 to 0, 1 to 0),
            inventory = listOf("NanoMed.sys"), installedPrograms = listOf(
                Program("ping", "ping.exe", "test", ramCost = 1, damage = 10)
            ),
            unlockedSkills = setOf("scan"), levelSeed = 99L
        )

        val payload = PortableSaveStateMapper.toPayload(state)
        val restored = PortableSaveStateMapper.restore(
            current = GameViewModel.GameUiState(),
            payload = payload,
            programLookup = { id -> Program(id, id, "restored", ramCost = 1) },
            implantLookup = { null }
        )

        assertEquals(state.runnerName, restored.runnerName)
        assertEquals(state.runnerClass, restored.runnerClass)
        assertEquals(state.level, restored.level)
        assertEquals(state.integrity, restored.integrity)
        assertEquals(state.credits, restored.credits)
        assertEquals(state.direction, restored.direction)
        assertEquals(state.currentZone, restored.currentZone)
        assertEquals(state.maze.map { it.toList() }, restored.maze.map { it.toList() })
        assertEquals(state.exploredCells, restored.exploredCells)
        assertEquals(listOf("ping"), restored.installedPrograms.map { it.id })
        assertEquals(state.unlockedSkills, restored.unlockedSkills)
        assertEquals(ActiveScreen.EXPLORATION, restored.screen)
    }

    @Test
    fun `restore keeps legacy enum fallbacks`() {
        val payload = PortableSavePayload(
            runnerClass = "UNKNOWN_CLASS", direction = "SIDEWAYS", currentZone = "VOID",
            activeWeather = "RAIN", gameStateName = "BROKEN", mazeData = "SAFE_ZONE"
        )
        val restored = PortableSaveStateMapper.restore(
            current = GameViewModel.GameUiState(),
            payload = payload,
            programLookup = { error("unused") },
            implantLookup = { null }
        )

        assertEquals(NetrunnerClass.CODE_SLASHER, restored.runnerClass)
        assertEquals(Direction.EAST, restored.direction)
        assertEquals(Zone.BUILDING, restored.currentZone)
        assertEquals(CyberWeather.CLEAR, restored.activeWeather)
        assertEquals(GameState.EXPLORATION, restored.gameState)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `restore rejects empty maze like legacy importer`() {
        PortableSaveStateMapper.restore(
            current = GameViewModel.GameUiState(),
            payload = PortableSavePayload(mazeData = ""),
            programLookup = { error("unused") },
            implantLookup = { null }
        )
    }
}
