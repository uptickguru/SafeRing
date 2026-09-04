package online.db1k.safering.android.service

import android.content.Context

/**
 * Local keywords + block digits — Android parity with iOS FilterRulesStore (no system Junk folder).
 */
object FilterRulesStore {
    private const val PREFS = "safering_filter"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_BLOCK = "block_senders"
    private const val KEY_ALLOW = "allow_senders"

    private val DEFAULT_KEYWORDS = listOf(
        "gift card", "wire transfer", "irs", "social security", "verify account",
        "suspended", "click here", "bitcoin", "crypto", "urgent action",
        "bank fraud", "refund", "prize", "lottery", "remote access", "anydesk", "teamviewer"
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keywords(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_KEYWORDS, null)
        if (raw.isNullOrBlank()) return DEFAULT_KEYWORDS
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setKeywords(ctx: Context, list: List<String>) {
        prefs(ctx).edit().putString(KEY_KEYWORDS, list.joinToString("\n") { it.trim() }.trim()).apply()
    }

    fun addKeyword(ctx: Context, word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        val cur = keywords(ctx).map { it.lowercase() }.toMutableList()
        if (w !in cur) cur.add(w)
        setKeywords(ctx, cur)
    }

    fun removeKeyword(ctx: Context, word: String) {
        setKeywords(ctx, keywords(ctx).filter { !it.equals(word, ignoreCase = true) })
    }

    fun blockDigits(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_BLOCK, emptySet())?.toSet() ?: emptySet()

    fun addBlockDigits(ctx: Context, raw: String) {
        val d = raw.filter { it.isDigit() }
        if (d.length < 10) return
        val set = blockDigits(ctx).toMutableSet()
        set.add(d)
        prefs(ctx).edit().putStringSet(KEY_BLOCK, set).apply()
    }

    fun removeBlockDigits(ctx: Context, digits: String) {
        val set = blockDigits(ctx).toMutableSet()
        set.remove(digits.filter { it.isDigit() })
        prefs(ctx).edit().putStringSet(KEY_BLOCK, set).apply()
    }

    fun isBlocked(ctx: Context, raw: String): Boolean {
        val d = raw.filter { it.isDigit() }
        if (d.length < 10) return false
        val blocks = blockDigits(ctx)
        return blocks.any { d.endsWith(it) || it.endsWith(d.takeLast(10)) || d == it }
    }

    fun allowDigits(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_ALLOW, emptySet())?.toSet() ?: emptySet()

    fun addAllowDigits(ctx: Context, raw: String) {
        val d = raw.filter { it.isDigit() }
        if (d.length < 10) return
        val set = allowDigits(ctx).toMutableSet()
        set.add(d)
        prefs(ctx).edit().putStringSet(KEY_ALLOW, set).apply()
    }

    fun matchesKeyword(ctx: Context, text: String): String? {
        val lower = text.lowercase()
        return keywords(ctx).firstOrNull { lower.contains(it.lowercase()) }
    }
}
