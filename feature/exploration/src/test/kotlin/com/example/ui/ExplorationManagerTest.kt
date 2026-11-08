package com.example.ui

import com.example.audio.SilentAudioPlayer
import com.example.core.model.CellType
import com.example.core.model.Direction
import com.example.stealth.StealthGuardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * d8 slice 6: KOTOR combat-opening classification for player-initiated
 * node strikes - a bump into a VIRUS_NODE is a shadow strike only
 * while the silent protocol is engaged and the floor's ICE sweep is
 * not already alerted.
 */
class ExplorationManagerTest {

    private val triggered = mutableListOf<Triple<Int, Int, CombatOpening>>()

    private fun corridorWithNode(): GameUiState =
        GameUiState(
            screen = ActiveScreen.EXPLORATION,
            gameState = GameState.EXPLORATION,
            gridX = 1,
            gridY = 1,
            direction = Direction.EAST,
            maze = arrayOf(
                arrayOf(CellType.WALL, CellType.WALL, CellType.WALL, CellType.WALL, CellType.WALL),
                arrayOf(CellType.WALL, CellType.PATH, CellType.VIRUS_NODE, CellType.PATH, CellType.WALL),
                arrayOf(CellType.WALL, CellType.WALL, CellType.WALL, CellType.WALL, CellType.WALL)
            )
        )

    private fun newManager(state: GameUiState): ExplorationManager =
        ExplorationManager(
            _uiState = MutableStateFlow(state),
            soundManager = SilentAudioPlayer,
            scope = CoroutineScope(Dispatchers.Unconfined),
            onLog = { _, _ -> },
            onTriggerCombat = { x, y, opening -> triggered.add(Triple(x, y, opening)) },
            underlyingCellTypes = ConcurrentHashMap()
        )

    @Test
    fun sneakingBumpIntoANodeIsAShadowStrike() {
        newManager(corridorWithNode().copy(isSneaking = true)).moveForward()
        assertEquals(listOf(Triple(2, 1, CombatOpening.AMBUSHED)), triggered)
    }

    @Test
    fun loudBumpIntoANodeIsAStandardEngagement() {
        newManager(corridorWithNode()).moveForward()
        assertEquals(listOf(Triple(2, 1, CombatOpening.STANDARD)), triggered)
    }

    @Test
    fun sneakingBumpOnAnAlertedFloorIsNoShadowStrike() {
        newManager(
            corridorWithNode().copy(isSneaking = true, iceGuardState = StealthGuardState.ALERTED)
        ).moveForward()
        assertEquals(listOf(Triple(2, 1, CombatOpening.STANDARD)), triggered)
    }
}
