package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CellType
import com.example.data.CombatActionType
import com.example.data.CombatWinner
import com.example.data.Direction
import com.example.data.GameEngine
import com.example.data.GridEntityCoordinateEntity
import com.example.data.PlayerEntity
import com.example.data.PlayerNpcCoordinatesDao
import com.example.data.TurnActionRecord
import com.example.data.TurnPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * High-level enum representing core turn states for the turn manager.
 */
enum class TurnStateEnum {
    PLAYER,
    ENEMY,
    PROCESSING
}

/**
 * State machine representing the granular stages of a turn cycle.
 */
sealed interface TurnState {
    object Idle : TurnState

    data class PlayerTurn(
        val turnNumber: Int,
        val isInputEnabled: Boolean = true
    ) : TurnState

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

    data class TurnMaintenance(
        val completedTurnNumber: Int,
        val nextTurnNumber: Int
    ) : TurnState

    data class EncounterConcluded(
        val totalTurns: Int,
        val winner: CombatWinner,
        val reason: String
    ) : TurnState
}

/**
 * Combat event emissions for UI animations, audio cues, and sensory banners.
 */
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
    data class NpcMoved(val entityId: String, val toX: Int, val toY: Int) : TurnCombatEvent
}

/**
 * Spatial coordinate descriptor for entities on the 2D floor grid.
 */
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

/**
 * Immutable UI State snapshot managed by [GameTurnViewModel].
 */
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

    // 2D Floor Map & Spatial Grid Representation
    val gridWidth: Int = 10,
    val gridHeight: Int = 10,
    val playerX: Int = 1,
    val playerY: Int = 1,
    val playerFacing: String = "NORTH",
    val npcs: List<NpcPosition> = emptyList(),
    val mapId: String = "current_save_L1_F0",
    val floorIndex: Int = 0,
    val levelNumber: Int = 1
)

/**
 * GameTurnViewModel:
 * Dedicated controller managing the turn-based state machine, turn counter,
 * input locking during NPC execution cycles, 2D Array floor map structure,
 * and player/NPC coordinates synchronized with the Room database.
 */
