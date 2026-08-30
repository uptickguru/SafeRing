package online.db1k.safering.android.util

/**
 * Application configuration constants.
 * Mirrors the iOS AppConfig.swift exactly.
 */
object AppConfig {
    const val DEFAULT_BASE_URL = "https://safering.deathbyathousand.com"
    const val API_VERSION = "v1"
    const val REQUEST_TIMEOUT_SECONDS = 15L
    const val MAX_RETRIES = 2

    // HMAC key for phone number hashing (provisioned at enrollment)
    // In production, this should be fetched from backend and stored in Keystore
    val HMAC_KEY: ByteArray = "safering-default-hmac-key-change-in-production".toByteArray()

    // Sync
    const val SYNC_INTERVAL_HOURS = 6L

    // Risk thresholds
    const val AUTO_BLOCK_THRESHOLD = 0.85
    const val WARNING_THRESHOLD = 0.3
    const val ALERT_THRESHOLD = 0.6

    // Cache
    const val CACHE_MAX_AGE_HOURS = 6L
    const val MAX_CALL_LOGS = 500
    const val MAX_SMS_LOGS = 500
    const val LOG_RETENTION_DAYS = 30L

    // Placeholder only so leftover repository call sites compile.
    // Free-tier screening does not hash numbers to a server.
    val HMAC_KEY: ByteArray = "safering-dev-hmac-not-for-production".toByteArray()
}
