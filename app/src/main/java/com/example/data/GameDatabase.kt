package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface RunRecordDao {
    @Query("SELECT * FROM run_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<RunRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RunRecord)

    @Query("DELETE FROM run_records")
    suspend fun clearRecords()
}

@Dao
interface CharacterProfileDao {
    @Query("SELECT * FROM character_profiles ORDER BY createdTimestamp DESC")
    fun getAllProfiles(): Flow<List<CharacterProfileEntity>>

    @Query("SELECT * FROM character_profiles WHERE profileId = :profileId LIMIT 1")
    fun getProfileById(profileId: String): Flow<CharacterProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: CharacterProfileEntity)

    @Update
    suspend fun updateProfile(profile: CharacterProfileEntity)

    @Delete
    suspend fun deleteProfileEntity(profile: CharacterProfileEntity)

    @Query("DELETE FROM character_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)
}

@Dao
interface GameSaveProgressDao {
    @Query("SELECT * FROM game_save_progress WHERE saveSlotId = :slotId LIMIT 1")
    fun getSaveProgress(slotId: String = "current_save"): Flow<GameSaveProgressEntity?>

    @Query("SELECT * FROM game_save_progress WHERE saveSlotId = :slotId LIMIT 1")
    suspend fun getSaveProgressSync(slotId: String = "current_save"): GameSaveProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaveProgress(saveProgress: GameSaveProgressEntity)

    @Update
    suspend fun updateSaveProgress(saveProgress: GameSaveProgressEntity)

    @Delete
    suspend fun deleteSaveProgressEntity(saveProgress: GameSaveProgressEntity)

    @Query("DELETE FROM game_save_progress WHERE saveSlotId = :slotId")
    suspend fun deleteSaveProgress(slotId: String = "current_save")
}

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items WHERE saveSlotId = :slotId ORDER BY acquiredTimestamp DESC")
    fun getInventoryItems(slotId: String = "current_save"): Flow<List<InventoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Delete
    suspend fun deleteItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE saveSlotId = :slotId")
    suspend fun clearInventoryForSlot(slotId: String = "current_save")

    @Query("DELETE FROM inventory_items WHERE saveSlotId = :slotId AND itemName = :itemName")
    suspend fun deleteItemByName(slotId: String = "current_save", itemName: String)
}

@Database(
    entities = [
        RunRecord::class,
        CharacterProfileEntity::class,
        GameSaveProgressEntity::class,
        InventoryItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun runRecordDao(): RunRecordDao
    abstract fun characterProfileDao(): CharacterProfileDao
    abstract fun gameSaveProgressDao(): GameSaveProgressDao
    abstract fun inventoryItemDao(): InventoryItemDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_profiles` (
                        `profileId` TEXT NOT NULL,
                        `runnerName` TEXT NOT NULL,
                        `runnerClass` TEXT NOT NULL,
                        `level` INTEGER NOT NULL,
                        `credits` INTEGER NOT NULL,
                        `totalCreditsEarned` INTEGER NOT NULL,
                        `maxIntegrity` INTEGER NOT NULL,
                        `maxRam` INTEGER NOT NULL,
                        `nodesHackedCount` INTEGER NOT NULL,
                        `createdTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `game_save_progress` (
                        `saveSlotId` TEXT NOT NULL,
                        `runnerName` TEXT NOT NULL,
                        `runnerClass` TEXT NOT NULL,
                        `level` INTEGER NOT NULL,
                        `integrity` INTEGER NOT NULL,
                        `maxIntegrity` INTEGER NOT NULL,
                        `ram` INTEGER NOT NULL,
                        `maxRam` INTEGER NOT NULL,
                        `credits` INTEGER NOT NULL,
                        `gridX` INTEGER NOT NULL,
                        `gridY` INTEGER NOT NULL,
                        `direction` TEXT NOT NULL,
                        `currentZone` TEXT NOT NULL,
                        `buildingFloor` INTEGER NOT NULL,
                        `collectorsLevel` INTEGER NOT NULL,
                        `cityDistrictIndex` INTEGER NOT NULL,
                        `hasElevatorKeycard` INTEGER NOT NULL,
                        `activeWeather` TEXT NOT NULL,
                        `nodesHackedCount` INTEGER NOT NULL,
                        `totalCreditsEarned` INTEGER NOT NULL,
                        `inventoryCsv` TEXT NOT NULL,
                        `installedCyberwareCsv` TEXT NOT NULL,
                        `installedProgramsCsv` TEXT NOT NULL,
                        `lastSavedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`saveSlotId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inventory_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `saveSlotId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `acquiredTimestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "netcrawler_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GameRepository(
    private val runRecordDao: RunRecordDao,
    private val characterProfileDao: CharacterProfileDao,
    private val gameSaveProgressDao: GameSaveProgressDao,
    private val inventoryItemDao: InventoryItemDao
) {
    val allRunRecords: Flow<List<RunRecord>> = runRecordDao.getAllRecords()
    val allCharacterProfiles: Flow<List<CharacterProfileEntity>> = characterProfileDao.getAllProfiles()
    val currentSaveProgress: Flow<GameSaveProgressEntity?> = gameSaveProgressDao.getSaveProgress("current_save")
    val currentInventoryItems: Flow<List<InventoryItemEntity>> = inventoryItemDao.getInventoryItems("current_save")

    suspend fun insert(record: RunRecord) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runRecordDao.insertRecord(record)
    }

    suspend fun clearAll() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runRecordDao.clearRecords()
    }

    suspend fun saveProfile(profile: CharacterProfileEntity) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        characterProfileDao.insertProfile(profile)
    }

    suspend fun deleteProfile(profileId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        characterProfileDao.deleteProfile(profileId)
    }

    suspend fun saveGameProgress(
        saveProgress: GameSaveProgressEntity,
        inventoryItems: List<InventoryItemEntity>
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        gameSaveProgressDao.insertSaveProgress(saveProgress)
        inventoryItemDao.clearInventoryForSlot(saveProgress.saveSlotId)
        if (inventoryItems.isNotEmpty()) {
            inventoryItemDao.insertItems(inventoryItems)
        }
    }

    suspend fun getSaveProgressSync(slotId: String = "current_save"): GameSaveProgressEntity? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            gameSaveProgressDao.getSaveProgressSync(slotId)
        }

    suspend fun deleteSaveProgress(slotId: String = "current_save") = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        gameSaveProgressDao.deleteSaveProgress(slotId)
        inventoryItemDao.clearInventoryForSlot(slotId)
    }

    suspend fun insertInventoryItem(item: InventoryItemEntity) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        inventoryItemDao.insertItem(item)
    }
}

