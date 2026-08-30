package online.db1k.safering.android.service

import online.db1k.safering.android.util.Logger

/**
 * SMS Classification Service — Android port of iOS SmsClassifierService.
 *
 * Analyzes incoming SMS messages for scam content using keyword-based classification.
 * Returns a risk score from 0.0 to 1.0 with classification labels.
 *
 * # Architecture
 * - Keyword-based classification (no ML model on Android yet)
 * - Pattern matching across 6 categories: urgency, financial, impersonation, prizes, general scam, URLs
 * - Composite scoring with category bonus
 *
 * # Privacy
 * - Message body processed entirely on-device
 * - Sender number is HMAC-hashed before any logging
 * - Only hash prefix sent to backend for event reporting
 */
class SmsClassificationService {

    companion object {
        // Thresholds
        const val AUTO_BLOCK_THRESHOLD = 0.85  // High confidence → auto-block
        const val WARN_THRESHOLD = 0.65        // Medium confidence → warn user
        const val MONITOR_THRESHOLD = 0.40     // Low confidence → log only

        // Urgency patterns
        private val URGENCY_PATTERNS = listOf(
            "act now", "action required", "immediately", "urgent",
            "respond now", "reply immediately", "don't delay", "do not delay",
            "time sensitive", "final notice", "last warning", "expire today",
            "expires today", "limited time", "within 24 hours", "within 12 hours",
            "your account will be", "failure to respond", "will be suspended",
            "will be closed", "will be locked", "verify now", "confirm now"
        )

        // Financial/payment terms
        private val FINANCIAL_TERMS = listOf(
            "gift card", "gift cards", "itunes card", "apple card", "google play card",
            "wire transfer", "western union", "moneygram", "money order",
            "bitcoin", "cryptocurrency", "crypto wallet", "btc",
            "paypal", "venmo", "cashapp", "zelle", "cash app",
            "bank account", "routing number", "account number", "ssn",
            "social security", "credit card", "debit card", "cvv",
            "wire money", "send money", "transfer funds"
        )

        // Impersonation patterns (organization → weight)
        private val IMPERSONATION_PATTERNS = mapOf(
            "irs" to 0.20,
            "internal revenue" to 0.20,
            "social security administration" to 0.20,
            "medicare" to 0.15,
            "fedex" to 0.12,
            "ups" to 0.10,
            "usps" to 0.12,
            "dhl" to 0.10,
            "amazon" to 0.12,
            "apple support" to 0.15,
            "apple id" to 0.15,
            "icloud" to 0.12,
            "microsoft" to 0.12,
            "netflix" to 0.10,
            "your bank" to 0.15,
            "chase bank" to 0.15,
            "wells fargo" to 0.15,
            "bank of america" to 0.15,
            "capital one" to 0.15
        )

        // General scam indicators
        private val SCAM_INDICATORS = listOf(
            "you won", "you've won", "you are the winner", "congratulations",
            "lottery", "prize", "sweepstakes", "jackpot",
            "inheritance", "beneficiary", "next of kin",
            "nigerian prince", "million dollars", "usd",
            "click here", "tap here", "click the link", "click below",
            "free money", "government grant", "stimulus",
            "work from home", "make money fast", "earn \$",
            "dating", "lonely", "soulmate", "romance",
            "virus detected", "malware detected", "your device is infected",
            "tech support", "call this number", "call us at"
        )

        // URL shorteners (high scam signal)
        private val URL_SHORTENERS = listOf(
            "bit.ly", "tinyurl", "t.co", "goo.gl", "is.gd", "ow.ly", "rebrand.ly"
        )

        // Suspicious TLDs
        private val SUSPICIOUS_TLDS = listOf(
            ".xyz", ".top", ".buzz", ".club", ".work", ".click", ".link", ".gq", ".tk", ".ml", ".cf"
        )
    }

    /**
     * Classification result
     */
    data class ClassificationResult(
        val riskScore: Double,
        val classification: Classification,
        val scamType: String?,
        val categoriesTriggered: Int
    )

    enum class Classification {
        SCAM,      // High confidence scam (≥0.85)
        SPAM,      // Medium confidence (0.65-0.85)
        SUSPICIOUS, // Low confidence (0.40-0.65)
        SAFE       // Below threshold (<0.40)
    }

