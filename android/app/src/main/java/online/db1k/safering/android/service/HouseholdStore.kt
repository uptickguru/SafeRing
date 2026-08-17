package online.db1k.safering.android.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class SignalChannel(val key: String, val title: String, val subtitle: String) {
    SMS("sms", "Text / RCS", "Opens Messages with the alert already typed. Best default."),
    WHATSAPP("whatsapp", "WhatsApp", "Opens a chat with the alert typed if WhatsApp is installed."),
    SIGNAL("signal", "Signal", "Opens a Signal chat. Signal has no send API — you type the rest."),
    PHONE("phone", "Phone call", "Dials the saved number. Not a text alert.");

    companion object {
        fun fromKey(key: String?): SignalChannel =
            entries.firstOrNull { it.key == key } ?: SMS
    }
}

class HouseholdStore private constructor(private val prefs: SharedPreferences) {

    var ownerDisplayName: String
        get() = prefs.getString(KEY_OWNER, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_OWNER, value).apply() }

    var trustedContactName: String
        get() = prefs.getString(KEY_TRUSTED_NAME, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_TRUSTED_NAME, value).apply() }

    var trustedContactNumber: String
        get() = prefs.getString(KEY_TRUSTED_NUMBER, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_TRUSTED_NUMBER, value).apply() }

    var preferredChannel: SignalChannel
        get() = SignalChannel.fromKey(prefs.getString(KEY_CHANNEL, null))
        set(value) { prefs.edit().putString(KEY_CHANNEL, value.key).apply() }

    var familyPassword: String
        get() = prefs.getString(KEY_PASSWORD, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_PASSWORD, value).apply() }

    var silenceUnknownConfirmed: Boolean
        get() = prefs.getBoolean(KEY_SILENCE, false)
        set(value) { prefs.edit().putBoolean(KEY_SILENCE, value).apply() }

    var carrierProtectionConfirmed: Boolean
        get() = prefs.getBoolean(KEY_CARRIER, false)
        set(value) { prefs.edit().putBoolean(KEY_CARRIER, value).apply() }

    var callScreeningConfirmed: Boolean
        get() = prefs.getBoolean(KEY_SCREENING, false)
        set(value) { prefs.edit().putBoolean(KEY_SCREENING, value).apply() }

    /** User opted into notification-based SMS number capture (NLS). */
    var smsNotificationCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_NLS, false)
        set(value) { prefs.edit().putBoolean(KEY_SMS_NLS, value).apply() }

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDED, value).apply() }

    var helpCount: Int
        get() = prefs.getInt(KEY_HELP_COUNT, 0)
        set(value) { prefs.edit().putInt(KEY_HELP_COUNT, value).apply() }

    var lastHelpAt: Long
        get() = prefs.getLong(KEY_LAST_HELP, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_HELP, value).apply() }

    var lastUnknownCallAt: Long
        get() = prefs.getLong(KEY_LAST_UNKNOWN, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_UNKNOWN, value).apply() }

    val hasFamilyPassword: Boolean
        get() = familyPassword.isNotBlank()

    val isConfigured: Boolean
        get() = normalizeToE164(trustedContactNumber).filter { it.isDigit() }.length >= 10 &&
            hasFamilyPassword &&
            ownerDisplayName.trim().length >= 2

    val osChecklistComplete: Boolean
        get() = silenceUnknownConfirmed && carrierProtectionConfirmed && callScreeningConfirmed

    val e164Number: String
        get() = normalizeToE164(trustedContactNumber)

    val displayNumber: String
        get() {
            val digits = trustedContactNumber.filter { it.isDigit() }
            return when {
                digits.length == 11 && digits.startsWith("1") ->
                    "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
                digits.length == 10 ->
                    "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
                else -> trustedContactNumber
            }
        }

    fun recordHelpSent() {
        helpCount += 1
        lastHelpAt = System.currentTimeMillis()
    }

    fun recordUnknownCall() {
        lastUnknownCallAt = System.currentTimeMillis()
    }

    fun consumeUnknownCallCheckIn(): Boolean {
        val at = lastUnknownCallAt
        if (at == 0L) return false
        val recent = System.currentTimeMillis() - at < 3 * 60 * 1000
        lastUnknownCallAt = 0L
        return recent
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "household.encrypted"
        private const val KEY_OWNER = "owner"
        private const val KEY_TRUSTED_NAME = "trusted_name"
        private const val KEY_TRUSTED_NUMBER = "trusted_number"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_PASSWORD = "family_password"
        private const val KEY_SILENCE = "silence"
        private const val KEY_CARRIER = "carrier"
        private const val KEY_SCREENING = "screening"
        private const val KEY_SMS_NLS = "sms_nls"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_HELP_COUNT = "help_count"
        private const val KEY_LAST_HELP = "last_help"
        private const val KEY_LAST_UNKNOWN = "last_unknown"

        @Volatile private var instance: HouseholdStore? = null

        fun get(context: Context): HouseholdStore {
            return instance ?: synchronized(this) {
                instance ?: HouseholdStore(createPrefs(context.applicationContext)).also { instance = it }
            }
        }

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                context.getSharedPreferences("household.fallback", Context.MODE_PRIVATE)
            }
        }

        fun normalizeToE164(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return when {
                digits.startsWith("1") && digits.length == 11 -> "+$digits"
                digits.length == 10 -> "+1$digits"
                raw.startsWith("+") -> "+$digits"
                digits.isEmpty() -> raw
                else -> "+$digits"
            }
        }
    }
}
