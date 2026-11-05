package com.example.data

import kotlin.math.max
import kotlin.math.min

data class EnemyTurnResolution(
    val enemyHealth: Int,
    val enemyShield: Int,
    val playerHealth: Int,
    val playerShield: Int,
    val playerRam: Int,
    val damageDealtToPlayer: Int
)

/** Pure application of an already-selected enemy AI decision. */
object CombatEnemyTurnRules {
    fun resolve(
        state: CombatEngineState,
        decision: EnemyAIDecision
    ): EnemyTurnResolution {
        var enemyHealth = state.enemyHealth
        var enemyShield = state.enemyShield
        var playerHealth = state.playerHealth
        var playerShield = state.playerShield
        var playerRam = state.playerRam
        var damageDealtToPlayer = 0

        when (decision.actionType) {
            EnemyActionType.ATTACK, EnemyActionType.HACK_PLAYER -> {
                var damage = decision.damage
                if (state.isPlayerDefending) {
                    damage = (damage * 0.35f).toInt().coerceAtLeast(2)
                }
                damageDealtToPlayer = damage

                val resolved = CombatDamageRules.resolveShieldFirst(
                    currentShield = playerShield,
                    currentHealth = playerHealth,
                    damage = damage
                )
                playerShield = resolved.remainingShield
                playerHealth = resolved.remainingHealth

                if (decision.ramDrain > 0) {
                    playerRam = max(0, playerRam - decision.ramDrain)
                }
            }

            EnemyActionType.HEAL -> {
                enemyHealth = min(state.enemyMaxHealth, state.enemyHealth + decision.healAmount)
            }

            EnemyActionType.FORTIFY_ICE -> {
                enemyShield = min(state.enemyMaxShield, state.enemyShield + decision.shieldAmount)
            }
        }

        return EnemyTurnResolution(
            enemyHealth = enemyHealth,
            enemyShield = enemyShield,
            playerHealth = playerHealth,
            playerShield = playerShield,
            playerRam = playerRam,
            damageDealtToPlayer = damageDealtToPlayer
        )
    }
}
