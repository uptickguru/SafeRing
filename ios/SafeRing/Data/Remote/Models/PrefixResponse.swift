import Foundation

/// Response from GET /v1/prefixes
/// Contains known scammer phone number prefixes for offline prefix-based blocking.
struct PrefixResponse: Decodable {
    /// Array of known scam prefixes and their associated risk levels.
    let prefixes: [ScamPrefix]

    /// Unix timestamp indicating when this prefix data was compiled.
    let updatedAt: TimeInterval

    enum CodingKeys: String, CodingKey {
        case prefixes
        case updatedAt = "updated_at"
    }
}

/// A known scam phone number prefix pattern.
struct ScamPrefix: Decodable {
    /// The prefix string (e.g., "+1234", "123", "+1-800-")
    let prefix: String

    /// Risk score associated with numbers matching this prefix.
    let risk: Double

    /// Number of known scam numbers matching this prefix.
    let count: Int

    /// Common scam types associated with this prefix.
    let commonTags: [String]

    enum CodingKeys: String, CodingKey {
        case prefix, risk, count
        case commonTags = "common_tags"
    }
}
