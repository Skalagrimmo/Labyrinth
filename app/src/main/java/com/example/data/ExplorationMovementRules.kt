package com.example.data

import kotlin.random.Random

data class ExplorationMoveResult(
    val nextX: Int,
    val nextY: Int,
    val wasScrambled: Boolean
)

data class ExplorationTurnResult(
    val direction: Direction,
    val wasScrambled: Boolean
)

object ExplorationMovementRules {
    private const val STORM_SCRAMBLE_CHANCE = 0.40f
    private const val RAM_RECOVERY_CHANCE_PERCENT = 40

    fun resolveMove(
        x: Int,
        y: Int,
        direction: Direction,
        forward: Boolean,
        weather: CyberWeather,
        random: Random = Random.Default
    ): ExplorationMoveResult {
        var nextX = x + if (forward) direction.dx else -direction.dx
        var nextY = y + if (forward) direction.dy else -direction.dy
        var wasScrambled = false

        if (weather.isStorm() && random.nextFloat() < STORM_SCRAMBLE_CHANCE) {
            val scrambledDirs = if (forward) {
                Direction.VALUES.filter { it != direction }
            } else {
                Direction.VALUES.toList()
            }
            val scrambledDirection = scrambledDirs[random.nextInt(scrambledDirs.size)]
            nextX = x + scrambledDirection.dx
            nextY = y + scrambledDirection.dy
            wasScrambled = true
        }

        return ExplorationMoveResult(
            nextX = nextX,
            nextY = nextY,
            wasScrambled = wasScrambled
        )
    }

    fun resolveTurn(
        direction: Direction,
        turnLeft: Boolean,
        weather: CyberWeather,
        random: Random = Random.Default
    ): ExplorationTurnResult {
        val scrambled = weather.isStorm() && random.nextFloat() < STORM_SCRAMBLE_CHANCE
        val nextDirection = when {
            scrambled && turnLeft -> direction.turnRight()
            scrambled -> direction.turnLeft()
            turnLeft -> direction.turnLeft()
            else -> direction.turnRight()
        }
        return ExplorationTurnResult(
            direction = nextDirection,
            wasScrambled = scrambled
        )
    }

    fun recoverRam(
        ram: Int,
        maxRam: Int,
        random: Random = Random.Default
    ): Int {
        val gained = if (random.nextInt(100) < RAM_RECOVERY_CHANCE_PERCENT) 1 else 0
        return minOf(maxRam, ram + gained)
    }

    private fun CyberWeather.isStorm(): Boolean =
        this == CyberWeather.DATA_STORM || this == CyberWeather.APEX_STORM
}
