package com.safering.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A known scam caller ID prefix (e.g., spoofed area codes).
 *
 * Examples of scam prefixes that are commonly spoofed:
 * - +1-202 (Washington DC — IRS impersonation)
 * - +1-888 (Toll-free — tech support scams)
 * - +1-909 (Social Security administration spoofing)
 *
 * These are stored as prefix strings (e.g., "1202", "1888") and matched
 * against the start of incoming caller numbers.
 */
@Entity(
    tableName = ScamPrefixEntity.TABLE_SCAM_PREFIXES,
    indices = [
        Index(value = ["prefix"], unique = true),
        Index(value = ["risk_weight"])
    ]
)
data class ScamPrefixEntity(
    @PrimaryKey
    @ColumnInfo(name = "prefix")
    val prefix: String,

    @ColumnInfo(name = "risk_weight")
    val riskWeight: Float,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "region")
    val region: String?,

    @ColumnInfo(name = "associated_types")
    val associatedTypes: String?,

    @ColumnInfo(name = "report_count")
    val reportCount: Int = 0,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_SCAM_PREFIXES = "scam_prefixes"
    }
}
