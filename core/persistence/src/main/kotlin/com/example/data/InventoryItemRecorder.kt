package com.example.data

/**
 * Persistence port for inventory item logging.
 * App adapter delegates to the Room-backed GameRepository; JVM feature modules stay persistence-agnostic.
 */
fun interface InventoryItemRecorder {
    suspend fun recordInventoryItem(
        saveSlotId: String,
        itemName: String,
        itemType: String,
        quantity: Int,
        description: String
    )
}
