package com.example.ui

data class CombatHackingPatternState(
    val targetPattern: List<String>,
    val userSequence: List<String> = emptyList(),
    val availablePool: List<String>,
    val timeRemainingSeconds: Int = 12,
    val maxTimeSeconds: Int = 12,
    val attemptsRemaining: Int = 3,
    val maxAttempts: Int = 3,
    val enemyName: String,
    val potentialDamage: Int
)
