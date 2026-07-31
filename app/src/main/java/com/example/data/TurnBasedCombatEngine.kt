package com.example.data

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Combat turn enum tracking active combat turn state.
 */
enum class EngineCombatTurn {
    PLAYER_TURN,
    ENEMY_TURN,
    ANIMATING,
    COMBAT_ENDED
}

/**
 * Outcome winner of the combat encounter.
 */
enum class CombatWinner {
    PLAYER,
    ENEMY,
    ESCAPED
}

/**
 * Status effects applied during turn-based combat engine calculations.
 */
data class CombatStatusEffect(
    val id: String = Random.nextInt(10000, 99999).toString(),
    val name: String,
    val type: StatusEffectType,
    val durationTurns: Int,
    val potency: Int
)

/**
 * Complete immutable snapshot of the Turn-Based Combat State.
 */
data class CombatEngineState(
    val turn: EngineCombatTurn = EngineCombatTurn.PLAYER_TURN,
    val turnNumber: Int = 1,
    
    // Player Fighter Stats
    val playerName: String = "V-Netrunner",
    val playerHealth: Int = 100,
    val playerMaxHealth: Int = 100,
    val playerShield: Int = 25,
    val playerMaxShield: Int = 50,
    val playerRam: Int = 12,
    val playerMaxRam: Int = 12,
    val playerLevel: Int = 1,
    val playerStance: String = "Slash",
    val isPlayerDefending: Boolean = false,
    
    // Hostile Cyber-Enemy Stats
    val enemyName: String = "Arasaka ICE-Sentinel",
    val enemyHealth: Int = 80,
    val enemyMaxHealth: Int = 80,
    val enemyShield: Int = 30,
    val enemyMaxShield: Int = 30,
    val enemyBaseDamage: Int = 15,
    val enemyArmor: Int = 5,
    val isEnemyStunned: Boolean = false,
    
    // Status Effects & Logs
    val activeStatusEffects: List<CombatStatusEffect> = emptyList(),
    val combatLog: List<String> = emptyList(),
    val lastActionSummary: String = "",
    val isCombatOver: Boolean = false,
    val winner: CombatWinner? = null
)

/**
 * Sealed class defining all valid player tactical inputs in turn-based combat.
 */
sealed class PlayerCombatAction {
    data class Strike(val stance: String = "Slash") : PlayerCombatAction()
    object Defend : PlayerCombatAction()
    data class RunProgram(
        val programName: String,
        val ramCost: Int,
        val damage: Int,
        val heal: Int,
        val shield: Int
    ) : PlayerCombatAction()
    data class ConsumeItem(val itemName: String) : PlayerCombatAction()
    object ScanEnemy : PlayerCombatAction()
    object Flee : PlayerCombatAction()
}

/**
 * Engine result container returned after state calculation.
 */
