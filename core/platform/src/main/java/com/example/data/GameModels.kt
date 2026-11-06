package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_records")
data class RunRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val runnerName: String,
    val runnerClass: String,
    val levelReached: Int,
    val nodesHacked: Int,
    val creditsEarned: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: String // "DECEASED" or "DISCONNECTED" (won/alive)
)

@Entity(tableName = "character_profiles")
data class CharacterProfileEntity(
    @PrimaryKey val profileId: String = "primary_profile",
    val runnerName: String,
    val runnerClass: String,
    val level: Int = 1,
    val credits: Int = 0,
    val totalCreditsEarned: Int = 0,
    val maxIntegrity: Int = 100,
    val maxRam: Int = 12,
    val nodesHackedCount: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_save_progress")
data class GameSaveProgressEntity(
    @PrimaryKey val saveSlotId: String = "current_save",
    val runnerName: String,
    val runnerClass: String,
    val level: Int,
    val integrity: Int,
    val maxIntegrity: Int,
    val playerShield: Int = 0,
    val playerMaxShield: Int = 50,
    val ram: Int,
    val maxRam: Int,
    val ramRecoveryRate: Int = 2,
    val credits: Int,
    val damageBonus: Int = 0,
    val defenseBonus: Int = 0,
    val characterLevel: Int = 1,
    val characterXp: Int = 0,
    val xpToNextLevel: Int = 100,
    val gridX: Int,
    val gridY: Int,
    val direction: String,
    val currentZone: String,
    val buildingFloor: Int,
    val collectorsLevel: Int,
    val cityDistrictIndex: Int,
    val hasElevatorKeycard: Boolean,
    val activeWeather: String,
    val weatherTurnsLeft: Int = 0,
    val stepsSinceLastEvent: Int = 0,
    val nextEventSteps: Int = 30,
    val predictedWeather: String = "",
    val nodesHackedCount: Int,
    val totalCreditsEarned: Int,
    val inventoryCsv: String,
    val installedCyberwareCsv: String,
    val installedProgramsCsv: String,
    val installedImplantsCsv: String = "",
    val exploredCellsCsv: String = "",
    val mazeData: String = "",
    val originalMazeData: String = "",
    val buildingFloorsData: String = "",
    val buildingExploredData: String = "",
    val collectorsLevelsData: String = "",
    val collectorsExploredData: String = "",
    val cityDistrictsData: String = "",
    val cityExploredData: String = "",
    val gameStateName: String = "EXPLORATION",
    val logFeedSerialized: String = "",
    val lastSavedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saveSlotId: String = "current_save",
    val itemName: String,
    val itemType: String = "UTILITY",
    val quantity: Int = 1,
    val description: String = "",
    val acquiredTimestamp: Long = System.currentTimeMillis()
)
