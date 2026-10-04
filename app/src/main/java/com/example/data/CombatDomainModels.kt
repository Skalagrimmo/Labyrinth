package com.example.data

/**
 * High-level game session state retained for save/import compatibility.
 */
enum class GameState {
    EXPLORATION,
    COMBAT_START,
    PLAYER_TURN,
    ENEMY_TURN,
    COMBAT_END
}

/** Phase lifecycle within the turn-based combat loop. */
enum class TurnPhase {
    PLAYER_INPUT,
    PLAYER_RESOLVING,
    ENEMY_RESOLVING,
    ROUND_MAINTENANCE,
    COMBAT_VICTORY,
    COMBAT_DEFEAT
}

/** Types of tactical actions available during combat rounds. */
enum class CombatActionType {
    STRIKE,
    DEFEND,
    QUICK_HACK,
    PROGRAM,
    USE_ITEM,
    SCAN,
    FLEE,
    PASS
}

/** Audit and telemetry log entry for a combat action. */
data class TurnActionRecord(
    val roundNumber: Int,
    val actorName: String,
    val isPlayer: Boolean,
    val actionType: CombatActionType,
    val summary: String,
    val damageDealt: Int = 0,
    val shieldAbsorbed: Int = 0,
    val healAmount: Int = 0,
    val isCrit: Boolean = false,
    val isMiss: Boolean = false,
    val statusApplied: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Outcome winner of a combat encounter. */
enum class CombatWinner {
    PLAYER,
    ENEMY,
    ESCAPED
}
