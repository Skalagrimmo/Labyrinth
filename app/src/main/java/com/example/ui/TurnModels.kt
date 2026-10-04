package com.example.ui

import com.example.data.CombatActionType
import com.example.data.CombatWinner
import com.example.data.FloorObstacleEntity
import com.example.data.TurnActionRecord
import com.example.data.TurnPhase

enum class TurnStateEnum {
    PLAYER,
    ENEMY,
    PROCESSING
}

sealed interface TurnState {
    object Idle : TurnState
    data class PlayerTurn(val turnNumber: Int, val isInputEnabled: Boolean = true) : TurnState
    data class PlayerResolving(
        val turnNumber: Int,
        val actionType: CombatActionType,
        val actionDescription: String
    ) : TurnState
    data class EnemyTurn(
        val turnNumber: Int,
        val enemyName: String,
        val intentDescription: String? = null
    ) : TurnState
    data class EnemyResolving(
        val turnNumber: Int,
        val enemyName: String,
        val actionDescription: String
    ) : TurnState
    data class TurnMaintenance(val completedTurnNumber: Int, val nextTurnNumber: Int) : TurnState
    data class EncounterConcluded(
        val totalTurns: Int,
        val winner: CombatWinner,
        val reason: String
    ) : TurnState
}

sealed interface TurnCombatEvent {
    data class TurnStarted(val turnNumber: Int, val isPlayer: Boolean) : TurnCombatEvent
    data class InputLocked(val reason: String) : TurnCombatEvent
    data class InputUnlocked(val turnNumber: Int) : TurnCombatEvent
    data class ActionExecuted(val record: TurnActionRecord) : TurnCombatEvent
    data class PhaseChanged(val phase: TurnPhase) : TurnCombatEvent
    data class StateTransitioned(val fromState: TurnStateEnum, val toState: TurnStateEnum) : TurnCombatEvent
    data class EnemyCycleStarted(val enemyName: String) : TurnCombatEvent
    data class MaintenanceTick(val turnNumber: Int, val ramRecovered: Int) : TurnCombatEvent
    data class EncounterFinished(val winner: CombatWinner, val totalTurns: Int) : TurnCombatEvent
    data class PlayerMoved(val fromX: Int, val fromY: Int, val toX: Int, val toY: Int) : TurnCombatEvent
    data class PlayerBlocked(val targetX: Int, val targetY: Int, val reason: String) : TurnCombatEvent
    data class NpcMoved(val entityId: String, val toX: Int, val toY: Int) : TurnCombatEvent
}

data class NpcPosition(
    val entityId: String,
    val name: String,
    val category: String = "ENEMY",
    val x: Int,
    val y: Int,
    val facing: String = "SOUTH",
    val isAlive: Boolean = true,
    val alertLevel: String = "UNALERTED"
)

data class GameTurnUiState(
    val currentTurn: Int = 1,
    val turnStateEnum: TurnStateEnum = TurnStateEnum.PLAYER,
    val turnState: TurnState = TurnState.Idle,
    val turnPhase: TurnPhase = TurnPhase.PLAYER_INPUT,
    val isInputLocked: Boolean = false,
    val isPlayerTurn: Boolean = true,
    val isEnemyActing: Boolean = false,
    val activeEnemyName: String? = null,
    val lastPlayerAction: TurnActionRecord? = null,
    val lastEnemyAction: TurnActionRecord? = null,
    val actionHistory: List<TurnActionRecord> = emptyList(),
    val totalPlayerActions: Int = 0,
    val totalEnemyTurns: Int = 0,
    val statusBanner: String? = null,
    val gridWidth: Int = 10,
    val gridHeight: Int = 10,
    val playerX: Int = 1,
    val playerY: Int = 1,
    val playerFacing: String = "NORTH",
    val npcs: List<NpcPosition> = emptyList(),
    val obstacles: List<FloorObstacleEntity> = emptyList(),
    val mapId: String = "current_save_L1_F0",
    val floorIndex: Int = 0,
    val levelNumber: Int = 1
)
