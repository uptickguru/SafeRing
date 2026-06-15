package online.db1k.safering.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing call history.
 * Mirrors the iOS CallLog SwiftData model.
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val numberHash: String,
    val callerName: String? = null,
    val riskScore: Double = 0.0,
    val riskLabel: String = "Safe",
    val scamType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val wasAnswered: Boolean = false,
    val wasBlocked: Boolean = false
)
