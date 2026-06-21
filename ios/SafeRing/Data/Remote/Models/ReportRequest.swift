import Foundation

/// Request model for POST /v1/report
///
/// # Zero PII
/// Only the SHA-256 hash of the phone number is sent.
/// The server never receives the original phone number.
///
struct ReportRequest: Encodable {
    /// SHA-256 hash of the phone number being reported (hex-encoded).
    let hash: String

    /// Scam type tag (e.g., "IRS-Impression", "Tech-Support", "Grandparent").
    let tag: String

    /// Unix timestamp of when the scam call was received.
    let timestamp: TimeInterval

    /// Optional: device model for anonymous aggregate stats (never includes identifiers).
    let deviceModel: String?

    /// Optional: iOS version for anonymous aggregate stats.
    let osVersion: String?

    enum CodingKeys: String, CodingKey {
        case hash, tag, timestamp
        case deviceModel = "device_model"
        case osVersion = "os_version"
    }
}

/// Response from POST /v1/report
struct ReportResponse: Decodable {
    /// Whether the report was accepted.
    let success: Bool

    /// Confirmation message.
    let message: String

    /// Updated report count for this hash.
    let totalReports: Int?

    enum CodingKeys: String, CodingKey {
        case success, message
        case totalReports = "total_reports"
    }
}
