package com.example.data

import com.example.core.model.BossType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatLootDropSystemTest {

    private data class ExpectedLoot(
        val itemType: String,
        val rarity: LootRarity,
        val description: String,
        val creditBonusMultiplier: Float
    )

    @Test
    fun `boss loot table stays pinned per boss type`() {
        val drop = CombatLootDropSystem.generateBossLootDrop(BossType.FIREWALL_SENTINEL, enemyLevel = 3)

        assertEquals("SentinelFirewallBreaker.exe", drop.itemName)
        assertEquals("PROGRAM", drop.itemType)
        assertEquals(LootRarity.LEGENDARY, drop.rarity)
        assertEquals("Bypasses all enemy armor. Deals 50 piercing damage. Bonus: +25% damage vs bosses.", drop.description)
        // (800 + 3 * 100) * multiplier(5)
        assertEquals(5500, drop.totalCreditsEarned)
        // 200 + 3 * 50 + roll[20, 49]
        assertTrue("xp roll out of range: ${drop.xpEarned}", drop.xpEarned in 370..399)
        assertEquals(
            "🎁 BOSS LOOT DROPPED: [★ LEGENDARY ★] SentinelFirewallBreaker.exe — Bypasses all enemy armor. Deals 50 piercing damage. Bonus: +25% damage vs bosses.",
            drop.logMessage
        )
    }

    @Test
    fun `every boss type has pinned legendary loot`() {
        val expected = mapOf(
            BossType.FIREWALL_SENTINEL to "SentinelFirewallBreaker.exe",
            BossType.DAEMON_OVERLORD to "DaemonSlayer.sys",
            BossType.BLACK_ICE_COLOSSUS to "ColossusBlade.exe"
        )
        expected.forEach { (boss, item) ->
            val drop = CombatLootDropSystem.generateBossLootDrop(boss, enemyLevel = 1)
            assertEquals(item, drop.itemName)
            assertEquals(LootRarity.LEGENDARY, drop.rarity)
        }
    }

    @Test
    fun `regular drop draws from the pinned loot database with legacy formulas`() {
        val drop = CombatLootDropSystem.generateLootDrop("Trace Daemon", enemyLevel = 2, baseBounty = 60)

        val expected = LOOT_POOL.getValue(drop.itemName)
        assertEquals(expected.itemType, drop.itemType)
        assertEquals(expected.rarity, drop.rarity)
        assertEquals(expected.description, drop.description)
        assertEquals((60 * expected.creditBonusMultiplier * (1f + 2 * 0.15f)).toInt(), drop.totalCreditsEarned)
        // 45 + 2 * 25 + roll[5, 14]
        assertTrue("xp roll out of range: ${drop.xpEarned}", drop.xpEarned in 100..109)
        assertTrue(drop.logMessage.startsWith("🎁 LOOT DROPPED from Trace Daemon: "))
        assertTrue(drop.logMessage.contains(drop.rarity.colorTag))
    }

    companion object {
        private val LOOT_POOL = mapOf(
            "NanoMed.sys" to ExpectedLoot("UTILITY", LootRarity.COMMON, "Nanite medical patch restoring 35 HP integrity.", 1.0f),
            "RAMBoost.exe" to ExpectedLoot("UTILITY", LootRarity.COMMON, "Volatile script injecting 6 MB RAM into active buffer.", 1.0f),
            "Decryptor.pkg" to ExpectedLoot("UTILITY", LootRarity.COMMON, "Decryption package bypassing basic sub-grid locks.", 1.1f),
            "HardwarePatch.exe" to ExpectedLoot("UTILITY", LootRarity.UNCOMMON, "Reinforces firewall integrity and shields.", 1.25f),
            "SignalDampener.mod" to ExpectedLoot("UTILITY", LootRarity.UNCOMMON, "Reduces trace detection speed by 25%.", 1.3f),
            "Siphon.exe" to ExpectedLoot("PROGRAM", LootRarity.UNCOMMON, "Leeches 15 HP and restores 3 RAM on strike.", 1.35f),
            "Firewall.sys" to ExpectedLoot("PROGRAM", LootRarity.UNCOMMON, "Generates +20 temporary Firewall Shielding.", 1.3f),
            "GibsonForecast.sys" to ExpectedLoot("UTILITY", LootRarity.RARE, "Predicts next sub-grid atmospheric weather event.", 1.5f),
            "OverclockDaemon.dll" to ExpectedLoot("UTILITY", LootRarity.RARE, "Grants +5 bonus combat damage for 3 turns.", 1.6f),
            "Kinetic Booster" to ExpectedLoot("CYBERWARE", LootRarity.RARE, "Sub-dermal booster enhancing strike accuracy by 15%.", 1.7f),
            "Memory Expansion" to ExpectedLoot("CYBERWARE", LootRarity.RARE, "Hardware chip permanently increasing max RAM pool.", 1.8f),
            "ZeroDay.exploit" to ExpectedLoot("PROGRAM", LootRarity.EPIC, "Deals massive 45 piercing damage bypassing armor.", 2.2f),
            "Subdermal Armor Patch" to ExpectedLoot("CYBERWARE", LootRarity.EPIC, "High-grade cyberware granting permanent +10 Armor.", 2.4f),
            "BlackIceBreaker.exe" to ExpectedLoot("PROGRAM", LootRarity.EPIC, "Stuns hostile ICE entities and deals 35 damage.", 2.5f),
            "Neural Accelerator Module" to ExpectedLoot("CYBERWARE", LootRarity.LEGENDARY, "Experimental coprocessor boosting turn initiative and crit chance.", 3.5f),
            "Quantum Core Payload" to ExpectedLoot("UTILITY", LootRarity.LEGENDARY, "Overcharges all combat stats to maximum capacity.", 4.0f)
        )
    }
}
