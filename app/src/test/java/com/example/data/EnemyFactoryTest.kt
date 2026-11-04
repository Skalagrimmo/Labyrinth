package com.example.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemyFactoryTest {
    private val archetypes = listOf(
        EnemyArchetype(
            name = "Test ICE",
            description = "Deterministic test archetype",
            asciiArt = "[ICE]",
            minTier = 1
        )
    )

    @Test
    fun `same seed and time create identical enemy`() {
        val first = EnemyFactory.create(2, archetypes, Random(4242), 123456L)
        val second = EnemyFactory.create(2, archetypes, Random(4242), 123456L)

        assertEquals(first, second)
    }

    @Test
    fun `layer below one is normalized before scaling`() {
        val enemy = EnemyFactory.create(0, archetypes, Random(7), 1L)

        assertTrue(enemy.maxIntegrity >= 55)
        assertTrue(enemy.damage >= 12)
        assertTrue(enemy.armor >= 1)
    }
}

class BossFactoryTest {
    @Test
    fun `boss creation is deterministic when id time is fixed`() {
        val first = BossFactory.create(BossType.FIREWALL_SENTINEL, level = 3, idTimeMillis = 99L)
        val second = BossFactory.create(BossType.FIREWALL_SENTINEL, level = 3, idTimeMillis = 99L)

        assertEquals(first, second)
        assertTrue(first.isBoss)
        assertEquals(BossType.FIREWALL_SENTINEL, first.bossType)
    }

    @Test
    fun `boss stats scale with level`() {
        val low = BossFactory.create(BossType.BLACK_ICE_COLOSSUS, level = 1, idTimeMillis = 1L)
        val high = BossFactory.create(BossType.BLACK_ICE_COLOSSUS, level = 5, idTimeMillis = 1L)

        assertTrue(high.maxIntegrity > low.maxIntegrity)
        assertTrue(high.maxShield > low.maxShield)
        assertTrue(high.damage > low.damage)
        assertTrue(high.bountyCredits > low.bountyCredits)
    }
}
