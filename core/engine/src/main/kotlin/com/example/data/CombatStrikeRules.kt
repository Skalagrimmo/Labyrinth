package com.example.data

import kotlin.math.max
import kotlin.random.Random

data class StrikeResolution(
    val hitChance: Int,
    val hitRoll: Int,
    val wasMiss: Boolean,
    val wasCrit: Boolean,
    val damageDealt: Int,
    val enemyShield: Int,
    val enemyHealth: Int
)

/** Pure strike calculation with caller-supplied RNG for reproducible rolls. */
object CombatStrikeRules {
    fun resolve(state: CombatEngineState, random: Random): StrikeResolution {
        val hitChance = (75 + state.playerLevel * 2 + state.playerRam).coerceIn(25, 95)
        val hitRoll = random.nextInt(100)
        if (hitRoll >= hitChance) {
            return StrikeResolution(
                hitChance = hitChance,
                hitRoll = hitRoll,
                wasMiss = true,
                wasCrit = false,
                damageDealt = 0,
                enemyShield = state.enemyShield,
                enemyHealth = state.enemyHealth
            )
        }

        var rawDamage = 18 + (state.playerLevel * 3)
        val wasCrit = random.nextInt(100) < 20
        if (wasCrit) rawDamage = (rawDamage * 1.75f).toInt()

        val effectiveArmor = if (wasCrit) (state.enemyArmor * 0.5f).toInt() else state.enemyArmor
        val damageDealt = max(3, rawDamage - effectiveArmor)
        val damage = CombatDamageRules.resolveShieldFirst(
            currentShield = state.enemyShield,
            currentHealth = state.enemyHealth,
            damage = damageDealt
        )
        return StrikeResolution(
            hitChance = hitChance,
            hitRoll = hitRoll,
            wasMiss = false,
            wasCrit = wasCrit,
            damageDealt = damageDealt,
            enemyShield = damage.remainingShield,
            enemyHealth = damage.remainingHealth
        )
    }
}
