package com.example.data

import kotlin.math.min

enum class UtilityItemEffect { HEAL, RAM, NONE }

data class UtilityItemResolution(
    val playerHealth: Int,
    val playerRam: Int,
    val appliedAmount: Int = 0,
    val effect: UtilityItemEffect = UtilityItemEffect.NONE
)

object CombatItemRules {
    fun resolve(state: CombatEngineState, itemName: String): UtilityItemResolution =
        when (itemName) {
            "NanoMed.sys" -> UtilityItemResolution(
                playerHealth = min(state.playerMaxHealth, state.playerHealth + 35),
                playerRam = state.playerRam,
                appliedAmount = 35,
                effect = UtilityItemEffect.HEAL
            )
            "RAMBoost.exe" -> UtilityItemResolution(
                playerHealth = state.playerHealth,
                playerRam = min(state.playerMaxRam, state.playerRam + 6),
                appliedAmount = 6,
                effect = UtilityItemEffect.RAM
            )
            else -> UtilityItemResolution(
                playerHealth = state.playerHealth,
                playerRam = state.playerRam
            )
        }
}
