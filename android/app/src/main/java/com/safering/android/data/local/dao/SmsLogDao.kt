package com.safering.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safering.android.data.local.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the sms_logs table — history of classified SMS messages.
 */
@Dao
interface SmsLogDao {

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE classification = 'SCAM' ORDER BY timestamp DESC LIMIT :limit")
    fun getScamMessages(limit: Int = 50): Flow<List<SmsLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(smsLog: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE classification = 'SCAM'")
    fun scamMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs")
    fun totalMessageCount(): Flow<Int>

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()
}
