package online.db1k.safering.android.util

/**
 * Application configuration — parity with iOS AppConfig (live edge).
 */
object AppConfig {
    /** Live Caruso edge — never deathbyathousand. */
    const val DEFAULT_BASE_URL = "https://safering.gulfmeridiangroup.com"
    const val LEGAL_PRIVACY = "https://safering.gulfmeridiangroup.com/legal/privacy.html"
    const val LEGAL_TERMS = "https://safering.gulfmeridiangroup.com/legal/terms.html"
    const val LEGAL_SUPPORT = "https://safering.gulfmeridiangroup.com/legal/support.html"
    const val API_VERSION = "v1"
    const val REQUEST_TIMEOUT_SECONDS = 15L
    const val MAX_RETRIES = 2

    val HMAC_KEY: ByteArray = "safering-default-hmac-key-change-in-production".toByteArray()

    const val SYNC_INTERVAL_HOURS = 6L
    const val AUTO_BLOCK_THRESHOLD = 0.85
    const val WARNING_THRESHOLD = 0.3
    const val ALERT_THRESHOLD = 0.6
    const val CACHE_MAX_AGE_HOURS = 6L
    const val MAX_CALL_LOGS = 500
    const val MAX_SMS_LOGS = 500
    const val LOG_RETENTION_DAYS = 30L

    const val DEVICE_COMMS_PATH = "/v1/device-comms"
    const val UNWANTED_REPORT_PATH = "/v1/unwanted-report"
    const val EXCEPTIONAL_PATH = "/v1/exceptional/capture"
    const val MESSAGE_FILTER_PATH = "/v1/message-filter"
    const val SAFECALL_STATUS_PATH = "/v1/safecall/status"
}
