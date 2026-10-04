package com.example.data

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Dedicated Turn-Based Combat System Engine.
 * Encapsulates game state management, turn execution sequence, Morrowind-style dice calculations,
 * status effect ticks, and AI combat decision trees.
 */
object TurnBasedCombatEngine {

    /**
     * Executes a player turn action and advances turn state.
     */
    fun processPlayerAction(
        action: PlayerCombatAction,
        currentState: CombatEngineState,
        random: Random = Random.Default
    ): CombatTurnResult {
        if (currentState.isCombatOver) {
            return CombatTurnResult(currentState, listOf("Combat has already concluded."))
        }

        val logs = mutableListOf<String>()
        var state = currentState.copy(turn = EngineCombatTurn.ANIMATING)
        var dmgToEnemy = 0
        var wasCrit = false
        var wasMiss = false

        // 1. Process player action
        when (action) {
            is PlayerCombatAction.Strike -> {
                val resolution = CombatStrikeRules.resolve(state, random)
                wasMiss = resolution.wasMiss
                wasCrit = resolution.wasCrit
                dmgToEnemy = resolution.damageDealt

                if (resolution.wasMiss) {
                    logs.add("⚔️ STRIKE MISSED! Weapon swung wide [Roll: ${resolution.hitRoll} vs Chance: ${resolution.hitChance}%].")
                } else {
                    if (resolution.wasCrit) {
                        logs.add("💥 CRITICAL STRIKE! Dealt maximum kinetic damage!")
                    }
                    state = state.copy(
                        enemyShield = resolution.enemyShield,
                        enemyHealth = resolution.enemyHealth,
                        playerStance = "Strike"
                    )
                    logs.add("⚔️ HIT! Dealt $dmgToEnemy damage to ${state.enemyName}.")
                }
            }

            is PlayerCombatAction.Defend -> {
                val resolution = CombatDefendRules.resolve(state)
                state = state.copy(
                    playerShield = resolution.playerShield,
                    isPlayerDefending = true
                )
                logs.add("🛡️ DEFENSIVE FIREWALL RAISED: Shield restored by ${resolution.shieldRestored} points!")
            }

            is PlayerCombatAction.RunProgram -> {
                val resolution = CombatProgramRules.resolve(state, action)
                if (!resolution.canExecute) {
                    logs.add("⚠️ INSUFFICIENT RAM: Requires ${action.ramCost} MB RAM.")
                    return CombatTurnResult(currentState, logs)
                }

                dmgToEnemy = resolution.damageDealt
                if (action.damage > 0) {
                    logs.add("⚡ EXPLOIT EXECUTED: ${action.programName} dealt $dmgToEnemy digital damage!")
                }
                if (action.heal > 0) {
                    logs.add("🩹 SYSTEM REPAIR: Restored ${action.heal} integrity.")
                }
                if (action.shield > 0) {
                    logs.add("🛡️ HARDENED SHIELD: Boosted defense by ${action.shield}.")
                }

                state = state.copy(
                    playerRam = resolution.remainingRam,
                    enemyShield = resolution.enemyShield,
                    enemyHealth = resolution.enemyHealth,
                    playerHealth = resolution.playerHealth,
                    playerShield = resolution.playerShield
                )
            }

            is PlayerCombatAction.ConsumeItem -> {
                val resolution = CombatItemRules.resolve(state, action.itemName)
                state = state.copy(
                    playerHealth = resolution.playerHealth,
                    playerRam = resolution.playerRam
                )
                when (resolution.effect) {
                    UtilityItemEffect.HEAL ->
                        logs.add("💊 CONSUMED NanoMed.sys: Reclaimed ${resolution.appliedAmount} Integrity.")
                    UtilityItemEffect.RAM ->
                        logs.add("🧪 CONSUMED RAMBoost.exe: Allocated ${resolution.appliedAmount} MB RAM.")
                    UtilityItemEffect.NONE ->
                        logs.add("USED UTILITY ITEM: ${action.itemName}.")
                }
            }

            is PlayerCombatAction.ScanEnemy -> {
                state = state.copy(isEnemyStunned = true)
                logs.add("🔍 SYSTEM SCAN COMPLETE: Enemy telemetry analyzed. Hostile signal stunned for 1 turn!")
            }

            is PlayerCombatAction.Flee -> {
                val fleeChance = 65
                if (random.nextInt(100) < fleeChance) {
                    logs.add("🏃 ESCAPE SUCCESSFUL! Dissolved neural link and retreated.")
                    val finalState = state.copy(
                        isCombatOver = true,
                        winner = CombatWinner.ESCAPED,
                        turn = EngineCombatTurn.COMBAT_ENDED,
                        combatLog = state.combatLog + logs
                    )
                    return CombatTurnResult(finalState, logs)
                } else {
                    logs.add("⚠️ ESCAPE FAILED! Hostile ICE locked the gateway connection.")
                }
            }
        }

        // 2. Check if enemy defeated
        if (state.enemyHealth <= 0) {
            logs.add("🏆 VICTORY! Hostile ${state.enemyName} system purged.")
            val victoryState = state.copy(
                isCombatOver = true,
                winner = CombatWinner.PLAYER,
                turn = EngineCombatTurn.COMBAT_ENDED,
                combatLog = state.combatLog + logs
            )
            return CombatTurnResult(victoryState, logs, damageDealtToEnemy = dmgToEnemy, wasCrit = wasCrit, wasMiss = wasMiss)
        }

        // Advance to Enemy Turn
        state = state.copy(turn = EngineCombatTurn.ENEMY_TURN)
        return CombatTurnResult(state, logs, damageDealtToEnemy = dmgToEnemy, wasCrit = wasCrit, wasMiss = wasMiss)
    }

