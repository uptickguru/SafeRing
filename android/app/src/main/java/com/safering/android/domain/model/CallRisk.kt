package com.safering.android.domain.model

/**
 * Domain model representing the scam risk assessment of a phone call.
 *
 * This is the unified risk representation used throughout the UI layer.
 * Raw phone numbers are never exposed — only hash-based lookups and risk scores.
 */
data class CallRisk(
    /** Risk score 0.0 (safe) to 1.0 (definitely scam) */
    val riskScore: Float,

    /** Type of scam if identified (e.g., "IRS Impersonation", "Tech Support") */
    val scamType: String? = null,

    /** Human-readable label for the scam */
    val scamLabel: String? = null,

    /** Number of user reports for this number */
    val reportCount: Int = 0,

    /** Whether the call was blocked */
    val wasBlocked: Boolean = false,

    /** Whether this result came from local cache */
    val fromCache: Boolean = true,

    /** Error message if lookup failed */
    val error: String? = null
) {
    val isHighRisk: Boolean get() = riskScore >= 0.7f
    val isMediumRisk: Boolean get() = riskScore in 0.4f until 0.7f
    val isLowRisk: Boolean get() = riskScore < 0.4f
    val isSafe: Boolean get() = riskScore == 0f && error == null

    val riskLabel: String
        get() = when {
            isHighRisk -> "High Risk"
            isMediumRisk -> "Medium Risk"
            isLowRisk -> "Low Risk"
            else -> "Safe"
        }

    val displayColor: Long
        get() = when {
            isHighRisk -> 0xFFD32F2F // Red
            isMediumRisk -> 0xFFF57C00 // Orange
            isLowRisk -> 0xFFFBC02D // Yellow
            else -> 0xFF388E3C // Green
        }
}
