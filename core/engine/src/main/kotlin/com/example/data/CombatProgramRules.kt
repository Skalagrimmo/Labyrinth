package com.example.data

import kotlin.math.min

data class ProgramResolution(
    val canExecute: Boolean,
    val remainingRam: Int,
    val enemyShield: Int,
    val enemyHealth: Int,
    val playerHealth: Int,
    val playerShield: Int,
    val damageDealt: Int
)

/** Pure resource and stat resolution for program execution. */
object CombatProgramRules {
    fun resolve(
        state: CombatEngineState,
        action: PlayerCombatAction.RunProgram
    ): ProgramResolution {
        if (state.playerRam < action.ramCost) {
            return ProgramResolution(
                canExecute = false,
                remainingRam = state.playerRam,
                enemyShield = state.enemyShield,
                enemyHealth = state.enemyHealth,
                playerHealth = state.playerHealth,
                playerShield = state.playerShield,
                damageDealt = 0
            )
        }

        val damageDealt = if (action.damage > 0) action.damage + (state.playerLevel * 2) else 0
        val damage = CombatDamageRules.resolveShieldFirst(
            currentShield = state.enemyShield,
            currentHealth = state.enemyHealth,
            damage = damageDealt
        )

        return ProgramResolution(
            canExecute = true,
            remainingRam = state.playerRam - action.ramCost,
            enemyShield = damage.remainingShield,
            enemyHealth = damage.remainingHealth,
            playerHealth = if (action.heal > 0) min(state.playerMaxHealth, state.playerHealth + action.heal) else state.playerHealth,
            playerShield = if (action.shield > 0) min(state.playerMaxShield, state.playerShield + action.shield) else state.playerShield,
            damageDealt = damageDealt
        )
    }
}
