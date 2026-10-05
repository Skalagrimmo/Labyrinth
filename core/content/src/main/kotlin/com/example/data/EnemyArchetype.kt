package com.example.data

/**
 * Data-only combat profile used by built-in content and Markdown mods.
 */
data class EnemyArchetype(
    val name: String,
    val description: String,
    val asciiArt: String,
    val minTier: Int,
    val hpMult: Float = 1.0f,
    val shieldMult: Float = 1.0f,
    val dmgMult: Float = 1.0f,
    val armorBonus: Int = 0,
    val bountyMult: Float = 1.0f,
    val statusEffects: List<Pair<StatusEffectType, Int>> = emptyList()
)

/**
 * Runtime overlay for mod-provided enemy profiles.
 * Built-in profiles remain immutable in GameContentCatalog.
 */
object EnemyArchetypeRegistry {
    private val registered = mutableListOf<EnemyArchetype>()

    fun register(archetypes: List<EnemyArchetype>) {
        registered.addAll(archetypes)
    }

    fun registeredArchetypes(): List<EnemyArchetype> = registered.toList()
}
