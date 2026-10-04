package com.example.data

data class CombatScanResolution(
    val isEnemyStunned: Boolean
)

object CombatScanRules {
    fun resolve(): CombatScanResolution =
        CombatScanResolution(isEnemyStunned = true)
}