class GameTurnViewModel(
    private val playerNpcCoordinatesDao: PlayerNpcCoordinatesDao? = null
) : ViewModel() {

    // 2D Array structure representing the active level floor map
    private var _floorGrid: Array<Array<CellType>> = Array(10) { Array(10) { CellType.WALL } }
    val floorGrid: Array<Array<CellType>> get() = _floorGrid

    private val _turnUiState = MutableStateFlow(GameTurnUiState())
    val turnUiState: StateFlow<GameTurnUiState> = _turnUiState.asStateFlow()

    private val _turnEvents = MutableSharedFlow<TurnCombatEvent>(extraBufferCapacity = 64)
    val turnEvents: SharedFlow<TurnCombatEvent> = _turnEvents.asSharedFlow()

    private var activeTurnJob: Job? = null

    init {
        // Initialize default 2D grid structure
        initializeFloorMap(width = 10, height = 10)
    }

    /**
     * Initializes the 2D array structure for the level floor map.
     */
    fun initializeFloorMap(
        width: Int = 10,
        height: Int = 10,
        initialGrid: Array<Array<CellType>>? = null,
        spawnX: Int = 1,
        spawnY: Int = 1,
        levelNumber: Int = 1,
        floorIndex: Int = 0,
        mapId: String = "current_save_L${levelNumber}_F${floorIndex}"
    ) {
        val safeWidth = width.coerceAtLeast(3)
        val safeHeight = height.coerceAtLeast(3)

        _floorGrid = initialGrid ?: Array(safeHeight) { y ->
            Array(safeWidth) { x ->
                if (x == 0 || y == 0 || x == safeWidth - 1 || y == safeHeight - 1) {
                    CellType.WALL
                } else {
                    CellType.PATH
                }
            }
        }

        val clampedSpawnX = spawnX.coerceIn(0, safeWidth - 1)
        val clampedSpawnY = spawnY.coerceIn(0, safeHeight - 1)

        _turnUiState.update { state ->
            state.copy(
                gridWidth = safeWidth,
                gridHeight = safeHeight,
                playerX = clampedSpawnX,
                playerY = clampedSpawnY,
                levelNumber = levelNumber,
                floorIndex = floorIndex,
                mapId = mapId
            )
        }
    }

    /**
     * Loads a procedurally generated or pre-constructed 2D floor matrix.
     */
    fun loadFloorMapMatrix(
        grid: Array<Array<CellType>>,
        playerSpawnX: Int = 1,
        playerSpawnY: Int = 1,
        npcs: List<NpcPosition> = emptyList(),
        mapId: String = "current_save_L1_F0",
        levelNumber: Int = 1,
        floorIndex: Int = 0
    ) {
        val height = grid.size
        val width = if (height > 0) grid[0].size else 10
        _floorGrid = grid

        _turnUiState.update { state ->
            state.copy(
                gridWidth = width,
                gridHeight = height,
                playerX = playerSpawnX.coerceIn(0, width - 1),
                playerY = playerSpawnY.coerceIn(0, height - 1),
                npcs = npcs,
                mapId = mapId,
                levelNumber = levelNumber,
                floorIndex = floorIndex
            )
        }
    }

    /**
     * Retrieves the cell type at the specified (x, y) 2D grid coordinates.
     */
    fun getCell(x: Int, y: Int): CellType? {
        if (y in _floorGrid.indices && x in _floorGrid[y].indices) {
            return _floorGrid[y][x]
        }
        return null
    }

    /**
     * Sets or modifies the cell type at (x, y) coordinates on the 2D floor map.
     */
    fun setCell(x: Int, y: Int, cellType: CellType) {
        if (y in _floorGrid.indices && x in _floorGrid[y].indices) {
            _floorGrid[y][x] = cellType
        }
    }

    /**
     * Checks whether a tile is passable for movement.
     */
    fun isTileWalkable(x: Int, y: Int): Boolean {
        val cell = getCell(x, y) ?: return false
        if (cell == CellType.WALL || cell == CellType.VIRUS_NODE) return false

        // Check if an impassable NPC blocks the tile
        val hasImpassableNpc = _turnUiState.value.npcs.any { it.x == x && it.y == y && it.isAlive }
        return !hasImpassableNpc
    }

    /**
     * Moves the player by deltaX, deltaY across the 2D floor grid.
     * Persists position updates to the Room database if DAO is available.
     */
    fun movePlayer(deltaX: Int, deltaY: Int): Boolean {
        if (_turnUiState.value.isInputLocked) return false

        val currentX = _turnUiState.value.playerX
        val currentY = _turnUiState.value.playerY
        val targetX = currentX + deltaX
        val targetY = currentY + deltaY

        val facing = when {
            deltaY < 0 -> "NORTH"
            deltaY > 0 -> "SOUTH"
            deltaX > 0 -> "EAST"
            deltaX < 0 -> "WEST"
            else -> _turnUiState.value.playerFacing
        }

        if (!isTileWalkable(targetX, targetY)) {
            _turnUiState.update { it.copy(playerFacing = facing) }
            return false
        }

        _turnUiState.update { state ->
            state.copy(
                playerX = targetX,
                playerY = targetY,
                playerFacing = facing
            )
        }

        _turnEvents.tryEmit(TurnCombatEvent.PlayerMoved(currentX, currentY, targetX, targetY))

        // Synchronize player coordinates to Room Database
        if (playerNpcCoordinatesDao != null) {
            viewModelScope.launch {
                playerNpcCoordinatesDao.updatePlayerPosition(
                    playerId = "player_current_save",
                    newX = targetX,
                    newY = targetY,
                    facingDirection = facing
                )
            }
        }
        return true
    }

    /**
     * Directly updates player coordinates on the 2D grid and synchronizes with Room.
     */
    fun updatePlayerCoordinates(
        x: Int,
        y: Int,
        facing: String = "NORTH"
    ) {
        val clampedX = x.coerceIn(0, _turnUiState.value.gridWidth - 1)
        val clampedY = y.coerceIn(0, _turnUiState.value.gridHeight - 1)

        _turnUiState.update { state ->
            state.copy(
                playerX = clampedX,
                playerY = clampedY,
                playerFacing = facing
            )
        }

        if (playerNpcCoordinatesDao != null) {
            viewModelScope.launch {
                playerNpcCoordinatesDao.updatePlayerPosition(
                    playerId = "player_current_save",
                    newX = clampedX,
                    newY = clampedY,
                    facingDirection = facing
                )
            }
        }
    }

    /**
     * Updates an NPC/Enemy's coordinates on the 2D grid and synchronizes with Room.
     */
    fun updateNpcCoordinates(
        entityId: String,
        x: Int,
        y: Int,
        facing: String = "SOUTH",
        alertLevel: String? = null
    ) {
        val clampedX = x.coerceIn(0, _turnUiState.value.gridWidth - 1)
        val clampedY = y.coerceIn(0, _turnUiState.value.gridHeight - 1)

        _turnUiState.update { state ->
            val updatedNpcs = state.npcs.map { npc ->
                if (npc.entityId == entityId) {
                    npc.copy(
                        x = clampedX,
                        y = clampedY,
                        facing = facing,
                        alertLevel = alertLevel ?: npc.alertLevel
                    )
                } else {
                    npc
                }
            }
            state.copy(npcs = updatedNpcs)
        }

        _turnEvents.tryEmit(TurnCombatEvent.NpcMoved(entityId, clampedX, clampedY))

        if (playerNpcCoordinatesDao != null) {
            viewModelScope.launch {
                if (alertLevel != null) {
                    playerNpcCoordinatesDao.updateNpcMovementAndAlert(
                        entityId = entityId,
                        newX = clampedX,
                        newY = clampedY,
                        alertLevel = alertLevel,
                        facingDirection = facing
                    )
                } else {
                    playerNpcCoordinatesDao.updateNpcPosition(
                        entityId = entityId,
                        newX = clampedX,
                        newY = clampedY,
                        facingDirection = facing
                    )
                }
            }
        }
    }

    /**
     * Synchronizes live coordinates from Room database into memory.
     */
    fun syncCoordinatesFromDatabase(mapId: String = _turnUiState.value.mapId) {
        if (playerNpcCoordinatesDao == null) return
        viewModelScope.launch {
            val coords = playerNpcCoordinatesDao.getFloorEntitiesCoordinates(mapId)
            val mappedNpcs = coords.npcs.map { entity ->
                NpcPosition(
                    entityId = entity.entityId,
                    name = entity.entityName,
                    category = entity.entityCategory,
                    x = entity.coordinateX,
                    y = entity.coordinateY,
                    facing = entity.facingDirection,
                    isAlive = entity.isAliveOrActive,
                    alertLevel = entity.alertLevel
                )
            }

            _turnUiState.update { state ->
                state.copy(
                    playerX = coords.playerX,
                    playerY = coords.playerY,
                    playerFacing = coords.playerFacing,
                    npcs = mappedNpcs
                )
            }
        }
    }

    // --- Turn State Transitions (PLAYER, ENEMY, PROCESSING) ---

    /**
     * Explicitly transitions the turn state machine to a target [TurnStateEnum].
     * Manages input locking, phase alignment, and event emission.
     */
    fun transitionTo(targetState: TurnStateEnum) {
        val previousState = _turnUiState.value.turnStateEnum
        if (previousState == targetState) return

        when (targetState) {
            TurnStateEnum.PLAYER -> {
                _turnUiState.update { state ->
                    state.copy(
                        turnStateEnum = TurnStateEnum.PLAYER,
                        isInputLocked = false,
                        isPlayerTurn = true,
                        isEnemyActing = false,
                        turnPhase = TurnPhase.PLAYER_INPUT,
                        turnState = TurnState.PlayerTurn(state.currentTurn, isInputEnabled = true),
                        statusBanner = "ROUND ${state.currentTurn}: YOUR TURN"
                    )
                }
                _turnEvents.tryEmit(TurnCombatEvent.InputUnlocked(_turnUiState.value.currentTurn))
                _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.PLAYER_INPUT))
            }
            TurnStateEnum.ENEMY -> {
                val enemyName = _turnUiState.value.activeEnemyName ?: "Hostile Target"
                _turnUiState.update { state ->
                    state.copy(
                        turnStateEnum = TurnStateEnum.ENEMY,
                        isInputLocked = true,
                        isPlayerTurn = false,
                        isEnemyActing = true,
                        turnPhase = TurnPhase.ENEMY_RESOLVING,
                        turnState = TurnState.EnemyTurn(state.currentTurn, enemyName),
                        statusBanner = "⚠️ $enemyName ACTING..."
                    )
                }
                _turnEvents.tryEmit(TurnCombatEvent.InputLocked("Enemy turn in progress"))
                _turnEvents.tryEmit(TurnCombatEvent.EnemyCycleStarted(enemyName))
                _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.ENEMY_RESOLVING))
            }
            TurnStateEnum.PROCESSING -> {
                _turnUiState.update { state ->
                    state.copy(
                        turnStateEnum = TurnStateEnum.PROCESSING,
                        isInputLocked = true,
                        turnPhase = TurnPhase.ROUND_MAINTENANCE,
                        statusBanner = "PROCESSING TURN ACTIONS..."
                    )
                }
                _turnEvents.tryEmit(TurnCombatEvent.InputLocked("Resolving combat & environmental turns"))
                _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.ROUND_MAINTENANCE))
            }
        }
        _turnEvents.tryEmit(TurnCombatEvent.StateTransitioned(previousState, targetState))
    }

    /**
     * Executes a full turn transition cycle: PLAYER action -> PROCESSING -> ENEMY response -> PROCESSING -> next PLAYER turn.
     */
    fun processFullTurnCycle(
        playerAction: TurnActionRecord,
        enemyActionExecution: (suspend () -> TurnActionRecord)? = null,
        ramRecovery: Int = 2
    ) {
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            // 1. Move to PROCESSING for player action resolution
            transitionTo(TurnStateEnum.PROCESSING)
            _turnUiState.update { state ->
                state.copy(
                    lastPlayerAction = playerAction,
                    actionHistory = state.actionHistory + playerAction,
                    totalPlayerActions = state.totalPlayerActions + 1,
                    turnState = TurnState.PlayerResolving(state.currentTurn, playerAction.actionType, playerAction.summary),
                    statusBanner = playerAction.summary
                )
            }
            _turnEvents.tryEmit(TurnCombatEvent.ActionExecuted(playerAction))
            delay(500)

            // 2. Move to ENEMY if hostile action provider exists
            if (enemyActionExecution != null) {
                transitionTo(TurnStateEnum.ENEMY)
                delay(600)

                // Execute enemy turn in PROCESSING
                transitionTo(TurnStateEnum.PROCESSING)
                val enemyRecord = enemyActionExecution()
                _turnUiState.update { state ->
                    state.copy(
                        lastEnemyAction = enemyRecord,
                        actionHistory = state.actionHistory + enemyRecord,
                        totalEnemyTurns = state.totalEnemyTurns + 1,
                        turnState = TurnState.EnemyResolving(state.currentTurn, enemyRecord.actorName, enemyRecord.summary),
                        statusBanner = enemyRecord.summary
                    )
                }
                _turnEvents.tryEmit(TurnCombatEvent.ActionExecuted(enemyRecord))
                delay(500)
            }

            // 3. Maintenance & Advance to next PLAYER turn
            val completedTurn = _turnUiState.value.currentTurn
            val nextTurn = completedTurn + 1
            _turnUiState.update { state ->
                state.copy(
                    currentTurn = nextTurn,
                    turnState = TurnState.TurnMaintenance(completedTurn, nextTurn)
                )
            }
            _turnEvents.tryEmit(TurnCombatEvent.MaintenanceTick(completedTurn, ramRecovery))
            delay(300)

            // 4. Return to PLAYER
            transitionTo(TurnStateEnum.PLAYER)
            _turnEvents.tryEmit(TurnCombatEvent.TurnStarted(nextTurn, isPlayer = true))
        }
    }

    // --- Turn Lifecycle Management ---

    /**
     * Initializes or resets a combat encounter with a fresh turn counter and state machine.
     */
    fun initializeEncounter(enemyName: String, initialTurn: Int = 1) {
        activeTurnJob?.cancel()
        _turnUiState.update { state ->
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
        }
        _turnEvents.tryEmit(TurnCombatEvent.TurnStarted(initialTurn, isPlayer = true))
        _turnEvents.tryEmit(TurnCombatEvent.InputUnlocked(initialTurn))
    }

    /**
     * Submits a player action into the turn lifecycle.
     * Automatically locks user input and advances state to resolving.
     */
    fun startPlayerAction(
        actionType: CombatActionType,
        description: String,
        actorName: String = "Player",
        damageDealt: Int = 0,
        shieldAbsorbed: Int = 0,
        healAmount: Int = 0,
        isCrit: Boolean = false,
        isMiss: Boolean = false,
        statusApplied: String? = null
    ): TurnActionRecord {
        val currentTurn = _turnUiState.value.currentTurn
        val record = TurnActionRecord(
            roundNumber = currentTurn,
            actorName = actorName,
            isPlayer = true,
            actionType = actionType,
            summary = description,
            damageDealt = damageDealt,
            shieldAbsorbed = shieldAbsorbed,
            healAmount = healAmount,
            isCrit = isCrit,
            isMiss = isMiss,
            statusApplied = statusApplied
        )

        _turnUiState.update { state ->
            state.copy(
                turnStateEnum = TurnStateEnum.PROCESSING,
                isInputLocked = true,
                isPlayerTurn = false,
                turnState = TurnState.PlayerResolving(currentTurn, actionType, description),
                turnPhase = TurnPhase.PLAYER_RESOLVING,
                lastPlayerAction = record,
                actionHistory = state.actionHistory + record,
                totalPlayerActions = state.totalPlayerActions + 1,
                statusBanner = description
            )
        }

        _turnEvents.tryEmit(TurnCombatEvent.InputLocked("Player action resolving: $description"))
        _turnEvents.tryEmit(TurnCombatEvent.ActionExecuted(record))
        _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.PLAYER_RESOLVING))
        return record
    }

    /**
     * Transitions from player action resolution into the enemy/NPC turn cycle.
     * Guarantees input remains strictly locked during the hostile AI cycle.
     */
    fun beginEnemyTurnCycle(
        enemyName: String,
        enemyIntent: String? = null,
        enemyActionExecution: (suspend () -> TurnActionRecord)? = null
    ) {
        val currentTurn = _turnUiState.value.currentTurn
        activeTurnJob?.cancel()

        _turnUiState.update { state ->
            state.copy(
                turnStateEnum = TurnStateEnum.ENEMY,
                isInputLocked = true,
                isPlayerTurn = false,
                isEnemyActing = true,
                activeEnemyName = enemyName,
                turnState = TurnState.EnemyTurn(currentTurn, enemyName, enemyIntent),
                turnPhase = TurnPhase.ENEMY_RESOLVING,
                statusBanner = "⚠️ $enemyName ACTING..."
            )
        }

        _turnEvents.tryEmit(TurnCombatEvent.EnemyCycleStarted(enemyName))
        _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.ENEMY_RESOLVING))

        if (enemyActionExecution != null) {
            activeTurnJob = viewModelScope.launch {
                delay(600) // Sensory pacing delay
                _turnUiState.update { it.copy(turnStateEnum = TurnStateEnum.PROCESSING) }
                val record = enemyActionExecution()
                recordEnemyAction(record)
                delay(600)
                concludeTurnCycleAndAdvance()
            }
        }
    }

    /**
     * Records hostile NPC/Enemy action telemetry and updates state machine.
     */
    fun recordEnemyAction(record: TurnActionRecord) {
        _turnUiState.update { state ->
            state.copy(
                lastEnemyAction = record,
                actionHistory = state.actionHistory + record,
                totalEnemyTurns = state.totalEnemyTurns + 1,
                turnState = TurnState.EnemyResolving(state.currentTurn, record.actorName, record.summary),
                statusBanner = record.summary
            )
        }
        _turnEvents.tryEmit(TurnCombatEvent.ActionExecuted(record))
    }

    /**
     * Executes round maintenance, increments the turn counter,
     * unlocks user input, and restores the player turn state.
     */
    fun concludeTurnCycleAndAdvance(
        ramRecovery: Int = 2,
        onMaintenanceComplete: (() -> Unit)? = null
    ) {
        val completedTurn = _turnUiState.value.currentTurn
        val nextTurn = completedTurn + 1

        _turnUiState.update { state ->
            state.copy(
                turnStateEnum = TurnStateEnum.PROCESSING,
                turnState = TurnState.TurnMaintenance(completedTurn, nextTurn),
                turnPhase = TurnPhase.ROUND_MAINTENANCE,
                statusBanner = "ROUND $nextTurn MAINTENANCE"
            )
        }

        _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.ROUND_MAINTENANCE))
        _turnEvents.tryEmit(TurnCombatEvent.MaintenanceTick(completedTurn, ramRecovery))

        onMaintenanceComplete?.invoke()

        // Unlock player input and set next round
        _turnUiState.update { state ->
            state.copy(
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

        _turnEvents.tryEmit(TurnCombatEvent.TurnStarted(nextTurn, isPlayer = true))
        _turnEvents.tryEmit(TurnCombatEvent.InputUnlocked(nextTurn))
        _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(TurnPhase.PLAYER_INPUT))
    }

    /**
     * Ends the encounter cleanly in victory, defeat, or escape.
     */
    fun endEncounter(winner: CombatWinner, reason: String) {
        activeTurnJob?.cancel()
        val totalTurns = _turnUiState.value.currentTurn
        val endPhase = when (winner) {
            CombatWinner.PLAYER -> TurnPhase.COMBAT_VICTORY
            CombatWinner.ENEMY -> TurnPhase.COMBAT_DEFEAT
            CombatWinner.ESCAPED -> TurnPhase.COMBAT_VICTORY
        }

        _turnUiState.update { state ->
            state.copy(
                turnStateEnum = TurnStateEnum.PROCESSING,
                isInputLocked = true,
                isPlayerTurn = false,
                isEnemyActing = false,
                turnState = TurnState.EncounterConcluded(totalTurns, winner, reason),
                turnPhase = endPhase,
                statusBanner = when (winner) {
                    CombatWinner.PLAYER -> "🏆 VICTORY: $reason"
                    CombatWinner.ENEMY -> "💀 DEFEAT: $reason"
                    CombatWinner.ESCAPED -> "🏃 EVADED: $reason"
                }
            )
        }

        _turnEvents.tryEmit(TurnCombatEvent.PhaseChanged(endPhase))
        _turnEvents.tryEmit(TurnCombatEvent.EncounterFinished(winner, totalTurns))
    }

    /**
     * Resets the turn manager to Idle state.
     */
    fun resetToIdle() {
        activeTurnJob?.cancel()
        _turnUiState.update {
            GameTurnUiState(
                currentTurn = 1,
                turnStateEnum = TurnStateEnum.PLAYER,
                turnState = TurnState.Idle,
                turnPhase = TurnPhase.PLAYER_INPUT,
                isInputLocked = false,
                isPlayerTurn = true,
                isEnemyActing = false,
                statusBanner = null
            )
        }
    }
}
