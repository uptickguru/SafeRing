import Foundation

/// Remote API models for the SafeRing scam check response.
///
/// # Security
/// The server receives only HMAC-SHA256 hashes of phone numbers.
/// HMAC uses a per-install secret key provisioned at enrollment,
/// making the hash computationally infeasible to reverse.
/// No plaintext phone numbers are ever sent to the API.
///
/// Response from GET /v1/check?hash=<hmac-sha256>
/// Contains risk assessment for a hashed phone number.
struct CheckResponse: Decodable {
    let hash: String
    let risk: Double
    let label: String?
    let confidence: Double
    let tags: [String]
    let firstReportedAt: TimeInterval?
    let reportCount: Int
    let isConfirmed: Bool
    let suggestedAction: String?

    enum CodingKeys: String, CodingKey {
        case hash, risk, label, confidence, tags
        case firstReportedAt = "first_reported_at"
        case reportCount = "report_count"
        case isConfirmed = "is_confirmed"
        case suggestedAction = "suggested_action"
    }
}
