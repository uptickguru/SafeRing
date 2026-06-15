package com.safering.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safering.android.data.local.entity.ScamNumberEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the scam_numbers table — offline cache of known scam number hashes.
 */
@Dao
interface ScamNumberDao {

    @Query("SELECT * FROM scam_numbers WHERE hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): ScamNumberEntity?

    @Query("SELECT * FROM scam_numbers ORDER BY risk_score DESC LIMIT :limit")
    suspend fun getHighestRisk(limit: Int = 100): List<ScamNumberEntity>

    @Query("SELECT * FROM scam_numbers ORDER BY risk_score DESC")
    fun getAllFlow(): Flow<List<ScamNumberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(numbers: List<ScamNumberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(number: ScamNumberEntity)

    @Query("DELETE FROM scam_numbers WHERE hash IN (SELECT hash FROM scam_numbers ORDER BY last_updated ASC LIMIT :limit)")
    suspend fun evictOldest(limit: Int): Int

    @Query("SELECT COUNT(*) FROM scam_numbers")
    suspend fun count(): Int

    @Query("DELETE FROM scam_numbers WHERE last_updated < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM scam_numbers WHERE hash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Query("DELETE FROM scam_numbers")
    suspend fun clearAll()
}
