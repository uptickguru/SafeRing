import Foundation

/// Device event sent to POST /v1/event for operational visibility.
/// Fire-and-forget — the server logs it and returns 200.
/// Zero PII: only an 8-char hash prefix, never a raw number.
struct DeviceEvent: Encodable {
    let platform: String       // "ios"
    let action: String         // "block" | "warn" | "monitor" | "check"
    let eventType: String      // "call" | "sms"
    let hashPrefix: String?    // first 8 hex chars of SHA-256 hash
    let riskScore: Double?     // 0.0 - 1.0
    let scamType: String?      // optional classification label
    let source: String?        // "local_cache" | "api" | "ml"

    enum CodingKeys: String, CodingKey {
        case platform
        case action
        case eventType = "event_type"
        case hashPrefix = "hash_prefix"
        case riskScore = "risk_score"
        case scamType = "scam_type"
        case source
    }
}

/// Empty response from the event endpoint.
struct EventResponse: Decodable {
    let status: String
}
