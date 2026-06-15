package com.safering.android.domain.model

/**
 * Domain model representing the classification result of an SMS message.
 */
data class SmsRisk(
    /** Classification label: LEGITIMATE, SPAM, or SCAM */
    val label: String,

    /** Specific scam type when label is SCAM */
    val scamType: String? = null,

    /** Confidence score 0.0-1.0 */
    val confidence: Float,

    /** Keywords matched during keyword-based fallback */
    val matchedKeywords: List<String> = emptyList(),

    /** Error message if classification failed */
    val error: String? = null
) {
    val isScam: Boolean get() = label == "SCAM"
    val isSpam: Boolean get() = label == "SPAM"

    val displayLabel: String
        get() = when {
            isScam -> scamType ?: "Scam"
            isSpam -> "Spam"
            else -> "Safe"
        }

    val displayConfidence: String
        get() = "${(confidence * 100).toInt()}%"

    val displayColor: Long
        get() = when {
            isScam -> 0xFFD32F2F // Red
            isSpam -> 0xFFF57C00 // Orange
            else -> 0xFF388E3C // Green
        }
}
