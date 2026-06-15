package com.safering.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record of an incoming or outgoing call.
 *
 * Privacy: The phone number is stored as SHA-256 hash.
 * The raw number is available ephemerally at call time for matching
 * against cached data, but only the hash is persisted.
 */
@Entity(
    tableName = CallLogEntity.TABLE_CALL_LOGS,
    indices = [
        Index(value = ["hash"]),
        Index(value = ["timestamp"]),
        Index(value = ["risk_score"])
    ]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "hash")
    val hash: String,

    @ColumnInfo(name = "raw_prefix")
    val rawPrefix: String?,

    @ColumnInfo(name = "direction")
    val direction: String, // INCOMING or OUTGOING

    @ColumnInfo(name = "risk_score")
    val riskScore: Float = 0f,

    @ColumnInfo(name = "scam_label")
    val scamLabel: String?,

    @ColumnInfo(name = "is_scam")
    val isScam: Boolean = false,

    @ColumnInfo(name = "was_blocked")
    val wasBlocked: Boolean = false,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_CALL_LOGS = "call_logs"
        const val DIRECTION_INCOMING = "INCOMING"
        const val DIRECTION_OUTGOING = "OUTGOING"
    }
}
