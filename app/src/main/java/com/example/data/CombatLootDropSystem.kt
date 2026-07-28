package com.example.data

import kotlin.random.Random

/**
 * Item Rarity classification for turn-based combat loot drops.
 */
enum class LootRarity(val label: String, val colorTag: String, val dropWeight: Int) {
    COMMON("COMMON", "[COMMON]", 50),
    UNCOMMON("UNCOMMON", "[UNCOMMON]", 30),
    RARE("RARE", "[RARE]", 12),
    EPIC("EPIC", "[EPIC]", 6),
    LEGENDARY("LEGENDARY", "[★ LEGENDARY ★]", 2)
}

/**
 * Result container for generated loot drops.
 */
data class LootDropResult(
    val itemName: String,
    val itemType: String,
    val description: String,
    val rarity: LootRarity,
    val totalCreditsEarned: Int,
    val inventoryEntity: InventoryItemEntity,
    val logMessage: String
)

/**
 * Item definition in the master combat loot database.
 */
data class LootTableItem(
    val name: String,
    val itemType: String, // "UTILITY", "PROGRAM", "CYBERWARE"
    val rarity: LootRarity,
    val description: String,
    val creditBonusMultiplier: Float = 1.0f
)

/**
 * Combat Loot Drop System.
 * Generates randomized items and bounties from a weighted database pool upon defeating enemies.
 */
object CombatLootDropSystem {

    private val LOOT_DATABASE = listOf(
        // COMMON LOOT
        LootTableItem("NanoMed.sys", "UTILITY", LootRarity.COMMON, "Nanite medical patch restoring 35 HP integrity.", 1.0f),
        LootTableItem("RAMBoost.exe", "UTILITY", LootRarity.COMMON, "Volatile script injecting 6 MB RAM into active buffer.", 1.0f),
        LootTableItem("Decryptor.pkg", "UTILITY", LootRarity.COMMON, "Decryption package bypassing basic sub-grid locks.", 1.1f),

        // UNCOMMON LOOT
        LootTableItem("HardwarePatch.exe", "UTILITY", LootRarity.UNCOMMON, "Reinforces firewall integrity and shields.", 1.25f),
        LootTableItem("SignalDampener.mod", "UTILITY", LootRarity.UNCOMMON, "Reduces trace detection speed by 25%.", 1.3f),
        LootTableItem("Siphon.exe", "PROGRAM", LootRarity.UNCOMMON, "Leeches 15 HP and restores 3 RAM on strike.", 1.35f),
        LootTableItem("Firewall.sys", "PROGRAM", LootRarity.UNCOMMON, "Generates +20 temporary Firewall Shielding.", 1.3f),

        // RARE LOOT
        LootTableItem("GibsonForecast.sys", "UTILITY", LootRarity.RARE, "Predicts next sub-grid atmospheric weather event.", 1.5f),
        LootTableItem("OverclockDaemon.dll", "UTILITY", LootRarity.RARE, "Grants +5 bonus combat damage for 3 turns.", 1.6f),
        LootTableItem("Kinetic Booster", "CYBERWARE", LootRarity.RARE, "Sub-dermal booster enhancing strike accuracy by 15%.", 1.7f),
        LootTableItem("Memory Expansion", "CYBERWARE", LootRarity.RARE, "Hardware chip permanently increasing max RAM pool.", 1.8f),

        // EPIC LOOT
        LootTableItem("ZeroDay.exploit", "PROGRAM", LootRarity.EPIC, "Deals massive 45 piercing damage bypassing armor.", 2.2f),
        LootTableItem("Subdermal Armor Patch", "CYBERWARE", LootRarity.EPIC, "High-grade cyberware granting permanent +10 Armor.", 2.4f),
        LootTableItem("BlackIceBreaker.exe", "PROGRAM", LootRarity.EPIC, "Stuns hostile ICE entities and deals 35 damage.", 2.5f),

        // LEGENDARY LOOT
        LootTableItem("Neural Accelerator Module", "CYBERWARE", LootRarity.LEGENDARY, "Experimental coprocessor boosting turn initiative and crit chance.", 3.5f),
        LootTableItem("Quantum Core Payload", "UTILITY", LootRarity.LEGENDARY, "Overcharges all combat stats to maximum capacity.", 4.0f)
    )

    /**
     * Randomly generates loot from database when an enemy is defeated.
     *
     * @param enemyName Name of the defeated enemy process
     * @param enemyLevel Current level/layer depth
     * @param baseBounty Standard credit reward for enemy
     * @param saveSlotId Target save slot ID for Room database entity
     */
    fun generateLootDrop(
        enemyName: String,
        enemyLevel: Int = 1,
        baseBounty: Int = 50,
        saveSlotId: String = "current_save"
    ): LootDropResult {
        val totalWeight = LOOT_DATABASE.sumOf { it.rarity.dropWeight }
        var randomRoll = Random.nextInt(totalWeight)
        
        var selectedItem = LOOT_DATABASE.first()
        for (item in LOOT_DATABASE) {
            if (randomRoll < item.rarity.dropWeight) {
                selectedItem = item
                break
            }
            randomRoll -= item.rarity.dropWeight
        }

        val bonusCredits = (baseBounty * selectedItem.creditBonusMultiplier * (1f + enemyLevel * 0.15f)).toInt()

        val entity = InventoryItemEntity(
            saveSlotId = saveSlotId,
            itemName = selectedItem.name,
            itemType = selectedItem.itemType,
            quantity = 1,
            description = selectedItem.description,
            acquiredTimestamp = System.currentTimeMillis()
        )

        val logMsg = "🎁 LOOT DROPPED from $enemyName: ${selectedItem.rarity.colorTag} ${selectedItem.name} (${selectedItem.description})"

        return LootDropResult(
            itemName = selectedItem.name,
            itemType = selectedItem.itemType,
            description = selectedItem.description,
            rarity = selectedItem.rarity,
            totalCreditsEarned = bonusCredits,
            inventoryEntity = entity,
            logMessage = logMsg
        )
    }
}
