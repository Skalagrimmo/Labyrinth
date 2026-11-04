package com.example.data

import kotlin.math.max

data class DamageResolution(
    val remainingShield: Int,
    val remainingHealth: Int,
    val shieldAbsorbed: Int,
    val healthDamage: Int
)

/** Pure shield-first damage arithmetic shared by combat actions. */
object CombatDamageRules {
    fun resolveShieldFirst(
        currentShield: Int,
        currentHealth: Int,
        damage: Int
    ): DamageResolution {
        val remainingShield = max(0, currentShield - damage)
        val shieldAbsorbed = currentShield - remainingShield
        val healthDamage = damage - shieldAbsorbed
        val remainingHealth = max(0, currentHealth - healthDamage)
        return DamageResolution(
            remainingShield = remainingShield,
            remainingHealth = remainingHealth,
            shieldAbsorbed = shieldAbsorbed,
            healthDamage = healthDamage
        )
    }
}
