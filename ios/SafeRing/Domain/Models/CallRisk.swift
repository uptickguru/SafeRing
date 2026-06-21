import Foundation

/// Domain model representing the risk assessment of an incoming or outgoing call.
///
/// # Zero PII
/// This model carries a hashed phone number — never the original plaintext number.
///
struct CallRisk: Equatable {

    // MARK: - Properties

    /// SHA-256 hash of the phone number (hex-encoded).
    let hashedPhoneNumber: String

    /// Risk score from 0.0 (safe) to 1.0 (confirmed scam).
    let score: Double

    /// Confidence level of the assessment (0.0–1.0).
    let confidence: Double

    /// Human-readable scam type (e.g., "IRS-Impression", "Grandparent Scam").
    let scamType: String?

    /// Whether this number is confirmed as an active threat.
    let isConfirmed: Bool

    /// Whether the system suggests automatically blocking this number.
    let shouldBlock: Bool

    /// Source of the risk assessment.
    let source: RiskSource

    /// When this assessment was made.
    let assessedAt: Date

    // MARK: - Risk Level

    /// Categorized risk level.
    var level: RiskLevel {
        switch score {
        case ..<0.3:  return .safe
        case ..<0.5:  return .suspicious
        case ..<0.75: return .highRisk
        default:      return .scam
        }
    }

    /// System image name for the risk level icon.
    var iconName: String {
        switch level {
        case .safe:       return "checkmark.shield.fill"
        case .suspicious: return "questionmark.shield.fill"
        case .highRisk:   return "exclamationmark.shield.fill"
        case .scam:       return "xmark.shield.fill"
        }
    }

    /// Color name for the risk level.
    var colorName: String {
        switch level {
        case .safe:       return "safeGreen"
        case .suspicious: return "warningYellow"
        case .highRisk:   return "highRiskOrange"
        case .scam:       return "criticalRed"
        }
    }

    // MARK: - Types

    enum RiskLevel: String, CaseIterable {
        case safe = "Safe"
        case suspicious = "Suspicious"
        case highRisk = "High Risk"
        case scam = "Scam"
    }

    enum RiskSource: String {
        case localCache = "Local Cache"
        case remoteAPI = "Remote API"
        case staleCache = "Stale Cache"
        case mlModel = "ML Model"
    }
}
