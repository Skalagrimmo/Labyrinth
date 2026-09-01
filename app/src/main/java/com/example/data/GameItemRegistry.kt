package com.example.data

import kotlin.random.Random

object GameItemRegistry {

    private val items = listOf(
        // Consumables
        GameItem(
            id = "nano_med",
            name = "NanoMed.sys",
            description = "Medical micro-bots repairing digital core structure (+40 HP).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.COMMON,
            icon = "💊",
            isConsumable = true,
            valueCredits = 40,
            healIntegrity = 40
        ),
        GameItem(
            id = "ram_boost",
            name = "RAMBoost.exe",
            description = "Frees up volatile memory buffers for high-demand script execution (+6 RAM).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.COMMON,
            icon = "⚡",
            isConsumable = true,
            valueCredits = 45,
            restoreRam = 6
        ),
        GameItem(
            id = "decryptor_pkg",
            name = "Decryptor.pkg",
            description = "Cracks open encrypted bank credentials to harvest liquidity (+150 Credits).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.UNCOMMON,
            icon = "💳",
            isConsumable = true,
            valueCredits = 150,
            grantCredits = 150
        ),
        GameItem(
            id = "chipset_mod",
            name = "ChipsetMod.pkg",
            description = "Overclocks CPU instruction set (+1 Permanent Damage Bonus).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.RARE,
            icon = "🔮",
            isConsumable = true,
            valueCredits = 200,
            damageBonus = 1
        ),
        GameItem(
            id = "anti_shield",
            name = "AntiShield.bin",
            description = "Injects malicious virus subroutines into offensive routines (+2 Permanent Damage Bonus).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.RARE,
            icon = "🗡️",
            isConsumable = true,
            valueCredits = 250,
            damageBonus = 2
        ),
        GameItem(
            id = "firewall_buffer",
            name = "FirewallBuffer.pkg",
            description = "Reinforces passive incoming packet defense layer (+2 Permanent Defense Bonus).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.UNCOMMON,
            icon = "🛡️",
            isConsumable = true,
            valueCredits = 180,
            defenseBonus = 2
        ),
        GameItem(
            id = "gibson_forecast",
            name = "GibsonForecast.sys",
            description = "Accesses atmospheric satellite arrays to predict upcoming grid weather events.",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.UNCOMMON,
            icon = "🛰️",
            isConsumable = true,
            valueCredits = 100
        ),
        GameItem(
            id = "corrosive_acid",
            name = "CorrosiveAcid.sh",
            description = "Weaponized corrosive virus script (Corrodes target enemy for 3 turns, 10 DPS).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.RARE,
            icon = "🧪",
            isConsumable = true,
            valueCredits = 220,
            statusEffectToApply = StatusEffectType.POISONED,
            statusEffectTurns = 3,
            targetSelf = false
        ),
        GameItem(
            id = "stun_pulse",
            name = "StunPulse.dll",
            description = "Discharges an EMP burst freezing target enemy actions for 2 turns.",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.EPIC,
            icon = "⚡",
            isConsumable = true,
            valueCredits = 300,
            statusEffectToApply = StatusEffectType.STUNNED,
            statusEffectTurns = 2,
            targetSelf = false
        ),
        GameItem(
            id = "overclock_juice",
            name = "OverclockJuice.exe",
            description = "Pushes netrunner core past safety limits (+50% attack damage for 3 turns).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.RARE,
            icon = "🔥",
            isConsumable = true,
            valueCredits = 250,
            statusEffectToApply = StatusEffectType.BUFFED,
            statusEffectTurns = 3,
            targetSelf = true
        ),
        GameItem(
            id = "anti_virus",
            name = "AntiVirus.sys",
            description = "Purges all negative system status debuffs & fortifies firewalls (-50% dmg taken for 2 turns).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.EPIC,
            icon = "🛡️",
            isConsumable = true,
            valueCredits = 320,
            statusEffectToApply = StatusEffectType.FORTIFIED,
            statusEffectTurns = 2,
            targetSelf = true
        ),
        GameItem(
            id = "emergency_patch",
            name = "EmergencyPatch.exe",
            description = "High-grade patch compiling structural integrity and experience cache (+80 HP, +50 XP).",
            category = InventoryCategory.CONSUMABLE,
            rarity = ItemRarity.RARE,
            icon = "🩹",
            isConsumable = true,
            valueCredits = 280,
            healIntegrity = 80,
            grantXp = 50
        ),

        // Equipment: Weapons
        GameItem(
            id = "wpn_cyber_blade",
            name = "CyberBlade.exe",
            description = "High-frequency monomolecular light blade (+4 Damage Bonus).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.UNCOMMON,
            icon = "⚔️",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.WEAPON,
            valueCredits = 350,
            damageBonus = 4
        ),
        GameItem(
            id = "wpn_monofilament",
            name = "MonofilamentWhip.sys",
            description = "Razor-thin neural fiber whip slicing through firewall barriers (+7 Damage Bonus).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.RARE,
            icon = "🪢",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.WEAPON,
            valueCredits = 600,
            damageBonus = 7
        ),
        GameItem(
            id = "wpn_plasma_cutter",
            name = "PlasmaCutter.bin",
            description = "Heavy energy-discharge weapon delivering high thermal output (+12 Damage, +2 RAM).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.EPIC,
            icon = "🔫",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.WEAPON,
            valueCredits = 1100,
            damageBonus = 12,
            ramBonus = 2
        ),

        // Equipment: Armor
        GameItem(
            id = "arm_aegis_grid",
            name = "AegisBarrier.pkg",
            description = "Hardened packet-filtering mesh (+20 Max Integrity, +4 Defense).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.RARE,
            icon = "🛡️",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.ARMOR,
            valueCredits = 500,
            integrityBonus = 20,
            defenseBonus = 4
        ),
        GameItem(
            id = "arm_titanium_weave",
            name = "TitaniumSubdermal.sys",
            description = "Subdermal alloy weaving absorbing physical & digital shock (+35 Max Integrity, +6 Defense).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.EPIC,
            icon = "🥋",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.ARMOR,
            valueCredits = 950,
            integrityBonus = 35,
            defenseBonus = 6
        ),

        // Equipment: Cyberware / Utility
        GameItem(
            id = "util_neural_v2",
            name = "NeuralMatrixV2.sys",
            description = "Advanced co-processor array expanding operational memory (+4 Max RAM).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.RARE,
            icon = "🧠",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.CYBERWARE,
            valueCredits = 550,
            ramBonus = 4
        ),
        GameItem(
            id = "util_overclock_coproc",
            name = "OverclockCoprocess.dll",
            description = "Experimental chip boosting damage, memory, and defensive reaction time (+3 Dmg, +3 RAM, +2 Def).",
            category = InventoryCategory.EQUIPMENT,
            rarity = ItemRarity.LEGENDARY,
            icon = "⚙️",
            isEquippable = true,
            equipmentSlot = EquipmentSlot.UTILITY,
            valueCredits = 1500,
            damageBonus = 3,
            ramBonus = 3,
            defenseBonus = 2
        ),

        // Key Items
        GameItem(
            id = "key_elevator",
            name = "Elevator Keycard",
            description = "Encrypted access badge authorizing Express Elevator shaft movement.",
            category = InventoryCategory.KEY_ITEM,
            rarity = ItemRarity.RARE,
            icon = "🔑",
            isConsumable = false,
            valueCredits = 500,
            stackable = false
        ),

        // Resources & Salvage
        GameItem(
            id = "res_data_core",
            name = "EnrichedDataCore",
            description = "Encrypted memory crystal retrieved from mainframe vaults. High market value (+300 Credits).",
            category = InventoryCategory.RESOURCE,
            rarity = ItemRarity.RARE,
            icon = "💎",
            valueCredits = 300
        ),
        GameItem(
            id = "res_quantum_chip",
            name = "QuantumChiplet",
            description = "Ultra-dense quantum logic unit coveted by black-market cyberware tech dealers (+750 Credits).",
            category = InventoryCategory.RESOURCE,
            rarity = ItemRarity.LEGENDARY,
            icon = "🪐",
            valueCredits = 750
        )
    )

// Mod-defined items registered at runtime via ContentRegistry (merged into lookups
// and drops so a mod never needs to recompile Kotlin).
    private val modItems = mutableListOf<GameItem>()

