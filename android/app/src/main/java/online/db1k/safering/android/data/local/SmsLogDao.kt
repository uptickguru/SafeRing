package online.db1k.safering.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import online.db1k.safering.android.data.local.models.SmsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSmsLogs(limit: Int = 500): Flow<List<SmsLogEntity>>

    @Insert
    suspend fun insert(log: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM sms_logs")
    suspend fun deleteAll()
}
