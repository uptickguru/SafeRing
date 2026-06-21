import Foundation

/// Remote API models for the SafeRing scam check response.
///
/// # Zero PII
/// The server receives only SHA-256 hashes of phone numbers.
/// No plaintext phone numbers are ever sent to the API.
///

/// Response from GET /v1/check?hash=<sha256>
/// Contains risk assessment for a hashed phone number.
struct CheckResponse: Decodable {
    /// The SHA-256 hash that was queried (echoed back for verification).
    let hash: String

    /// Risk score between 0.0 (safe) and 1.0 (confirmed scam).
    let risk: Double

    /// Human-readable scam type label.
    let label: String?

    /// Confidence level of the assessment (0.0–1.0).
    let confidence: Double

    /// Array of scam type tags (e.g., ["IRS", "Phone-Scam"]).
    let tags: [String]

    /// Unix timestamp of when this number was first reported.
    let firstReportedAt: TimeInterval?

    /// Total number of corroborating reports.
    let reportCount: Int

    /// Whether the number is confirmed as an active scam.
    let isConfirmed: Bool

    /// Server-side suggested action.
    let suggestedAction: String?

    enum CodingKeys: String, CodingKey {
        case hash, risk, label, confidence, tags
        case firstReportedAt = "first_reported_at"
        case reportCount = "report_count"
        case isConfirmed = "is_confirmed"
        case suggestedAction = "suggested_action"
    }
}