    /**
     * Executes enemy turn AI logic using EnemyCombatAIScript decision behavior and advances back to Player turn.
     */
    fun processEnemyTurn(
        currentState: CombatEngineState,
        proximityDistance: Int = 1,
        random: Random = Random.Default
    ): CombatTurnResult {
        if (currentState.isCombatOver) {
            return CombatTurnResult(currentState, emptyList())
        }

        val logs = mutableListOf<String>()
        var state = currentState.copy(turn = EngineCombatTurn.ANIMATING)
        var dmgToPlayer = 0

        // Handle enemy stun
        if (state.isEnemyStunned) {
            logs.add("⚡ ENEMY STUNNED: ${state.enemyName} is recalibrating and skips action.")
            state = state.copy(
                isEnemyStunned = false,
                turn = EngineCombatTurn.PLAYER_TURN,
                turnNumber = state.turnNumber + 1,
                playerRam = min(state.playerMaxRam, state.playerRam + 2),
                isPlayerDefending = false,
                combatLog = state.combatLog + logs
            )
            return CombatTurnResult(state, logs)
        }

        // Evaluate AI Decision using EnemyCombatAIScript based on health, shield, RAM, and proximity distance
        val decision = EnemyCombatAIScript.evaluateAction(
            enemyHealth = state.enemyHealth,
            enemyMaxHealth = state.enemyMaxHealth,
            enemyShield = state.enemyShield,
            enemyMaxShield = state.enemyMaxShield,
            enemyBaseDamage = state.enemyBaseDamage,
            playerHealth = state.playerHealth,
            playerRam = state.playerRam,
            proximityDistance = proximityDistance,
            random = random
        )

        logs.add(decision.logMessage)

        val resolution = CombatEnemyTurnRules.resolve(state, decision)
        dmgToPlayer = resolution.damageDealtToPlayer
        state = state.copy(
            enemyHealth = resolution.enemyHealth,
            enemyShield = resolution.enemyShield,
            playerShield = resolution.playerShield,
            playerHealth = resolution.playerHealth,
            playerRam = resolution.playerRam
        )

        // Check player defeat
        val isDefeated = state.playerHealth <= 0
        val winner = if (isDefeated) CombatWinner.ENEMY else null

        if (isDefeated) {
            logs.add("💀 SYSTEM OVERLOAD: Core Integrity breached. Player defeated.")
        }

        // RAM recovery tick at turn end
        val recoveredRam = min(state.playerMaxRam, state.playerRam + 2)

        state = state.copy(
            playerRam = recoveredRam,
            isPlayerDefending = false,
            turn = if (isDefeated) EngineCombatTurn.COMBAT_ENDED else EngineCombatTurn.PLAYER_TURN,
            turnNumber = state.turnNumber + 1,
            isCombatOver = isDefeated,
            winner = winner,
            combatLog = state.combatLog + logs
        )

        return CombatTurnResult(
            newState = state,
            logMessages = logs,
            damageDealtToPlayer = dmgToPlayer
        )
    }

}
}
