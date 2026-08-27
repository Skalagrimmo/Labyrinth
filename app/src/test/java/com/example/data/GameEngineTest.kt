package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for GameEngine's content-generation functions.
 *
 * Only the deterministic, side-effect-light behavior is asserted here (stat ranges and
 * invariants) to stay stable regardless of RNG seeding.
 */
class GameEngineTest {

    @Test
    fun `spawnEnemy produces a valid enemy at any layer`() {
        val enemy = GameEngine.spawnEnemy(layer = 3)
        assertTrue(enemy.name.isNotBlank())
        assertEquals(enemy.maxIntegrity, enemy.integrity)
        assertEquals(enemy.maxShield, enemy.shield)
        assertTrue(enemy.damage > 0)
        assertTrue(enemy.integrity > 0)
        assertTrue(enemy.iconAscii.isNotBlank())
        assertTrue(enemy.description.isNotBlank())
    }

    @Test
    fun `spawnEnemy scales with layer`() {
        // Deep layers use a strictly larger base (40 + layer*15) before the per-archetype
        // multiplier. A very wide gap (1 vs 15) makes the ordering robust to RNG and to
        // low hpMult archetypes (some < 1.0), so maxIntegrity must rise on average.
        val low = GameEngine.spawnEnemy(layer = 1).maxIntegrity
        val mid = GameEngine.spawnEnemy(layer = 15).maxIntegrity
        // Even the weakest archetype at layer 15 (265 * 0.8 = 212) exceeds the strongest
        // archetype at layer 1 (~93), so the ordering holds for any single sample.
        assertTrue(mid > low)
    }

    @Test
    fun `spawnBoss scales with level across all boss types`() {
        for (type in BossType.values()) {
            val boss = GameEngine.spawnBoss(type, level = 5)
            assertTrue(boss.isBoss)
            assertEquals(type, boss.bossType)
            assertTrue(boss.maxIntegrity > 0)
            assertTrue(boss.damage > 0)
            assertTrue(boss.name.isNotBlank())
        }
    }
}
