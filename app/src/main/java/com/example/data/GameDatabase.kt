package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

@Database(entities = [RunRecord::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun runRecordDao(): RunRecordDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "netcrawler_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GameRepository(private val runRecordDao: RunRecordDao) {
    val allRunRecords: Flow<List<RunRecord>> = runRecordDao.getAllRecords()

    suspend fun insert(record: RunRecord) {
        runRecordDao.insertRecord(record)
    }

    suspend fun clearAll() {
        runRecordDao.clearRecords()
    }
}
