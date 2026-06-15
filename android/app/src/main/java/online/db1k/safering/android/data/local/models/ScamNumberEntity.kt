package online.db1k.safering.android.data.local.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a phone number flagged as a known scam.
 * Mirrors the iOS ScamNumber SwiftData model.
 *
 * # Zero PII Policy
 * The numberHash field stores the SHA-256 hash, NOT the raw number.
 */
@Entity(
    tableName = "scam_numbers",
    indices = [Index(value = ["numberHash"], unique = true)]
)
data class ScamNumberEntity(
    @PrimaryKey
    val numberHash: String,
    val riskScore: Double,
    val scamLabel: String,
    val confidence: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val firstReportedAt: Long? = null,
    val reportCount: Int = 0,
    val shouldBlock: Boolean = false
) {
    val riskLevel: RiskLevel
        get() = when {
            riskScore < 0.3 -> RiskLevel.SAFE
            riskScore < 0.5 -> RiskLevel.SUSPICIOUS
            riskScore < 0.75 -> RiskLevel.HIGH_RISK
            else -> RiskLevel.SCAM
        }
}

enum class RiskLevel {
    SAFE,
    SUSPICIOUS,
    HIGH_RISK,
    SCAM
}
