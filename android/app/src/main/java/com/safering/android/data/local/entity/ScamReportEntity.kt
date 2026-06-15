package com.safering.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-submitted scam report.
 *
 * Zero PII guarantee: Only the SHA-256 hash of the phone number is stored,
 * never the raw number. Reports are sent to the server as hash + tag only.
 */
@Entity(
    tableName = ScamReportEntity.TABLE_SCAM_REPORTS,
    indices = [
        Index(value = ["hash"]),
        Index(value = ["reported_at"])
    ]
)
data class ScamReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "hash")
    val hash: String,

    @ColumnInfo(name = "scam_tag")
    val scamTag: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "reported_at")
    val reportedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_to_server")
    val syncedToServer: Boolean = false
) {
    companion object {
        const val TABLE_SCAM_REPORTS = "scam_reports"
    }
}
