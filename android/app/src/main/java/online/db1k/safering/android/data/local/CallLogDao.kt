package online.db1k.safering.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import online.db1k.safering.android.data.local.models.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallLogs(limit: Int = 500): Flow<List<CallLogEntity>>

    @Insert
    suspend fun insert(log: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}
