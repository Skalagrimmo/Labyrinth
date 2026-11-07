package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CharacterProfileEntity
import com.example.data.GameDatabase
import com.example.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CharacterProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(
        database.runRecordDao(),
        database.characterProfileDao(),
        database.gameSaveProgressDao(),
        database.inventoryItemDao()
    )

    val profiles: StateFlow<List<CharacterProfileEntity>> = repository.allCharacterProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedProfile = MutableStateFlow<CharacterProfileEntity?>(null)
    val selectedProfile: StateFlow<CharacterProfileEntity?> = _selectedProfile.asStateFlow()

    fun selectProfile(profile: CharacterProfileEntity) {
        _selectedProfile.value = profile
    }

    fun createCharacterProfile(
        runnerName: String,
        runnerClass: String,
        maxIntegrity: Int = 100,
        maxRam: Int = 12,
        initialCredits: Int = 100
    ) {
        val cleanName = runnerName.trim()
        if (cleanName.isEmpty()) return

        val profileId = "profile_${cleanName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}"
        val profile = CharacterProfileEntity(
            profileId = profileId,
            runnerName = cleanName,
            runnerClass = runnerClass,
            level = 1,
            credits = initialCredits,
            totalCreditsEarned = initialCredits,
            maxIntegrity = maxIntegrity,
            maxRam = maxRam,
            nodesHackedCount = 0,
            createdTimestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.saveProfile(profile)
            _selectedProfile.value = profile
        }
    }

    fun updateProfile(profile: CharacterProfileEntity) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            if (_selectedProfile.value?.profileId == profile.profileId) {
                _selectedProfile.value = profile
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            if (_selectedProfile.value?.profileId == profileId) {
                _selectedProfile.value = null
            }
        }
    }
}
