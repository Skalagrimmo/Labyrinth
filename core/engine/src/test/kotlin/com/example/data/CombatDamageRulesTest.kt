package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatDamageRulesTest {
    @Test
    fun `shield absorbs all damage before health`() {
        assertEquals(
            DamageResolution(remainingShield = 15, remainingHealth = 100, shieldAbsorbed = 10, healthDamage = 0),
            CombatDamageRules.resolveShieldFirst(25, 100, 10)
        )
    }

    @Test
    fun `overflow damage reaches health after shield depletion`() {
        assertEquals(
            DamageResolution(remainingShield = 0, remainingHealth = 85, shieldAbsorbed = 5, healthDamage = 15),
            CombatDamageRules.resolveShieldFirst(5, 100, 20)
        )
    }

    @Test
    fun `damage cannot reduce health or shield below zero`() {
        assertEquals(
            DamageResolution(remainingShield = 0, remainingHealth = 0, shieldAbsorbed = 3, healthDamage = 97),
            CombatDamageRules.resolveShieldFirst(3, 7, 100)
        )
    }

    @Test
    fun `zero damage leaves target unchanged`() {
        assertEquals(
            DamageResolution(remainingShield = 4, remainingHealth = 9, shieldAbsorbed = 0, healthDamage = 0),
            CombatDamageRules.resolveShieldFirst(4, 9, 0)
        )
    }
}
