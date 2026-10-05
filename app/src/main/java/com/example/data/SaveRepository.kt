interface SaveRepository {
    suspend fun insert(record: RunRecord)
    suspend fun clearAll()
    suspend fun saveProfile(profile: CharacterProfileEntity)
    suspend fun saveGameProgress(
        saveProgress: GameSaveProgressEntity,
        inventoryItems: List<InventoryItemEntity>
    )
    suspend fun getSaveProgressSync(slotId: String = "current_save"): GameSaveProgressEntity?
}

class GameRepository(
    private val runRecordDao: RunRecordDao,
    private val characterProfileDao: CharacterProfileDao,
    private val gameSaveProgressDao: GameSaveProgressDao,
    private val inventoryItemDao: InventoryItemDao
) : SaveRepository {
    val allRunRecords: Flow<List<RunRecord>> = runRecordDao.getAllRecords()
    val allCharacterProfiles: Flow<List<CharacterProfileEntity>> = characterProfileDao.getAllProfiles()
    val currentSaveProgress: Flow<GameSaveProgressEntity?> = gameSaveProgressDao.getSaveProgress("current_save")
    val currentInventoryItems: Flow<List<InventoryItemEntity>> = inventoryItemDao.getInventoryItems("current_save")

    override suspend fun insert(record: RunRecord) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runRecordDao.insertRecord(record)
    }

    override suspend fun clearAll() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runRecordDao.clearRecords()
    }

    override suspend fun saveProfile(profile: CharacterProfileEntity) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        characterProfileDao.insertProfile(profile)
    }

    suspend fun deleteProfile(profileId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        characterProfileDao.deleteProfile(profileId)
    }

    override suspend fun saveGameProgress(
        saveProgress: GameSaveProgressEntity,
        inventoryItems: List<InventoryItemEntity>
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        gameSaveProgressDao.insertSaveProgress(saveProgress)
        inventoryItemDao.clearInventoryForSlot(saveProgress.saveSlotId)
        if (inventoryItems.isNotEmpty()) {
            inventoryItemDao.insertItems(inventoryItems)
        }
    }

    override suspend fun getSaveProgressSync(slotId: String): GameSaveProgressEntity? =
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

