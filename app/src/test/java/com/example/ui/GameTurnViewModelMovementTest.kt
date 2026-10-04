package com.example.ui

import com.example.data.CellType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTurnViewModelMovementTest {
    @Test
    fun `player moves only onto walkable tile and updates facing`() {
        val vm = GameTurnViewModel()
        vm.initializeFloorMap(width = 5, height = 5, spawnX = 2, spawnY = 2)

        assertTrue(vm.handlePlayerMoveInput(1, 0))
        assertEquals(3, vm.turnUiState.value.playerX)
        assertEquals(2, vm.turnUiState.value.playerY)
        assertEquals("EAST", vm.turnUiState.value.playerFacing)
    }

    @Test
    fun `blocked move changes facing but not coordinates`() {
        val vm = GameTurnViewModel()
        vm.initializeFloorMap(width = 5, height = 5, spawnX = 1, spawnY = 1)

        assertFalse(vm.handlePlayerMoveInput(-1, 0))
        assertEquals(1, vm.turnUiState.value.playerX)
        assertEquals(1, vm.turnUiState.value.playerY)
        assertEquals("WEST", vm.turnUiState.value.playerFacing)
    }

    @Test
    fun `virus node is impassable`() {
        val vm = GameTurnViewModel()
        vm.initializeFloorMap(width = 5, height = 5, spawnX = 2, spawnY = 2)
        vm.setCell(3, 2, CellType.VIRUS_NODE)

        assertFalse(vm.handlePlayerMoveInput(1, 0))
        assertEquals(2, vm.turnUiState.value.playerX)
    }

    @Test
    fun `movement is rejected outside player turn`() {
        val vm = GameTurnViewModel()
        vm.initializeFloorMap(width = 5, height = 5, spawnX = 2, spawnY = 2)
        vm.transitionTo(TurnStateEnum.ENEMY)

        assertFalse(vm.handlePlayerMoveInput(1, 0))
        assertEquals(2, vm.turnUiState.value.playerX)
        assertEquals(2, vm.turnUiState.value.playerY)
    }
}
