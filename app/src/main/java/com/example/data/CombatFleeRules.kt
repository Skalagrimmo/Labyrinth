package com.example.data

import kotlin.random.Random

data class FleeResolution(
    val escaped: Boolean,
    val turn: EngineCombatTurn,
    val isCombatOver: Boolean,
    val winner: CombatWinner?
)

object CombatFleeRules {
    const val FLEE_CHANCE_PERCENT = 65

    fun resolve(random: Random): FleeResolution {
        val escaped = random.nextInt(100) < FLEE_CHANCE_PERCENT
        return FleeResolution(
            escaped = escaped,
            turn = if (escaped) EngineCombatTurn.COMBAT_ENDED else EngineCombatTurn.ENEMY_TURN,
            isCombatOver = escaped,
            winner = if (escaped) CombatWinner.ESCAPED else null
        )
    }
}
