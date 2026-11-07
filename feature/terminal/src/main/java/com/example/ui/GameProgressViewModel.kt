package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.data.GameSaveProgressEntity
import com.example.data.InventoryItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(
        database.runRecordDao(),
        database.characterProfileDao(),
        database.gameSaveProgressDao(),
        database.inventoryItemDao()
    )

    val currentSaveProgress: StateFlow<GameSaveProgressEntity?> = repository.currentSaveProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun saveGameProgress(
        saveProgress: GameSaveProgressEntity,
        inventoryItems: List<InventoryItemEntity> = emptyList()
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.saveGameProgress(saveProgress, inventoryItems)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteSaveSlot(slotId: String = "current_save") {
        viewModelScope.launch {
            repository.deleteSaveProgress(slotId)
        }
    }
}
