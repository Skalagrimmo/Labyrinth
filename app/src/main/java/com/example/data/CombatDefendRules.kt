package com.example.data

import kotlin.math.min

data class DefendResolution(
    val shieldRestored: Int,
    val playerShield: Int
)

object CombatDefendRules {
    fun resolve(state: CombatEngineState): DefendResolution {
        val restored = 15 + (state.playerLevel * 3)
        return DefendResolution(
            shieldRestored = restored,
            playerShield = min(state.playerMaxShield, state.playerShield + restored)
        )
    }
}
