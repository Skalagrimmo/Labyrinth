package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatScanRulesTest {
    @Test
    fun `scan stuns enemy for the next turn`() {
        assertEquals(
            true,
            CombatScanRules.resolve().isEnemyStunned
        )
    }
}
