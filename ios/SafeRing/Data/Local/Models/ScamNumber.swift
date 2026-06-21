import Foundation
import SwiftData

/// SwiftData model representing a phone number flagged as a known scam.
///
/// # Zero PII Policy
/// The `numberHash` field stores the **SHA-256 hash** of the full phone number,
/// NOT the raw number. The original phone number is never persisted locally
/// or transmitted over the network in plain text.
///
@Model
final class ScamNumber {

    // MARK: - Attributes

    /// SHA-256 hash of the full phone number (hex-encoded).
    /// This is the primary identifier used for lookups.
    @Attribute(.unique) var numberHash: String

    /// Risk score between 0.0 (legitimate) and 1.0 (confirmed scam).
    var riskScore: Double

    /// Human-readable label for the scam type (e.g., "IRS-Impression", "Tech Support").
    var scamLabel: String

    /// Confidence level of the risk assessment (0.0–1.0).
    var confidence: Double

    /// Timestamp when this record was first added to the local database.
    var createdAt: Date

    /// Timestamp when this record was last updated from the server.
    var updatedAt: Date

    /// The date when this scam was first reported.
    var firstReportedAt: Date?

    /// Number of user reports corroborating this scam number.
    var reportCount: Int

    /// Indicates whether this number should be auto-blocked.
    var shouldBlock: Bool

    // MARK: - Index

    init(
        numberHash: String,
        riskScore: Double,
        scamLabel: String,
        confidence: Double,
        firstReportedAt: Date? = nil,
        reportCount: Int = 0,
        shouldBlock: Bool = false
    ) {
        self.numberHash = numberHash
        self.riskScore = riskScore
        self.scamLabel = scamLabel
        self.confidence = confidence
        self.createdAt = Date()
        self.updatedAt = Date()
        self.firstReportedAt = firstReportedAt
        self.reportCount = reportCount
        self.shouldBlock = shouldBlock
    }
}

// MARK: - Risk Level

extension ScamNumber {
    /// Categorized risk level based on score.
    enum RiskLevel: String, CaseIterable {
        case safe = "Safe"
        case suspicious = "Suspicious"
        case highRisk = "High Risk"
        case scam = "Scam"

        var color: String {
            switch self {
            case .safe: return "safeGreen"
            case .suspicious: return "warningYellow"
            case .highRisk: return "highRiskOrange"
            case .scam: return "criticalRed"
            }
        }
    }

    var riskLevel: RiskLevel {
        switch riskScore {
        case ..<0.3: return .safe
        case ..<0.5: return .suspicious
        case ..<0.75: return .highRisk
        default: return .scam
        }
    }
}
