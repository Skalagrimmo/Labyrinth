package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository providing clean, reactive Room database persistence and operations
 * for Player vitals (health, stamina, energy, shields) and 2D grid coordinates.
 */
class PlayerRepository(
    private val playerDao: PlayerDao
) {

    // Reactive Flows

    fun getPlayer(playerId: String = "player_current_save"): Flow<PlayerEntity?> =
        playerDao.getPlayer(playerId)

    fun getPlayerBySlot(saveSlotId: String = "current_save"): Flow<PlayerEntity?> =
        playerDao.getPlayerBySlot(saveSlotId)

    fun getPlayerWithGridMap(playerId: String = "player_current_save"): Flow<PlayerWithGridMap?> =
        playerDao.getPlayerWithGridMap(playerId)

    fun getPlayerWithFloorMap(playerId: String = "player_current_save"): Flow<PlayerWithFloorMap?> =
        playerDao.getPlayerWithFloorMap(playerId)

    // Suspend Database Operations

    suspend fun getPlayerSync(playerId: String = "player_current_save"): PlayerEntity? =
        withContext(Dispatchers.IO) {
            playerDao.getPlayerSync(playerId)
        }

    suspend fun getPlayerBySlotSync(saveSlotId: String = "current_save"): PlayerEntity? =
        withContext(Dispatchers.IO) {
            playerDao.getPlayerBySlotSync(saveSlotId)
        }

    suspend fun savePlayer(player: PlayerEntity) = withContext(Dispatchers.IO) {
        playerDao.insertPlayer(player)
    }

    suspend fun updatePlayerPosition(
        playerId: String = "player_current_save",
        gridX: Int,
        gridY: Int,
        direction: String,
        mapId: String,
        floorIndex: Int,
        levelNumber: Int
    ) = withContext(Dispatchers.IO) {
        playerDao.updatePosition(
            playerId = playerId,
            gridX = gridX,
            gridY = gridY,
            direction = direction,
            mapId = mapId,
            floorIndex = floorIndex,
            levelNumber = levelNumber
        )
    }

    suspend fun updateVitals(
        playerId: String = "player_current_save",
        health: Int,
        stamina: Int,
        shield: Int,
        ram: Int,
        isAlive: Boolean
    ) = withContext(Dispatchers.IO) {
        playerDao.updateVitals(
            playerId = playerId,
            health = health,
            stamina = stamina,
            shield = shield,
            ram = ram,
            isAlive = isAlive
        )
    }

    suspend fun consumeStamina(playerId: String = "player_current_save", amount: Int) = withContext(Dispatchers.IO) {
        playerDao.consumeStamina(playerId, amount)
    }

    suspend fun restoreStamina(playerId: String = "player_current_save", amount: Int) = withContext(Dispatchers.IO) {
        playerDao.restoreStamina(playerId, amount)
    }

    suspend fun applyDamage(playerId: String = "player_current_save", damage: Int) = withContext(Dispatchers.IO) {
        playerDao.applyDamage(playerId, damage)
    }

    suspend fun applyHeal(playerId: String = "player_current_save", healAmount: Int) = withContext(Dispatchers.IO) {
        playerDao.applyHeal(playerId, healAmount)
    }

    suspend fun deletePlayer(playerId: String = "player_current_save") = withContext(Dispatchers.IO) {
        playerDao.deletePlayerById(playerId)
    }

    suspend fun clearPlayerForSlot(saveSlotId: String = "current_save") = withContext(Dispatchers.IO) {
        playerDao.deletePlayerForSlot(saveSlotId)
    }
}
