package online.db1k.safering.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import online.db1k.safering.android.data.local.models.ScamNumberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScamNumberDao {
    @Query("SELECT * FROM scam_numbers ORDER BY updatedAt DESC")
    fun getAllScamNumbers(): Flow<List<ScamNumberEntity>>

    @Query("SELECT * FROM scam_numbers WHERE numberHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): ScamNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scamNumber: ScamNumberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(scamNumbers: List<ScamNumberEntity>)

    @Query("DELETE FROM scam_numbers WHERE numberHash = :hash")
    suspend fun deleteByHash(hash: String)

    @Query("DELETE FROM scam_numbers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM scam_numbers")
    suspend fun count(): Int

    @Query("SELECT * FROM scam_numbers WHERE shouldBlock = 1")
    fun getBlockedNumbers(): Flow<List<ScamNumberEntity>>

    @Query("SELECT * FROM scam_numbers WHERE shouldBlock = 1")
    suspend fun getBlockedNumbersOnce(): List<ScamNumberEntity>

    @Query("SELECT MAX(updatedAt) FROM scam_numbers")
    suspend fun getLastUpdateTime(): Long?
}
