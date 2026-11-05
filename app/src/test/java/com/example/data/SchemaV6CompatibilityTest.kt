package com.example.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Locks the Room schema at version 6 — the migration baseline of the rebuild
 * (see docs/LABYRINTH-REBUILD.md guardrails).
 *
 * The test reads the KSP-exported schema JSON (app/schemas), i.e. the same
 * artifact Room validates against room_master_table at runtime, and pins the
 * version, identity hash, table set, ordered column lists, primary keys and
 * index names. Any entity change fails here: shipping it requires an explicit
 * version bump + migration, or a deliberate, reviewed update of this lock.
 */
class SchemaV6CompatibilityTest {

    private fun database(): JSONObject =
        JSONObject(locateSchema().readText()).getJSONObject("database")

    private fun locateSchema(): File {
        val moduleRelative = "schemas/com.example.data.GameDatabase/6.json"
        val repoRelative = "app/schemas/com.example.data.GameDatabase/6.json"
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (true) {
            File(dir, moduleRelative).takeIf { it.isFile }?.let { return it }
            File(dir, repoRelative).takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile ?: error("Room schema export not found (expected $repoRelative)")
        }
    }

    private fun entities(): List<JSONObject> {
        val array = database().getJSONArray("entities")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun entity(table: String): JSONObject =
        entities().first { it.getString("tableName") == table }

    private fun columnsOf(table: String): List<String> {
        val fields = entity(table).getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
    }

    private fun primaryKeyOf(table: String): List<String> {
        val names = entity(table).getJSONObject("primaryKey").getJSONArray("columnNames")
        return (0 until names.length()).map { names.getString(it) }
    }

    private fun indicesOf(table: String): Set<String> {
        val e = entity(table)
        if (!e.has("indices")) return emptySet()
        val indices = e.getJSONArray("indices")
        return (0 until indices.length()).map { indices.getJSONObject(it).getString("name") }.toSet()
    }

    @Test
    fun `schema stays at version 6 with pinned identity hash`() {
        val db = database()
        assertEquals("Room schema must stay at the v6 migration baseline", 6, db.getInt("version"))
        assertEquals(
            "Entity change detected: ship an explicit migration + version bump, or review this lock",
            "bd09bc95302b5ab159a9df39bfdb2bcf",
            db.getString("identityHash")
        )
    }

    @Test
    fun `schema keeps exactly the ten legacy tables`() {
        assertEquals(
            "Table set drift",
            EXPECTED_COLUMNS.keys,
            entities().map { it.getString("tableName") }.toSet()
        )
    }

    @Test
    fun `table columns stay pinned`() {
        EXPECTED_COLUMNS.forEach { (table, columns) ->
            assertEquals("Column drift in $table", columns, columnsOf(table))
        }
    }

    @Test
    fun `primary keys stay pinned`() {
        EXPECTED_PRIMARY_KEYS.forEach { (table, columns) ->
            assertEquals("Primary key drift in $table", columns, primaryKeyOf(table))
        }
    }

    @Test
    fun `indices stay pinned`() {
        EXPECTED_INDICES.forEach { (table, names) ->
            assertEquals("Index drift in $table", names, indicesOf(table))
        }
    }

    companion object {
        private val EXPECTED_COLUMNS: Map<String, List<String>> = mapOf(
            "run_records" to listOf(
                "id", "runnerName", "runnerClass", "levelReached", "nodesHacked",
                "creditsEarned", "timestamp", "outcome"
            ),
            "character_profiles" to listOf(
                "profileId", "runnerName", "runnerClass", "level", "credits",
                "totalCreditsEarned", "maxIntegrity", "maxRam", "nodesHackedCount", "createdTimestamp"
            ),
            "game_save_progress" to listOf(
                "saveSlotId", "runnerName", "runnerClass", "level", "integrity", "maxIntegrity",
                "playerShield", "playerMaxShield", "ram", "maxRam", "ramRecoveryRate", "credits",
                "damageBonus", "defenseBonus", "characterLevel", "characterXp", "xpToNextLevel",
                "gridX", "gridY", "direction", "currentZone", "buildingFloor", "collectorsLevel",
                "cityDistrictIndex", "hasElevatorKeycard", "activeWeather", "weatherTurnsLeft",
                "stepsSinceLastEvent", "nextEventSteps", "predictedWeather", "nodesHackedCount",
                "totalCreditsEarned", "inventoryCsv", "installedCyberwareCsv", "installedProgramsCsv",
                "installedImplantsCsv", "exploredCellsCsv", "mazeData", "originalMazeData",
                "buildingFloorsData", "buildingExploredData", "collectorsLevelsData",
                "collectorsExploredData", "cityDistrictsData", "cityExploredData", "gameStateName",
                "logFeedSerialized", "lastSavedTimestamp"
            ),
            "inventory_items" to listOf(
                "id", "saveSlotId", "itemName", "itemType", "quantity", "description", "acquiredTimestamp"
            ),
            "floor_maps" to listOf(
                "mapId", "saveSlotId", "levelNumber", "floorIndex", "floorName", "sectorName",
                "districtTheme", "width", "height", "spawnFloor", "spawnX", "spawnY", "exitFloor",
                "exitX", "exitY", "playerX", "playerY", "playerFloor", "playerDirection", "gridData",
                "exploredData", "securityLevel", "isCleared", "isFullyExplored", "totalWalkableTiles",
                "exploredTilesCount", "totalObstacles", "clearedObstacles", "createdTimestamp", "lastUpdatedTimestamp"
            ),
            "floor_obstacles" to listOf(
                "id", "mapId", "saveSlotId", "levelNumber", "floorIndex", "gridX", "gridY",
                "obstacleType", "name", "description", "isPassable", "isDestructible", "isHacked",
                "isDisarmed", "durability", "maxDurability", "hackDifficulty", "interactionPrompt",
                "rewardCredits", "rewardItem", "createdTimestamp"
            ),
            "player_map_positions" to listOf(
                "saveSlotId", "levelNumber", "floorIndex", "gridX", "gridY", "facingDirection",
                "currentZone", "stepsTaken", "totalFloorsExplored", "totalObstaclesBypassed",
                "exploredTilesHistoryCsv", "lastMovedTimestamp"
            ),
            "grid_map_state" to listOf(
                "mapId", "saveSlotId", "levelNumber", "floorIndex", "gridWidth", "gridHeight",
                "minBoundX", "minBoundY", "maxBoundX", "maxBoundY", "defaultTileType", "theme",
                "spawnX", "spawnY", "exitX", "exitY", "totalTiles", "walkableTilesCount",
                "serializedGridTiles", "serializedExploredMask", "createdTimestamp", "updatedTimestamp"
            ),
            "grid_entity_coordinates" to listOf(
                "entityId", "mapId", "saveSlotId", "levelNumber", "floorIndex", "entityName",
                "entityCategory", "coordinateX", "coordinateY", "facingDirection", "isPassable",
                "isVisibleToPlayer", "isAliveOrActive", "health", "maxHealth", "speed",
                "visionRadius", "alertLevel", "metadataPayload", "lastMovedTimestamp"
            ),
            "players" to listOf(
                "playerId", "saveSlotId", "name", "runnerClass", "health", "maxHealth", "stamina",
                "maxStamina", "staminaRecoveryRate", "shield", "maxShield", "ram", "maxRam",
                "ramRecoveryRate", "mapId", "levelNumber", "floorIndex", "gridX", "gridY",
                "facingDirection", "currentZone", "level", "experience", "xpToNextLevel", "credits",
                "attackPower", "defense", "speed", "visionRadius", "isAlive", "stepsTaken",
                "turnsElapsed", "nodesHackedCount", "createdTimestamp", "lastUpdatedTimestamp"
            )
        )

        private val EXPECTED_PRIMARY_KEYS: Map<String, List<String>> = mapOf(
            "run_records" to listOf("id"),
            "character_profiles" to listOf("profileId"),
            "game_save_progress" to listOf("saveSlotId"),
            "inventory_items" to listOf("id"),
            "floor_maps" to listOf("mapId"),
            "floor_obstacles" to listOf("id"),
            "player_map_positions" to listOf("saveSlotId"),
            "grid_map_state" to listOf("mapId"),
            "grid_entity_coordinates" to listOf("entityId"),
            "players" to listOf("playerId")
        )

        private val EXPECTED_INDICES: Map<String, Set<String>> = mapOf(
            "floor_maps" to setOf(
                "index_floor_maps_saveSlotId_levelNumber_floorIndex",
                "index_floor_maps_saveSlotId_levelNumber"
            ),
            "floor_obstacles" to setOf(
                "index_floor_obstacles_mapId_gridX_gridY",
                "index_floor_obstacles_saveSlotId_levelNumber_floorIndex"
            ),
            "player_map_positions" to setOf("index_player_map_positions_saveSlotId"),
            "grid_map_state" to setOf("index_grid_map_state_saveSlotId_levelNumber_floorIndex"),
            "grid_entity_coordinates" to setOf(
                "index_grid_entity_coordinates_mapId_coordinateX_coordinateY",
                "index_grid_entity_coordinates_mapId_entityCategory",
                "index_grid_entity_coordinates_saveSlotId_entityId"
            ),
            "players" to setOf(
                "index_players_saveSlotId",
                "index_players_mapId",
                "index_players_gridX_gridY",
                "index_players_saveSlotId_levelNumber_floorIndex"
            )
        )
    }
}