    fun registerModItems(newItems: List<GameItem>) {
        modItems.addAll(newItems)
    }

    private fun allItems(): List<GameItem> = items + modItems

    fun getItemByName(name: String): GameItem {
        val key = name.lowercase().trim()
        allItems().forEach { if (it.name.lowercase() == key) return it }
        return GameItem(
            id = "custom_" + key.replace(" ", "_"),
            name = name,
            description = "Scavenged cyber asset stored in virtual memory.",
            category = if (name.endsWith(".sys") || name.endsWith(".exe") || name.endsWith(".pkg") || name.endsWith(".sh") || name.endsWith(".dll")) InventoryCategory.CONSUMABLE else InventoryCategory.RESOURCE,
            rarity = ItemRarity.COMMON,
            icon = "📦",
            isConsumable = true,
            healIntegrity = 25,
            valueCredits = 50
        )
    }

    fun getAllItems(): List<GameItem> = allItems()

    fun getRandomExplorationDrop(floor: Int): GameItem {
        val pool = allItems().filter { it.category != InventoryCategory.KEY_ITEM }
        val weights = pool.map { item ->
            when (item.rarity) {
                ItemRarity.COMMON -> 50
                ItemRarity.UNCOMMON -> 30
                ItemRarity.RARE -> 15
                ItemRarity.EPIC -> 4 + floor
                ItemRarity.LEGENDARY -> 1 + (floor / 2)
            }
        }
        val totalWeight = weights.sum()
        var roll = Random.nextInt(totalWeight)
        for (i in pool.indices) {
            roll -= weights[i]
            if (roll < 0) return pool[i]
        }
        return pool.first()
    }
}

