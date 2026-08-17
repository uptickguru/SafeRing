package online.db1k.safering.android.util

import java.util.regex.Pattern

/**
 * E.164 normalize + extract candidate phones from free text (SMS body).
 * Never invents numbers; only sanitizes what the OS or paste already provided.
 */
object PhoneNumberUtils {

    private val phoneInText: Pattern = Pattern.compile(
        "(?<!\\d)(?:\\+?1[\\s.-]?)?(?:\\(\\d{3}\\)|\\d{3})[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?!\\d)"
    )

    fun normalizeToE164(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.startsWith("1") && digits.length == 11) return "+$digits"
        if (digits.length == 10) return "+1$digits"
        if (raw.trim().startsWith("+") && digits.isNotEmpty()) return "+$digits"
        return if (digits.isEmpty()) raw.trim() else "+$digits"
    }

    fun isPlausibleE164(raw: String): Boolean {
        val n = normalizeToE164(raw)
        val d = n.filter { it.isDigit() }
        return d.length in 10..15
    }

    /** Pretty US-ish display; falls back to E.164. */
    fun pretty(raw: String): String {
        val n = normalizeToE164(raw)
        val d = n.filter { it.isDigit() }
        return if (d.length == 11 && d.startsWith("1")) {
            val a = d.substring(1, 4)
            val b = d.substring(4, 7)
            val c = d.substring(7)
            "($a) $b-$c"
        } else n
    }

    /**
     * Extract phone-like strings from SMS/email body.
     * Returns unique E.164 candidates.
     */
    fun extractPhones(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val found = linkedSetOf<String>()
        val m = phoneInText.matcher(text)
        while (m.find()) {
            val raw = m.group() ?: continue
            val e164 = normalizeToE164(raw)
            if (isPlausibleE164(e164)) found.add(e164)
        }
        return found.toList()
    }

    fun firstPhone(text: String): String? = extractPhones(text).firstOrNull()

    fun hashPrefix(e164: String, key: ByteArray = AppConfig.HMAC_KEY): String {
        val h = HmacHashUtils.hmacSHA256(normalizeToE164(e164), key)
        return h.take(12)
    }
}
