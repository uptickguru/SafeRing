package com.safering.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record of a received SMS message that was classified.
 *
 * Privacy: The sender number is SHA-256 hashed.
 * The message body is stored only for on-device classification review
 * and is NEVER transmitted. The body is retained only for a configurable
 * period (default 7 days) and then auto-deleted.
 */
@Entity(
    tableName = SmsLogEntity.TABLE_SMS_LOGS,
    indices = [
        Index(value = ["sender_hash"]),
        Index(value = ["timestamp"]),
        Index(value = ["classification"])
    ]
)
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "sender_hash")
    val senderHash: String,

    @ColumnInfo(name = "message_body")
    val messageBody: String,

    @ColumnInfo(name = "classification")
    val classification: String, // LEGITIMATE, SPAM, SCAM

    @ColumnInfo(name = "scam_type")
    val scamType: String?,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_SMS_LOGS = "sms_logs"
        const val CLASSIFICATION_LEGITIMATE = "LEGITIMATE"
        const val CLASSIFICATION_SPAM = "SPAM"
        const val CLASSIFICATION_SCAM = "SCAM"
    }
}
