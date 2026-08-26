package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity representing the Player's complete persistent state,
 * including health, stamina, energy pools, and 2D grid position coordinates
 * linked directly to the floor/grid database maps.
 */
@Entity(
    tableName = "players",
    indices = [
        Index(value = ["saveSlotId"], unique = true),
        Index(value = ["mapId"]),
        Index(value = ["gridX", "gridY"]),
        Index(value = ["saveSlotId", "levelNumber", "floorIndex"])
    ]
)
data class PlayerEntity(
    @PrimaryKey val playerId: String = "player_current_save",
    val saveSlotId: String = "current_save",
    val name: String = "V-Runner",
    val runnerClass: String = "NETRUNNER",

    // Vitals & Energy Pools
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stamina: Int = 100,
    val maxStamina: Int = 100,
    val staminaRecoveryRate: Int = 5,
    val shield: Int = 0,
    val maxShield: Int = 50,
    val ram: Int = 12,
    val maxRam: Int = 12,
    val ramRecoveryRate: Int = 2,

    // 2D Grid Coordinates & Spatial Anchors
    val mapId: String = "current_save_L1_F0",
    val levelNumber: Int = 1,
    val floorIndex: Int = 0,
    val gridX: Int = 1,
    val gridY: Int = 1,
    val facingDirection: String = "NORTH", // "NORTH", "SOUTH", "EAST", "WEST"
    val currentZone: String = "BUILDING",

    // Attributes & Combat Modifiers
    val level: Int = 1,
    val experience: Int = 0,
    val xpToNextLevel: Int = 100,
    val credits: Int = 150,
    val attackPower: Int = 15,
    val defense: Int = 5,
    val speed: Int = 1,
    val visionRadius: Int = 6,
    val isAlive: Boolean = true,

    // Movement & Gameplay Telemetry
    val stepsTaken: Int = 0,
    val turnsElapsed: Int = 0,
    val nodesHackedCount: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Composite relational query linking the Player with the associated Grid Map state.
 */
data class PlayerWithGridMap(
    @Embedded val player: PlayerEntity,
    @Relation(
        parentColumn = "mapId",
        entityColumn = "mapId"
    )
    val gridMap: GridMapStateEntity?
)

/**
 * Composite relational query linking the Player with the associated Floor Map & Obstacles.
 */
data class PlayerWithFloorMap(
    @Embedded val player: PlayerEntity,
    @Relation(
        parentColumn = "mapId",
        entityColumn = "mapId"
    )
    val floorMap: FloorMapEntity?
)

/**
 * Data Access Object for Player Entity queries and mutations.
 */
@Dao
interface PlayerDao {

    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    fun getPlayer(playerId: String = "player_current_save"): Flow<PlayerEntity?>

    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    suspend fun getPlayerSync(playerId: String = "player_current_save"): PlayerEntity?

    @Query("SELECT * FROM players WHERE saveSlotId = :saveSlotId LIMIT 1")
    fun getPlayerBySlot(saveSlotId: String = "current_save"): Flow<PlayerEntity?>

    @Query("SELECT * FROM players WHERE saveSlotId = :saveSlotId LIMIT 1")
    suspend fun getPlayerBySlotSync(saveSlotId: String = "current_save"): PlayerEntity?

    @Transaction
    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    fun getPlayerWithGridMap(playerId: String = "player_current_save"): Flow<PlayerWithGridMap?>

    @Transaction
    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    fun getPlayerWithFloorMap(playerId: String = "player_current_save"): Flow<PlayerWithFloorMap?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("UPDATE players SET gridX = :gridX, gridY = :gridY, facingDirection = :direction, mapId = :mapId, floorIndex = :floorIndex, levelNumber = :levelNumber, stepsTaken = stepsTaken + 1, turnsElapsed = turnsElapsed + 1, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun updatePosition(
        playerId: String = "player_current_save",
        gridX: Int,
        gridY: Int,
        direction: String,
        mapId: String,
        floorIndex: Int,
        levelNumber: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE players SET health = :health, stamina = :stamina, shield = :shield, ram = :ram, isAlive = :isAlive, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun updateVitals(
        playerId: String = "player_current_save",
        health: Int,
        stamina: Int,
        shield: Int,
        ram: Int,
        isAlive: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE players SET stamina = CASE WHEN stamina - :amount < 0 THEN 0 ELSE stamina - :amount END, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun consumeStamina(
        playerId: String = "player_current_save",
        amount: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE players SET stamina = CASE WHEN stamina + :amount > maxStamina THEN maxStamina ELSE stamina + :amount END, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun restoreStamina(
        playerId: String = "player_current_save",
        amount: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE players SET health = CASE WHEN health - :damage < 0 THEN 0 ELSE health - :damage END, isAlive = CASE WHEN health - :damage <= 0 THEN 0 ELSE 1 END, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun applyDamage(
        playerId: String = "player_current_save",
        damage: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE players SET health = CASE WHEN health + :healAmount > maxHealth THEN maxHealth ELSE health + :healAmount END, isAlive = 1, lastUpdatedTimestamp = :timestamp WHERE playerId = :playerId")
    suspend fun applyHeal(
        playerId: String = "player_current_save",
        healAmount: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deletePlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE playerId = :playerId")
    suspend fun deletePlayerById(playerId: String = "player_current_save")

    @Query("DELETE FROM players WHERE saveSlotId = :saveSlotId")
    suspend fun deletePlayerForSlot(saveSlotId: String = "current_save")
}
