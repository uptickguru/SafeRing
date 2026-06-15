package com.safering.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A known scam phone number, stored as SHA-256 hash.
 *
 * Zero PII guarantee: The raw phone number is NEVER stored.
 * Only its SHA-256 hash is persisted, both locally and on the server.
 */
@Entity(
    tableName = ScamNumberEntity.TABLE_SCAM_NUMBERS,
    indices = [
        Index(value = ["hash"], unique = true),
        Index(value = ["risk_score"]),
        Index(value = ["last_updated"])
    ]
)
data class ScamNumberEntity(
    @PrimaryKey
    @ColumnInfo(name = "hash")
    val hash: String,

    @ColumnInfo(name = "risk_score")
    val riskScore: Float,

    @ColumnInfo(name = "scam_type")
    val scamType: String?,

    @ColumnInfo(name = "scam_label")
    val scamLabel: String?,

    @ColumnInfo(name = "report_count")
    val reportCount: Int,

    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = false,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_SCAM_NUMBERS = "scam_numbers"
    }
}
