package com.example.data

/** Combat turn enum tracking active combat turn state. */
enum class EngineCombatTurn {
    PLAYER_TURN,
    ENEMY_TURN,
    ANIMATING,
    COMBAT_ENDED
}

/** Status effect snapshot used by combat calculations. */
data class CombatStatusEffect(
    val id: String,
    val name: String,
    val type: StatusEffectType,
    val durationTurns: Int,
    val potency: Int
)

/** Complete immutable snapshot of the turn-based combat state. */
data class CombatEngineState(
    val turn: EngineCombatTurn = EngineCombatTurn.PLAYER_TURN,
    val turnNumber: Int = 1,
    val playerName: String = "V-Netrunner",
    val playerHealth: Int = 100,
    val playerMaxHealth: Int = 100,
    val playerShield: Int = 25,
    val playerMaxShield: Int = 50,
    val playerRam: Int = 12,
    val playerMaxRam: Int = 12,
    val playerLevel: Int = 1,
    val playerStance: String = "Strike",
    val isPlayerDefending: Boolean = false,
    val enemyName: String = "Arasaka ICE-Sentinel",
    val enemyHealth: Int = 80,
    val enemyMaxHealth: Int = 80,
    val enemyShield: Int = 30,
    val enemyMaxShield: Int = 30,
    val enemyBaseDamage: Int = 15,
    val enemyArmor: Int = 5,
    val isEnemyStunned: Boolean = false,
    val activeStatusEffects: List<CombatStatusEffect> = emptyList(),
    val combatLog: List<String> = emptyList(),
    val lastActionSummary: String = "",
    val isCombatOver: Boolean = false,
    val winner: CombatWinner? = null
)

/**
 * Explicit factory for status-effect identity. Randomness is supplied by the caller,
 * keeping the immutable domain model deterministic and side-effect free.
 */
object CombatStatusEffectFactory {
    fun create(
        name: String,
        type: StatusEffectType,
        durationTurns: Int,
        potency: Int,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): CombatStatusEffect = CombatStatusEffect(
        id = random.nextInt(10000, 99999).toString(),
        name = name,
        type = type,
        durationTurns = durationTurns,
        potency = potency
    )
}
