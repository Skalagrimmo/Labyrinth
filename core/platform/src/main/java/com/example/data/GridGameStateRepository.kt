package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating Room Database queries and mutations for 2D Grid map dimensions
 * and real-time entity coordinates (Player, NPCs, Enemies, Hazards, Terminals).
 */
class GridGameStateRepository(
    private val gridMapStateDao: GridMapStateDao,
    private val gridEntityCoordinateDao: GridEntityCoordinateDao
) {

    // Reactive Flows

    fun getAllGridMaps(slotId: String = "current_save"): Flow<List<GridMapStateEntity>> =
        gridMapStateDao.getAllGridMaps(slotId)

    fun getGridMapById(mapId: String): Flow<GridMapStateEntity?> =
        gridMapStateDao.getGridMapById(mapId)

    fun getGridMap(level: Int, floor: Int, slotId: String = "current_save"): Flow<GridMapStateEntity?> =
        gridMapStateDao.getGridMap(slotId, level, floor)

    fun getGridMapWithEntities(mapId: String): Flow<GridMapWithEntities?> =
        gridMapStateDao.getGridMapWithEntities(mapId)

    fun getActiveEntitiesForMap(mapId: String): Flow<List<GridEntityCoordinateEntity>> =
        gridEntityCoordinateDao.getActiveEntitiesForMap(mapId)

    fun getPlayerEntity(slotId: String = "current_save"): Flow<GridEntityCoordinateEntity?> =
        gridEntityCoordinateDao.getPlayerEntity(slotId)

    fun getEntitiesAtCoordinate(mapId: String, x: Int, y: Int): Flow<List<GridEntityCoordinateEntity>> =
        gridEntityCoordinateDao.getEntitiesAtCoordinate(mapId, x, y)

    // Suspend / Database Operations

    suspend fun getGridMapSync(level: Int, floor: Int, slotId: String = "current_save"): GridMapStateEntity? =
        withContext(Dispatchers.IO) {
            gridMapStateDao.getGridMapSync(slotId, level, floor)
        }

    suspend fun getGridMapWithEntitiesSync(mapId: String): GridMapWithEntities? =
        withContext(Dispatchers.IO) {
            gridMapStateDao.getGridMapWithEntitiesSync(mapId)
        }

    suspend fun getPlayerEntitySync(slotId: String = "current_save"): GridEntityCoordinateEntity? =
        withContext(Dispatchers.IO) {
            gridEntityCoordinateDao.getPlayerEntitySync(slotId)
        }

    suspend fun saveGridMapState(gridMap: GridMapStateEntity) = withContext(Dispatchers.IO) {
        gridMapStateDao.insertGridMap(gridMap)
    }

    suspend fun saveGridMaps(gridMaps: List<GridMapStateEntity>) = withContext(Dispatchers.IO) {
        gridMapStateDao.insertGridMaps(gridMaps)
    }

    suspend fun updateGridDimensions(mapId: String, width: Int, height: Int) = withContext(Dispatchers.IO) {
        gridMapStateDao.updateGridDimensions(mapId, width, height)
    }

    suspend fun saveEntityCoordinate(entity: GridEntityCoordinateEntity) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.insertEntity(entity)
    }

    suspend fun saveEntitiesCoordinates(entities: List<GridEntityCoordinateEntity>) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.insertEntities(entities)
    }

    suspend fun moveEntity(
        entityId: String,
        newX: Int,
        newY: Int,
        direction: String
    ) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.updateEntityCoordinates(
            entityId = entityId,
            newX = newX,
            newY = newY,
            direction = direction
        )
    }

    suspend fun updateEntityHealth(
        entityId: String,
        health: Int,
        isAlive: Boolean
    ) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.updateEntityStatus(
            entityId = entityId,
            health = health,
            isAlive = isAlive
        )
    }

    suspend fun updateEntityAlertLevel(
        entityId: String,
        alertLevel: String
    ) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.updateEntityAlertLevel(entityId, alertLevel)
    }

    suspend fun removeEntity(entityId: String) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.deleteEntityById(entityId)
    }

    suspend fun clearMapState(mapId: String) = withContext(Dispatchers.IO) {
        gridEntityCoordinateDao.deleteEntitiesForMap(mapId)
    }

    suspend fun clearSlotGameState(slotId: String = "current_save") = withContext(Dispatchers.IO) {
        gridMapStateDao.deleteGridMapsForSlot(slotId)
        gridEntityCoordinateDao.deleteEntitiesForSlot(slotId)
    }
}
