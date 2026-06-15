package online.db1k.safering.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing SMS history.
 * Mirrors the iOS SmsLog SwiftData model.
 */
@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val numberHash: String,
    val messageBody: String? = null,
    val riskScore: Double = 0.0,
    val riskLabel: String = "Safe",
    val scamType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val wasBlocked: Boolean = false
)
