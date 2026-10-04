package com.example.data

import kotlin.random.Random

/**
 * Creates regular enemies from the built-in and mod-registered archetype catalogs.
 * Randomness and id time are injectable so generation can be characterized deterministically.
 */
object EnemyFactory {
    fun create(
        layer: Int,
        archetypes: List<EnemyArchetype>,
        random: Random = Random.Default,
        idTimeMillis: Long = System.currentTimeMillis()
    ): Enemy {
        require(archetypes.isNotEmpty()) { "Enemy archetype catalog must not be empty." }

        val effectiveLayer = layer.coerceAtLeast(1)
        val candidates = archetypes.filter { it.minTier <= effectiveLayer }
        val chosen = if (candidates.isEmpty()) archetypes.first() else {
            val weighted = buildList {
                for (candidate in candidates) {
                    repeat(2 + (effectiveLayer - candidate.minTier)) { add(candidate) }
                }
            }
            weighted[random.nextInt(weighted.size)]
        }

        val isElite = random.nextInt(100) < (8 + effectiveLayer).coerceAtMost(20)
        val eliteHpMult = if (isElite) 1.6f else 1f
        val eliteShieldMult = if (isElite) 1.4f else 1f
        val eliteDmgMult = if (isElite) 1.35f else 1f
        val eliteArmorBonus = if (isElite) 4 else 0
        val eliteBountyMult = if (isElite) 2f else 1f

        val integrity = ((40 + effectiveLayer * 15) * chosen.hpMult * eliteHpMult).toInt() + random.nextInt(15)
        val shield = ((15 + effectiveLayer * 10) * chosen.shieldMult * eliteShieldMult).toInt() + random.nextInt(10)
        val damage = ((8 + effectiveLayer * 4) * chosen.dmgMult * eliteDmgMult).toInt() + random.nextInt(5)
        val armor = effectiveLayer + chosen.armorBonus + eliteArmorBonus + random.nextInt(2)
        val bounty = ((50 + effectiveLayer * 25) * chosen.bountyMult * eliteBountyMult).toInt() + random.nextInt(30)

        val effects = chosen.statusEffects.map { (type, turns) ->
            ActiveStatusEffect(type = type, turnsRemaining = turns, sourceName = chosen.name)
        }.toMutableList()
        if (isElite) {
            effects += ActiveStatusEffect(
                type = StatusEffectType.FORTIFIED,
                turnsRemaining = 3,
                sourceName = "ELITE PROTOCOL"
            )
        }

        return Enemy(
            id = "enemy_${idTimeMillis}_${random.nextInt(10000)}",
            name = if (isElite) "[ELITE] ${chosen.name}" else chosen.name,
            maxIntegrity = integrity,
            integrity = integrity,
            maxShield = shield,
            shield = shield,
            damage = damage,
            armor = armor,
            iconAscii = chosen.asciiArt,
            bountyCredits = bounty,
            description = if (isElite) "ELITE BLACK-ICE VARIANT // ${chosen.description}" else chosen.description,
            statusEffects = effects,
            isElite = isElite
        )
    }
}
