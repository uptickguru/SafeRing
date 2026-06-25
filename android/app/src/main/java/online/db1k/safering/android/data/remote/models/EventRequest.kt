package online.db1k.safering.android.data.remote.models

/**
 * Lightweight device event sent to POST /v1/event.
 * Fire-and-forget — server logs it for operational visibility.
 */
data class EventRequest(
    val platform: String,      // "android"
    val action: String,        // "block" | "warn" | "monitor" | "check"
    val event_type: String,    // "call" | "sms"
    val hash_prefix: String = "",
    val risk_score: Double = 0.0,
    val scam_type: String = "",
    val source: String = ""    // "local_cache" | "api" | "ml"
)

data class EventResponse(
    val status: String
)