// Crafting recipes: combine two items at a terminal to forge a new asset.
data class CraftRecipe(
    val id: String,
    val name: String,
    val description: String,
    val resultItemName: String,
    // Pairs of ingredient item name -> quantity required.
    val ingredients: List<Pair<String, Int>>
)

object CraftingRecipes {
    val RECIPES = listOf(
        CraftRecipe(
            id = "sys_patch",
            name = "SYS-PATCH SYNTHESIS",
            description = "Fuse two medical sticks into a high-grade emergency patch.",
            resultItemName = "EmergencyPatch.exe",
            ingredients = listOf("NanoMed.sys" to 2)
        ),
        CraftRecipe(
            id = "cache_refit",
            name = "CACHE REFIT",
            description = "Convert liquidated credentials and an acid vial into a full anti-virus purge.",
            resultItemName = "AntiVirus.sys",
            ingredients = listOf("Decryptor.pkg" to 1, "CorrosiveAcid.sh" to 1)
        ),
        CraftRecipe(
            id = "overclock",
            name = "OVERCLOCK MODULE",
            description = "Overclock a chipset with raw RAM to brew a combat hot-juice.",
            resultItemName = "OverclockJuice.exe",
            ingredients = listOf("ChipsetMod.pkg" to 1, "RAMBoost.exe" to 1)
        ),
        CraftRecipe(
            id = "hardening",
            name = "FIREWALL HARDENING",
            description = "Plate a firewall buffer with medical micro-bots into living armor.",
            resultItemName = "AegisBarrier.pkg",
            ingredients = listOf("FirewallBuffer.pkg" to 1, "NanoMed.sys" to 1)
        ),
        CraftRecipe(
            id = "warpack",
            name = "WARHEAD REFIT",
            description = "Re-prime a scavenged EMP grenade with nano-tech into a stun pulse.",
            resultItemName = "StunPulse.dll",
            ingredients = listOf("EMPGrenade.bin" to 1, "NanoMed.sys" to 1)
        )
    )

    fun find(index: Int): CraftRecipe? = RECIPES.getOrNull(index)
}
