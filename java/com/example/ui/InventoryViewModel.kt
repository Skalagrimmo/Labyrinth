package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.data.InventoryItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(
        database.runRecordDao(),
        database.characterProfileDao(),
        database.gameSaveProgressDao(),
        database.inventoryItemDao()
    )

    val inventoryItems: StateFlow<List<InventoryItemEntity>> = repository.currentInventoryItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addInventoryItem(
        itemName: String,
        itemType: String = "UTILITY",
        quantity: Int = 1,
        description: String = "",
        saveSlotId: String = "current_save"
    ) {
        if (itemName.isBlank()) return

        val entity = InventoryItemEntity(
            saveSlotId = saveSlotId,
            itemName = itemName,
            itemType = itemType,
            quantity = quantity,
            description = description,
            acquiredTimestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.insertInventoryItem(entity)
        }
    }

    fun deleteItemByName(itemName: String, saveSlotId: String = "current_save") {
        viewModelScope.launch {
            database.inventoryItemDao().deleteItemByName(saveSlotId, itemName)
        }
    }

    fun clearInventory(saveSlotId: String = "current_save") {
        viewModelScope.launch {
            database.inventoryItemDao().clearInventoryForSlot(saveSlotId)
        }
    }
}
