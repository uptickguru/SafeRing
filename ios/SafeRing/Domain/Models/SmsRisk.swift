import Foundation

/// Domain model representing the risk assessment of an SMS message.
///
/// # Zero PII
/// - The `hashedSenderNumber` is a SHA-256 hash — never the original number.
/// - The `messageBody` is the raw text of the SMS, processed **entirely on-device**
///   by the CoreML classifier. It is NEVER transmitted to the server.
/// - If the user opts in to message body storage, it persists locally only.
///
struct SmsRisk: Equatable {

    // MARK: - Properties

    /// SHA-256 hash of the sender's phone number.
    let hashedSenderNumber: String

    /// Raw SMS message body (on-device only; never transmitted).
    let messageBody: String

    /// Classification result from the on-device ML model.
    let classification: Classification

    /// Confidence score (0.0–1.0).
    let confidence: Double

    /// Specific scam type if applicable (e.g., "Phishing", "IRS", "Package Delivery").
    let scamType: String?

    /// Key trigger phrases detected in the message.
    let triggerPhrases: [String]

    /// When the message was received and classified.
    let assessedAt: Date

    /// Source of the classification.
    let source: RiskSource

    // MARK: - Classification

    enum Classification: String, CaseIterable {
        case legitimate = "Legitimate"
        case spam = "Spam"
        case scam = "Scam"
        case unknown = "Unknown"

        var iconName: String {
            switch self {
            case .legitimate: return "checkmark.message.fill"
            case .spam:       return "trash.fill"
            case .scam:       return "exclamationmark.triangle.fill"
            case .unknown:    return "questionmark"
            }
        }

        var colorName: String {
            switch self {
            case .legitimate: return "safeGreen"
            case .spam:       return "warningYellow"
            case .scam:       return "criticalRed"
            case .unknown:    return "secondaryText"
            }
        }
    }

    enum RiskSource: String {
        case mlModel = "ML Model"
        case keywordOnly = "Keyword Scan"
    }
}
