package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Query("""
        SELECT * FROM logs 
        WHERE sender LIKE '%' || :query || '%' OR messageBody LIKE '%' || :query || '%' OR matchedRuleName LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchLogs(query: String): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM logs")
    suspend fun clearLogs()

    @Delete
    suspend fun deleteLog(log: LogEntity)
}
