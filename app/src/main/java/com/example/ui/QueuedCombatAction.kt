package com.example.ui

/** Action types that can be queued from the combat HUD. */
enum class QueuedCombatActionType { STRIKE, DEFEND, SCAN }

/** One entry of the combat action queue (UI vocabulary: id, type, HUD label). */
data class QueuedCombatAction(
    val id: Long,
    val type: QueuedCombatActionType,
    val label: String
)
