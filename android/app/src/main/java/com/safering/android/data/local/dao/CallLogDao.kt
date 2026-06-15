package com.safering.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safering.android.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the call_logs table — history of incoming/outgoing calls with scam flags.
 */
@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE is_scam = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getScamCalls(limit: Int = 50): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE hash = :hash ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastCallFromHash(hash: String): CallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM call_logs WHERE is_scam = 1")
    fun scamCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs")
    fun totalCallCount(): Flow<Int>

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()
}
