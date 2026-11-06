package com.example.ui

import com.example.data.CombatActionType
import com.example.data.CombatWinner
import com.example.data.TurnActionRecord
import com.example.data.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnStateMachineTest {
    private fun action(player: Boolean, name: String = if (player) "Player" else "ICE") =
        TurnActionRecord(1, name, player, CombatActionType.STRIKE, if (player) "Strike" else "Pulse")

    @Test
    fun `pure lifecycle advances one complete round`() {
        var s = TurnStateMachine.initialize(GameTurnUiState(), "ICE", 1)
        s = TurnStateMachine.startPlayerAction(s, action(true))
        assertEquals(TurnPhase.PLAYER_RESOLVING, s.turnPhase)
        assertTrue(s.isInputLocked)

        s = TurnStateMachine.beginEnemyTurn(s, "ICE", "Attack")
        assertEquals(TurnStateEnum.ENEMY, s.turnStateEnum)
        assertTrue(s.isEnemyActing)

        s = TurnStateMachine.recordEnemyAction(s, action(false))
        assertEquals(1, s.totalEnemyTurns)

        s = TurnStateMachine.beginMaintenance(s)
        assertEquals(TurnPhase.ROUND_MAINTENANCE, s.turnPhase)
        assertTrue(s.turnState is TurnState.TurnMaintenance)

        s = TurnStateMachine.advanceToPlayer(s)
        assertEquals(2, s.currentTurn)
        assertEquals(TurnStateEnum.PLAYER, s.turnStateEnum)
        assertEquals(TurnPhase.PLAYER_INPUT, s.turnPhase)
        assertFalse(s.isInputLocked)
        assertEquals(2, s.actionHistory.size)
    }

    @Test
    fun `initialize clears encounter telemetry but preserves unrelated state`() {
        val original = GameTurnUiState(playerX = 7, playerY = 4, totalPlayerActions = 9, statusBanner = "old")
        val s = TurnStateMachine.initialize(original, "Daemon", 3)

        assertEquals(7, s.playerX)
        assertEquals(4, s.playerY)
        assertEquals(0, s.totalPlayerActions)
        assertEquals(3, s.currentTurn)
        assertEquals("Daemon", s.activeEnemyName)
    }

    @Test
    fun `escaped encounter uses victory phase while retaining escaped winner`() {
        val s = TurnStateMachine.conclude(
            TurnStateMachine.initialize(GameTurnUiState(), "ICE", 4),
            CombatWinner.ESCAPED,
            "Route found"
        )
        val concluded = s.turnState as TurnState.EncounterConcluded

        assertEquals(TurnPhase.COMBAT_VICTORY, s.turnPhase)
        assertEquals(CombatWinner.ESCAPED, concluded.winner)
        assertEquals(4, concluded.totalTurns)
        assertTrue(s.isInputLocked)
    }

    @Test
    fun `generic transitions preserve legacy flags and phases`() {
        var s = TurnStateMachine.initialize(GameTurnUiState(), "ICE", 2)

        s = TurnStateMachine.transition(s, TurnStateEnum.PROCESSING)
        assertEquals(TurnStateEnum.PROCESSING, s.turnStateEnum)
        assertEquals(TurnPhase.ROUND_MAINTENANCE, s.turnPhase)
        assertTrue(s.isInputLocked)

        s = TurnStateMachine.transition(s, TurnStateEnum.ENEMY)
        assertEquals(TurnStateEnum.ENEMY, s.turnStateEnum)
        assertEquals(TurnPhase.ENEMY_RESOLVING, s.turnPhase)
        assertTrue(s.isEnemyActing)
        assertFalse(s.isPlayerTurn)

        s = TurnStateMachine.transition(s, TurnStateEnum.PLAYER)
        assertEquals(TurnStateEnum.PLAYER, s.turnStateEnum)
        assertEquals(TurnPhase.PLAYER_INPUT, s.turnPhase)
        assertFalse(s.isInputLocked)
        assertTrue(s.isPlayerTurn)
        assertFalse(s.isEnemyActing)
        assertEquals(2, s.currentTurn)
    }
}
