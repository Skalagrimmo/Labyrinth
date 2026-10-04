package com.example.ui

import com.example.data.CombatActionType
import com.example.data.CombatWinner
import com.example.data.TurnActionRecord
import com.example.data.TurnPhase

/**
 * Pure synchronous reducer for the combat turn lifecycle.
 * Coroutine pacing, Room synchronization and UI event emission remain in GameTurnViewModel.
 */
object TurnStateMachine {
    fun initialize(state: GameTurnUiState, enemyName: String, initialTurn: Int = 1): GameTurnUiState =
        state.copy(
            currentTurn = initialTurn,
            turnStateEnum = TurnStateEnum.PLAYER,
            turnState = TurnState.PlayerTurn(initialTurn, isInputEnabled = true),
            turnPhase = TurnPhase.PLAYER_INPUT,
            isInputLocked = false,
            isPlayerTurn = true,
            isEnemyActing = false,
            activeEnemyName = enemyName,
            lastPlayerAction = null,
            lastEnemyAction = null,
            actionHistory = emptyList(),
            totalPlayerActions = 0,
            totalEnemyTurns = 0,
            statusBanner = "ROUND $initialTurn: READY"
        )

    fun startPlayerAction(state: GameTurnUiState, record: TurnActionRecord): GameTurnUiState =
        state.copy(
            turnStateEnum = TurnStateEnum.PROCESSING,
            isInputLocked = true,
            isPlayerTurn = false,
            turnState = TurnState.PlayerResolving(state.currentTurn, record.actionType, record.summary),
            turnPhase = TurnPhase.PLAYER_RESOLVING,
            lastPlayerAction = record,
            actionHistory = state.actionHistory + record,
            totalPlayerActions = state.totalPlayerActions + 1,
            statusBanner = record.summary
        )

    fun beginEnemyTurn(state: GameTurnUiState, enemyName: String, enemyIntent: String? = null): GameTurnUiState =
        state.copy(
            turnStateEnum = TurnStateEnum.ENEMY,
            isInputLocked = true,
            isPlayerTurn = false,
            isEnemyActing = true,
            activeEnemyName = enemyName,
            turnState = TurnState.EnemyTurn(state.currentTurn, enemyName, enemyIntent),
            turnPhase = TurnPhase.ENEMY_RESOLVING,
            statusBanner = "⚠️ $enemyName ACTING..."
        )

    fun recordEnemyAction(state: GameTurnUiState, record: TurnActionRecord): GameTurnUiState =
        state.copy(
            lastEnemyAction = record,
            actionHistory = state.actionHistory + record,
            totalEnemyTurns = state.totalEnemyTurns + 1,
            turnState = TurnState.EnemyResolving(state.currentTurn, record.actorName, record.summary),
            statusBanner = record.summary
        )

    fun beginMaintenance(state: GameTurnUiState): GameTurnUiState {
        val nextTurn = state.currentTurn + 1
        return state.copy(
            turnStateEnum = TurnStateEnum.PROCESSING,
            turnState = TurnState.TurnMaintenance(state.currentTurn, nextTurn),
            turnPhase = TurnPhase.ROUND_MAINTENANCE,
            statusBanner = "ROUND $nextTurn MAINTENANCE"
        )
    }

    fun advanceToPlayer(state: GameTurnUiState): GameTurnUiState {
        val nextTurn = state.currentTurn + 1
        return state.copy(
            currentTurn = nextTurn,
            turnStateEnum = TurnStateEnum.PLAYER,
            turnState = TurnState.PlayerTurn(nextTurn, isInputEnabled = true),
            turnPhase = TurnPhase.PLAYER_INPUT,
            isInputLocked = false,
            isPlayerTurn = true,
            isEnemyActing = false,
            statusBanner = "ROUND $nextTurn: READY"
        )
    }

    fun conclude(state: GameTurnUiState, winner: CombatWinner, reason: String): GameTurnUiState {
        val phase = when (winner) {
            CombatWinner.PLAYER, CombatWinner.ESCAPED -> TurnPhase.COMBAT_VICTORY
            CombatWinner.ENEMY -> TurnPhase.COMBAT_DEFEAT
        }
        return state.copy(
            turnStateEnum = TurnStateEnum.PROCESSING,
            isInputLocked = true,
            isPlayerTurn = false,
            isEnemyActing = false,
            turnState = TurnState.EncounterConcluded(state.currentTurn, winner, reason),
            turnPhase = phase,
            statusBanner = when (winner) {
                CombatWinner.PLAYER -> "🏆 VICTORY: $reason"
                CombatWinner.ENEMY -> "💀 DEFEAT: $reason"
                CombatWinner.ESCAPED -> "🏃 EVADED: $reason"
            }
        )
    }
}
