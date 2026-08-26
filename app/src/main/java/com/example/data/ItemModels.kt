package com.example.data

enum class InventoryCategory(val displayName: String, val icon: String) {
    CONSUMABLE("Consumable", "💊"),
    EQUIPMENT("Equipment", "⚔️"),
    PROGRAM("Program", "💾"),
    KEY_ITEM("Key Item", "🔑"),
    RESOURCE("Resource", "📦")
}

enum class ItemRarity(val displayName: String, val colorHex: Long) {
    COMMON("Common", 0xFF9CA3AF),
    UNCOMMON("Uncommon", 0xFF10B981),
    RARE("Rare", 0xFF3B82F6),
    EPIC("Epic", 0xFFA855F7),
    LEGENDARY("Legendary", 0xFFF59E0B)
}

enum class EquipmentSlot(val displayName: String, val icon: String) {
    WEAPON("Weapon", "🗡️"),
    ARMOR("Armor", "🛡️"),
    CYBERWARE("Cyberware", "🔌"),
    UTILITY("Utility Module", "📟")
}

data class GameItem(
    val id: String,
    val name: String,
    val description: String,
    val category: InventoryCategory,
    val rarity: ItemRarity = ItemRarity.COMMON,
    val icon: String = "📦",
    val isConsumable: Boolean = false,
    val isEquippable: Boolean = false,
    val equipmentSlot: EquipmentSlot? = null,
    val valueCredits: Int = 50,
    val stackable: Boolean = true,
    val maxStack: Int = 99,
    // Stat Modifiers when equipped
    val damageBonus: Int = 0,
    val defenseBonus: Int = 0,
    val ramBonus: Int = 0,
    val integrityBonus: Int = 0,
    // Consumable Effects
    val healIntegrity: Int = 0,
    val restoreRam: Int = 0,
    val grantCredits: Int = 0,
    val grantXp: Int = 0,
    val statusEffectToApply: StatusEffectType? = null,
    val statusEffectTurns: Int = 0,
    val targetSelf: Boolean = true
)

data class InventorySlot(
    val item: GameItem,
    val quantity: Int = 1,
    val isEquipped: Boolean = false
)

enum class InventorySortOption(val displayName: String) {
    NAME("Name"),
    CATEGORY("Category"),
    RARITY("Rarity"),
    QUANTITY("Quantity")
}