data class CombatTurnResult(
    val newState: CombatEngineState,
    val logMessages: List<String>,
    val damageDealtToEnemy: Int = 0,
    val damageDealtToPlayer: Int = 0,
    val wasCrit: Boolean = false,
    val wasMiss: Boolean = false
)

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
        currentState: CombatEngineState
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
                val stance = action.stance
                val baseChance = when (stance) {
                    "Slash" -> 70
                    "Chop" -> 55
                    "Thrust" -> 85
                    else -> 70
                }
                val hitChance = (baseChance + state.playerLevel * 2 + state.playerRam).coerceIn(25, 95)
                val roll = Random.nextInt(100)

                if (roll >= hitChance) {
                    wasMiss = true
                    logs.add("⚔️ STRIKE MISSED! Weapon swung wide [Roll: $roll vs Chance: $hitChance%].")
                } else {
                    val baseDmg = when (stance) {
                        "Slash" -> 16
                        "Chop" -> 24
                        "Thrust" -> 11
                        else -> 16
                    }
                    var rawDmg = baseDmg + (state.playerLevel * 3)
                    
                    // Crit check
                    if (Random.nextInt(100) < 20) {
                        wasCrit = true
                        rawDmg = (rawDmg * 1.75f).toInt()
                        logs.add("💥 CRITICAL STRIKE! Dealt maximum kinetic damage!")
                    }

                    val effectiveArmor = if (wasCrit) (state.enemyArmor * 0.5f).toInt() else state.enemyArmor
                    dmgToEnemy = max(3, rawDmg - effectiveArmor)

                    // Apply damage to shield first, then core health
                    val (remShield, remHealth) = applyDamageToShieldAndHealth(
                        currentShield = state.enemyShield,
                        currentHealth = state.enemyHealth,
                        damage = dmgToEnemy
                    )

                    state = state.copy(
                        enemyShield = remShield,
                        enemyHealth = remHealth,
                        playerStance = stance
                    )

                    logs.add("⚔️ HIT! Dealt $dmgToEnemy damage to ${state.enemyName}.")
                }
            }

            is PlayerCombatAction.Defend -> {
                val shieldRestored = 15 + (state.playerLevel * 3)
                val newShield = min(state.playerMaxShield, state.playerShield + shieldRestored)
                state = state.copy(
                    playerShield = newShield,
                    isPlayerDefending = true
                )
                logs.add("🛡️ DEFENSIVE FIREWALL RAISED: Shield restored by $shieldRestored points!")
            }

            is PlayerCombatAction.RunProgram -> {
                if (state.playerRam < action.ramCost) {
                    logs.add("⚠️ INSUFFICIENT RAM: Requires ${action.ramCost} MB RAM.")
                    return CombatTurnResult(currentState, logs)
                }

                val newRam = state.playerRam - action.ramCost
                var newEnemyShield = state.enemyShield
                var newEnemyHealth = state.enemyHealth
                var newPlayerHealth = state.playerHealth
                var newPlayerShield = state.playerShield

                if (action.damage > 0) {
                    dmgToEnemy = action.damage + (state.playerLevel * 2)
                    val (rShield, rHealth) = applyDamageToShieldAndHealth(newEnemyShield, newEnemyHealth, dmgToEnemy)
                    newEnemyShield = rShield
                    newEnemyHealth = rHealth
                    logs.add("⚡ EXPLOIT EXECUTED: ${action.programName} dealt $dmgToEnemy digital damage!")
                }

                if (action.heal > 0) {
                    newPlayerHealth = min(state.playerMaxHealth, newPlayerHealth + action.heal)
                    logs.add("🩹 SYSTEM REPAIR: Restored ${action.heal} integrity.")
                }

                if (action.shield > 0) {
                    newPlayerShield = min(state.playerMaxShield, newPlayerShield + action.shield)
                    logs.add("🛡️ HARDENED SHIELD: Boosted defense by ${action.shield}.")
                }

                state = state.copy(
                    playerRam = newRam,
                    enemyShield = newEnemyShield,
                    enemyHealth = newEnemyHealth,
                    playerHealth = newPlayerHealth,
                    playerShield = newPlayerShield
                )
            }

            is PlayerCombatAction.ConsumeItem -> {
                when (action.itemName) {
                    "NanoMed.sys" -> {
                        val heal = 35
                        val newHp = min(state.playerMaxHealth, state.playerHealth + heal)
                        state = state.copy(playerHealth = newHp)
                        logs.add("💊 CONSUMED NanoMed.sys: Reclaimed $heal Integrity.")
                    }
                    "RAMBoost.exe" -> {
                        val boost = 6
                        val newRam = min(state.playerMaxRam, state.playerRam + boost)
                        state = state.copy(playerRam = newRam)
                        logs.add("🧪 CONSUMED RAMBoost.exe: Allocated $boost MB RAM.")
                    }
                    else -> {
                        logs.add("USED UTILITY ITEM: ${action.itemName}.")
                    }
                }
            }

            is PlayerCombatAction.ScanEnemy -> {
                state = state.copy(isEnemyStunned = true)
                logs.add("🔍 SYSTEM SCAN COMPLETE: Enemy telemetry analyzed. Hostile signal stunned for 1 turn!")
            }

            is PlayerCombatAction.Flee -> {
                val fleeChance = 65
                if (Random.nextInt(100) < fleeChance) {
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
        proximityDistance: Int = 1
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
            proximityDistance = proximityDistance
        )

        logs.add(decision.logMessage)

        var newEnemyHealth = state.enemyHealth
        var newEnemyShield = state.enemyShield
        var remPlayerShield = state.playerShield
        var remPlayerHp = state.playerHealth
        var newPlayerRam = state.playerRam

        when (decision.actionType) {
            EnemyActionType.ATTACK, EnemyActionType.HACK_PLAYER -> {
                var rawDmg = decision.damage
                if (state.isPlayerDefending) {
                    rawDmg = (rawDmg * 0.35f).toInt().coerceAtLeast(2)
                    logs.add("🛡️ FIREWALL DAMPENING: Player defense reduced incoming impact to $rawDmg!")
                }
                dmgToPlayer = rawDmg
                val (rShield, rHp) = applyDamageToShieldAndHealth(
                    currentShield = state.playerShield,
                    currentHealth = state.playerHealth,
                    damage = dmgToPlayer
                )
                remPlayerShield = rShield
                remPlayerHp = rHp

                if (decision.ramDrain > 0) {
                    newPlayerRam = max(0, state.playerRam - decision.ramDrain)
                    logs.add("💾 RAM DRAIN: Player RAM capacity depleted by ${decision.ramDrain} MB.")
                }
            }

            EnemyActionType.HEAL -> {
                newEnemyHealth = min(state.enemyMaxHealth, state.enemyHealth + decision.healAmount)
            }

            EnemyActionType.FORTIFY_ICE -> {
                newEnemyShield = min(state.enemyMaxShield, state.enemyShield + decision.shieldAmount)
            }
        }

        // Check player defeat
        val isDefeated = remPlayerHp <= 0
        val winner = if (isDefeated) CombatWinner.ENEMY else null

        if (isDefeated) {
            logs.add("💀 SYSTEM OVERLOAD: Core Integrity breached. Player defeated.")
        }

        // RAM recovery tick at turn end
        val recoveredRam = min(state.playerMaxRam, newPlayerRam + 2)

        state = state.copy(
            enemyHealth = newEnemyHealth,
            enemyShield = newEnemyShield,
            playerShield = remPlayerShield,
            playerHealth = remPlayerHp,
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

    private fun applyDamageToShieldAndHealth(
        currentShield: Int,
        currentHealth: Int,
        damage: Int
    ): Pair<Int, Int> {
        val remShield = max(0, currentShield - damage)
        val shieldAbsorbed = currentShield - remShield
        val hpDamage = damage - shieldAbsorbed
        val remHp = max(0, currentHealth - hpDamage)
        return Pair(remShield, remHp)
    }
}