    /**
     * Classify an incoming SMS message.
     *
     * @param messageBody The raw SMS text
     * @return ClassificationResult with risk score and label
     */
    fun classify(messageBody: String): ClassificationResult {
        val lowercased = messageBody.lowercase()

        var score = 0.0

        // 1. URL analysis
        score += scoreURLs(messageBody)

        // 2. Urgency patterns
        score += scoreUrgency(lowercased)

        // 3. Financial keywords
        score += scoreFinancial(lowercased)

        // 4. Impersonation patterns
        score += scoreImpersonation(lowercased)

        // 5. General scam indicators
        score += scoreGeneralScam(lowercased)

        // 6. Category bonus (multiple categories = higher confidence)
        val categoriesTriggered = countCategories(lowercased)
        if (categoriesTriggered >= 3) {
            score += 0.15
        } else if (categoriesTriggered >= 2) {
            score += 0.08
        }

        score = score.coerceAtMost(1.0)

        // Determine classification
        val classification = when {
            score >= AUTO_BLOCK_THRESHOLD -> Classification.SCAM
            score >= WARN_THRESHOLD -> Classification.SPAM
            score >= MONITOR_THRESHOLD -> Classification.SUSPICIOUS
            else -> Classification.SAFE
        }

        // Extract scam type
        val scamType = extractScamType(lowercased)

        Logger.info(
            "SMS classified: score=${"%.2f".format(score)} type=${classification} scamType=$scamType categories=$categoriesTriggered",
            Logger.Category.SMS
        )

        return ClassificationResult(
            riskScore = score,
            classification = classification,
            scamType = scamType,
            categoriesTriggered = categoriesTriggered
        )
    }

    // MARK: - Scoring Functions

    private fun scoreURLs(body: String): Double {
        var score = 0.0

        // Shorteners
        for (shortener in URL_SHORTENERS) {
            if (body.contains(shortener, ignoreCase = true)) {
                score += 0.20
                break
            }
        }

        // Suspicious TLDs
        for (tld in SUSPICIOUS_TLDS) {
            if (body.contains(tld, ignoreCase = true)) {
                score += 0.15
                break
            }
        }

        // Raw IP addresses
        val ipPattern = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")
        if (ipPattern.containsMatchIn(body)) {
            score += 0.20
        }

        // URL present
        val urlPattern = Regex("""https?://[^\s]+""")
        if (urlPattern.containsMatchIn(body)) {
            score += 0.05
        }

        return score.coerceAtMost(0.35)
    }

    private fun scoreUrgency(body: String): Double {
        val matches = URGENCY_PATTERNS.count { body.contains(it) }
        return (matches * 0.08).coerceAtMost(0.25)
    }

    private fun scoreFinancial(body: String): Double {
        val matches = FINANCIAL_TERMS.count { body.contains(it) }
        return (matches * 0.10).coerceAtMost(0.30)
    }

    private fun scoreImpersonation(body: String): Double {
        var maxScore = 0.0
        for ((pattern, weight) in IMPERSONATION_PATTERNS) {
            if (body.contains(pattern)) {
                maxScore = maxOf(maxScore, weight)
            }
        }
        return maxScore.coerceAtMost(0.30)
    }

    private fun scoreGeneralScam(body: String): Double {
        val matches = SCAM_INDICATORS.count { body.contains(it) }
        return (matches * 0.06).coerceAtMost(0.20)
    }

    private fun countCategories(body: String): Int {
        var count = 0

        // Urgency
        if (URGENCY_PATTERNS.any { body.contains(it) }) count++

        // Financial
        if (FINANCIAL_TERMS.any { body.contains(it) }) count++

        // Impersonation
        if (IMPERSONATION_PATTERNS.keys.any { body.contains(it) }) count++

        // Prizes
        val prizes = listOf("you won", "congratulations", "lottery", "prize", "winner")
        if (prizes.any { body.contains(it) }) count++

        // URL
        if (body.contains("http://") || body.contains("https://")) count++

        return count
    }

    private fun extractScamType(body: String): String? {
        return when {
            body.contains("irs") || body.contains("tax") -> "IRS Impersonation"
            body.contains("gift card") || body.contains("western union") || body.contains("wire transfer") -> "Payment Scam"
            body.contains("social security") || body.contains("ssn") -> "Identity Theft"
            body.contains("grandson") || body.contains("granddaughter") || body.contains("family emergency") -> "Grandparent Scam"
            body.contains("romance") || body.contains("dating") || body.contains("lonely") -> "Romance Scam"
            body.contains("tech support") || body.contains("virus") || body.contains("malware") -> "Tech Support Scam"
            body.contains("bitcoin") || body.contains("crypto") -> "Crypto Scam"
            body.contains("amazon") || body.contains("fedex") || body.contains("usps") || body.contains("ups") -> "Package Scam"
            body.contains("you won") || body.contains("lottery") || body.contains("prize") || body.contains("congratulations") -> "Prize Scam"
            body.contains("work from home") || body.contains("make money fast") -> "Job Scam"
            body.contains("government grant") || body.contains("stimulus") -> "Government Scam"
            else -> null
        }
    }
}
