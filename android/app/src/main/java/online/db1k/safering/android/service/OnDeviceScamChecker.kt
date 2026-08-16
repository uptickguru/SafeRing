package online.db1k.safering.android.service

import java.util.regex.Pattern

enum class ScamVerdict { LIKELY_SCAM, SUSPICIOUS, LOOKS_OKAY }

data class ScamCheckResult(
    val verdict: ScamVerdict,
    val score: Double,
    val reasons: List<String>,
    val urls: List<String>
) {
    val title: String
        get() = when (verdict) {
            ScamVerdict.LIKELY_SCAM -> "Treat this as a scam"
            ScamVerdict.SUSPICIOUS -> "This looks fishy"
            ScamVerdict.LOOKS_OKAY -> "No obvious scam markers"
        }
}

object OnDeviceScamChecker {

    private val phrases = listOf(
        "gift card" to "Asks for a gift card",
        "wire transfer" to "Asks for a wire",
        "western union" to "Western Union payment",
        "moneygram" to "MoneyGram payment",
        "bitcoin" to "Crypto payment",
        "usdt" to "Crypto payment",
        "itunes card" to "Gift card payment",
        "social security" to "Social Security impersonation",
        "your ssn" to "Asks for SSN",
        "irs" to "IRS impersonation",
        "warrant for your arrest" to "Fake warrant",
        "account suspended" to "Account-suspension phish",
        "verify your account" to "Account-verification phish",
        "unusual activity" to "Bank-impersonation phrasing",
        "final notice" to "Urgency + final notice",
        "act now" to "Urgency language",
        "grandson" to "Grandparent-scam language",
        "granddaughter" to "Grandparent-scam language",
        "i'm in jail" to "Emergency / jail script",
        "i am in jail" to "Emergency / jail script",
        "don't tell" to "Secrecy request",
        "do not tell" to "Secrecy request",
        "keep this secret" to "Secrecy request",
        "anydesk" to "Remote-access tool",
        "teamviewer" to "Remote-access tool",
        "microsoft support" to "Fake tech support",
        "usps" to "Postal / package lure",
        "fedex" to "Package lure",
        "package held" to "Package-hold phish",
        "delivery fee" to "Fake delivery fee",
        "unpaid postage" to "Fake postage fee",
        "click here" to "Click-through lure"
    )

    private val lookalikes = mapOf(
        "usps" to listOf("usps.com", "usps.gov"),
        "fedex" to listOf("fedex.com"),
        "ups" to listOf("ups.com"),
        "amazon" to listOf("amazon.com"),
        "apple" to listOf("apple.com", "icloud.com"),
        "paypal" to listOf("paypal.com"),
        "irs" to listOf("irs.gov"),
        "ssa" to listOf("ssa.gov"),
        "microsoft" to listOf("microsoft.com", "live.com", "office.com")
    )

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly"
    )

    private val urlPattern: Pattern = Pattern.compile(
        "(https?://[^\\s]+)|(www\\.[^\\s]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun check(raw: String): ScamCheckResult {
        val text = raw.trim()
        val lower = text.lowercase()
        val reasons = mutableListOf<String>()
        var score = 0.0
        var matched = 0

        for ((phrase, reason) in phrases) {
            if (lower.contains(phrase)) {
                matched++
                if (reason !in reasons) reasons.add(reason)
            }
        }
        score += (matched * 0.16).coerceAtMost(0.7)

        val urls = extractUrls(text)
        if (urls.isNotEmpty() && matched >= 1) {
            score += 0.15
            reasons.add("Contains a link plus scam language")
        }
        for (url in urls) {
            val host = hostOf(url)
            if (host in shorteners || shorteners.any { host.endsWith(".$it") }) {
                score += 0.2
                reasons.add("Uses a link shortener ($host)")
            }
            if (isLookalike(host)) {
                score += 0.35
                reasons.add("Link host looks like a brand impersonation ($host)")
            }
            if (!url.startsWith("https://", ignoreCase = true) && url.startsWith("http://", ignoreCase = true)) {
                score += 0.08
                reasons.add("Link is not HTTPS")
            }
        }
        score = score.coerceAtMost(1.0)

        val verdict = when {
            score >= 0.6 -> ScamVerdict.LIKELY_SCAM
            score >= 0.3 -> ScamVerdict.SUSPICIOUS
            else -> ScamVerdict.LOOKS_OKAY
        }
        if (reasons.isEmpty()) {
            reasons.add("No classic scam phrases or brand-lookalike links. Still verify anything about money.")
        }
        return ScamCheckResult(verdict, score, reasons, urls)
    }

    private fun extractUrls(text: String): List<String> {
        val out = mutableListOf<String>()
        val matcher = urlPattern.matcher(text)
        while (matcher.find()) {
            matcher.group()?.let { out.add(it) }
        }
        return out
    }

    private fun hostOf(url: String): String {
        val trimmed = url.removePrefix("https://").removePrefix("http://").substringBefore("/")
        return trimmed.lowercase()
    }

    private fun isLookalike(host: String): Boolean {
        for ((brand, legit) in lookalikes) {
            if (legit.any { host == it || host.endsWith(".$it") }) return false
            if (host.contains(brand)) return true
        }
        return false
    }
}
