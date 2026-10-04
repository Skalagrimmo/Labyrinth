package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatEnemyTurnRulesTest {
    @Test
    fun `defending player takes legacy 35 percent damage with minimum two`() {
        val state = CombatEngineState(
            playerHealth = 100, playerShield = 0, isPlayerDefending = true
        )
        val result = CombatEnemyTurnRules.resolve(
            state,
            EnemyAIDecision(EnemyActionType.ATTACK, "test", damage = 20)
        )
        assertEquals(7, result.damageDealtToPlayer)
        assertEquals(93, result.playerHealth)
    }

    @Test
    fun `enemy hack applies damage and drains RAM`() {
        val state = CombatEngineState(playerHealth = 100, playerShield = 5, playerRam = 8)
        val result = CombatEnemyTurnRules.resolve(
            state,
            EnemyAIDecision(EnemyActionType.HACK_PLAYER, "hack", damage = 12, ramDrain = 4)
        )
        assertEquals(0, result.playerShield)
        assertEquals(93, result.playerHealth)
        assertEquals(4, result.playerRam)
    }

    @Test
    fun `enemy healing and fortification are capped`() {
        val heal = CombatEnemyTurnRules.resolve(
            CombatEngineState(enemyHealth = 95, enemyMaxHealth = 100),
            EnemyAIDecision(EnemyActionType.HEAL, "heal", healAmount = 20)
        )
        val fortify = CombatEnemyTurnRules.resolve(
            CombatEngineState(enemyShield = 28, enemyMaxShield = 30),
            EnemyAIDecision(EnemyActionType.FORTIFY_ICE, "fortify", shieldAmount = 20)
        )
        assertEquals(100, heal.enemyHealth)
        assertEquals(30, fortify.enemyShield)
    }
}
