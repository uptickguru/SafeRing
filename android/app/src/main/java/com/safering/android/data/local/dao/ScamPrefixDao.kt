package com.safering.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.safering.android.data.local.entity.ScamPrefixEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the scam_prefixes table — known scam caller ID prefixes.
 */
@Dao
interface ScamPrefixDao {

    @Query("SELECT * FROM scam_prefixes WHERE :numberPrefix LIKE prefix || '%' ORDER BY LENGTH(prefix) DESC LIMIT 1")
    suspend fun findMatchingPrefix(numberPrefix: String): ScamPrefixEntity?

    @Query("SELECT * FROM scam_prefixes ORDER BY risk_weight DESC")
    fun getAllFlow(): Flow<List<ScamPrefixEntity>>

    @Query("SELECT * FROM scam_prefixes ORDER BY risk_weight DESC")
    suspend fun getAll(): List<ScamPrefixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prefixes: List<ScamPrefixEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prefix: ScamPrefixEntity)

    @Query("DELETE FROM scam_prefixes")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM scam_prefixes")
    suspend fun count(): Int

    @Query("DELETE FROM scam_prefixes WHERE last_updated < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
}
