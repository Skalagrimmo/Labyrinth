package com.example.ui

import com.example.data.CombatActionType
import com.example.data.CombatWinner
import com.example.data.TurnActionRecord
import com.example.data.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTurnViewModelStateTest {
    @Test
    fun `encounter starts on unlocked player input`() {
        val vm = GameTurnViewModel()
        vm.initializeEncounter("ICE", initialTurn = 3)
        val s = vm.turnUiState.value

        assertEquals(3, s.currentTurn)
        assertEquals(TurnStateEnum.PLAYER, s.turnStateEnum)
        assertEquals(TurnPhase.PLAYER_INPUT, s.turnPhase)
        assertTrue(s.turnState is TurnState.PlayerTurn)
        assertFalse(s.isInputLocked)
        assertTrue(s.isPlayerTurn)
        assertFalse(s.isEnemyActing)
        assertEquals("ICE", s.activeEnemyName)
    }

    @Test
    fun `player action locks input and enters resolving phase`() {
        val vm = GameTurnViewModel()
        vm.initializeEncounter("ICE")
        val record = vm.startPlayerAction(CombatActionType.STRIKE, "Strike", damageDealt = 8)
        val s = vm.turnUiState.value

        assertEquals(TurnStateEnum.PROCESSING, s.turnStateEnum)
        assertEquals(TurnPhase.PLAYER_RESOLVING, s.turnPhase)
        assertTrue(s.turnState is TurnState.PlayerResolving)
        assertTrue(s.isInputLocked)
        assertFalse(s.isPlayerTurn)
        assertEquals(record, s.lastPlayerAction)
        assertEquals(1, s.totalPlayerActions)
        assertEquals(listOf(record), s.actionHistory)
    }

    @Test
    fun `enemy cycle keeps input locked and enemy record is retained`() {
        val vm = GameTurnViewModel()
        vm.initializeEncounter("Daemon")
        vm.beginEnemyTurnCycle("Daemon", "Probe")
        assertEquals(TurnStateEnum.ENEMY, vm.turnUiState.value.turnStateEnum)
        assertTrue(vm.turnUiState.value.isInputLocked)
        assertTrue(vm.turnUiState.value.isEnemyActing)

        val record = TurnActionRecord(
            roundNumber = 1, actorName = "Daemon", isPlayer = false,
            actionType = CombatActionType.STRIKE, summary = "Pulse", damageDealt = 4
        )
        vm.recordEnemyAction(record)
        val s = vm.turnUiState.value
        assertTrue(s.turnState is TurnState.EnemyResolving)
        assertEquals(record, s.lastEnemyAction)
        assertEquals(1, s.totalEnemyTurns)
    }

    @Test
    fun `maintenance advances exactly one round and restores player input`() {
        val vm = GameTurnViewModel()
        vm.initializeEncounter("ICE", initialTurn = 5)
        vm.beginEnemyTurnCycle("ICE")
        vm.concludeTurnCycleAndAdvance()
        val s = vm.turnUiState.value

        assertEquals(6, s.currentTurn)
        assertEquals(TurnStateEnum.PLAYER, s.turnStateEnum)
        assertEquals(TurnPhase.PLAYER_INPUT, s.turnPhase)
        assertTrue(s.turnState is TurnState.PlayerTurn)
        assertFalse(s.isInputLocked)
        assertTrue(s.isPlayerTurn)
        assertFalse(s.isEnemyActing)
    }

    @Test
    fun `encounter conclusion locks input and maps winner to terminal phase`() {
        val vm = GameTurnViewModel()
        vm.initializeEncounter("Boss", initialTurn = 4)
        vm.endEncounter(CombatWinner.PLAYER, "Boss neutralized")
        val s = vm.turnUiState.value

        assertEquals(TurnStateEnum.PROCESSING, s.turnStateEnum)
        assertEquals(TurnPhase.COMBAT_VICTORY, s.turnPhase)
        assertTrue(s.turnState is TurnState.EncounterConcluded)
        assertTrue(s.isInputLocked)
        assertFalse(s.isPlayerTurn)
        assertFalse(s.isEnemyActing)
    }

    @Test
    fun `defeat maps to combat defeat phase`() {
        val vm = GameTurnViewModel()
        vm.initializeEncounter("Boss")
        vm.endEncounter(CombatWinner.ENEMY, "Runner down")
        assertEquals(TurnPhase.COMBAT_DEFEAT, vm.turnUiState.value.turnPhase)
    }
}
