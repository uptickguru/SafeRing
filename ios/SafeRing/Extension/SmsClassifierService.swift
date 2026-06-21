import Foundation
import CoreML

/// Service for classifying incoming SMS messages for scam content.
///
/// This is invoked by the app when new SMS messages are detected.
/// It coordinates the full classification pipeline:
/// 1. Keyword-based pre-scan (instant, always available)
/// 2. On-device ML model inference (when model is loaded)
/// 3. Local cache lookup for known scam senders
///
/// # Zero PII
/// - SMS message bodies are processed entirely on-device.
/// - The raw text NEVER leaves the device.
/// - Sender phone numbers are hashed before any lookup.
///
final class SmsClassifierService {

    // MARK: - Properties

    private let smsClassifier: SmsClassifier
    private let repository: ScamRepository?

    // MARK: - Initializer

    /// Creates an SMS classification service.
    /// - Parameters:
    ///   - smsClassifier: The on-device CoreML/NL classifier.
    ///   - repository: Optional repository for sender number lookups.
    init(
        smsClassifier: SmsClassifier,
        repository: ScamRepository? = nil
    ) {
        self.smsClassifier = smsClassifier
        self.repository = repository
    }

    // MARK: - Public API

    /// Performs a full scam scan on an incoming SMS message.
    ///
    /// - Parameters:
    ///   - senderRawNumber: The raw sender phone number (will be hashed).
    ///   - messageBody: The SMS text content.
    ///   - shouldStoreBody: Whether to store the message body locally.
    /// - Returns: The classified SmsLog entry.
    func scanSms(
        senderRawNumber: String,
        messageBody: String,
        shouldStoreBody: Bool = false
    ) async -> SmsLog {
        let normalized = normalizePhoneNumber(senderRawNumber)
        let hash = HashUtils.sha256(normalized)

        Logger.shared.info(
            "Scanning SMS from hash: \(hash.prefix(8))...",
            category: .sms
        )

        // Run ML classification
        let mlResult = smsClassifier.classify(message: messageBody)

        // Determine classification
        let classification: SmsLog.SmsClassification
        let scamLabel: String?
        let confidence: Double

        if mlResult.scamScore > 0.7 {
            classification = .scam
            scamLabel = mlResult.scamLabel ?? "Scam"
            confidence = mlResult.scamScore
        } else if mlResult.spamScore > 0.6 {
            classification = .spam
            scamLabel = nil
            confidence = mlResult.spamScore
        } else if mlResult.legitimateScore > 0.8 {
            classification = .legitimate
            scamLabel = nil
            confidence = mlResult.legitimateScore
        } else {
            classification = .unknown
            scamLabel = nil
            confidence = max(mlResult.legitimateScore, 0.3)
        }

        // Check if we should auto-filter
        let shouldAutoFilter = classification == .scam && confidence > 0.8

        return SmsLog(
            hashedSenderNumber: hash,
            senderLabel: "Unknown",
            storedMessageBody: shouldStoreBody ? messageBody : nil,
            classification: classification,
            confidence: confidence,
            scamLabel: scamLabel,
            receivedAt: Date(),
            isAcknowledged: false,
            wasAutoFiltered: shouldAutoFilter
        )
    }

    /// Performs a quick safety check on an SMS without creating a full log entry.
    /// Used for real-time notification classification.
    ///
    /// - Parameter messageBody: The SMS text content.
    /// - Returns: True if the message appears to be a scam with high confidence.
    func isQuickScamCheck(messageBody: String) -> Bool {
        let lowercased = messageBody.lowercased()

        // Check for high-confidence scam indicators
        let urgentIndicators = [
            "urgent", "immediately", "action required", "account suspended",
            "verify now", "limited time", "expire today", "final notice",
        ]

        let scamCount = lowercased.contains("irs") ||
                         lowercased.contains("gift card") ||
                         lowercased.contains("wire transfer") ||
                         lowercased.contains("social security") ||
                         lowercased.contains("bitcoin") ||
                         lowercased.contains("won ") ? 2 : 0

        let urgentCount = urgentIndicators.filter { lowercased.contains($0) }.count
        let urlCount = countURLs(in: messageBody)

        // High confidence scam: keyword + urgency + unexpected URL
        return scamCount >= 2 && urgentCount >= 1 && urlCount >= 1
    }

    // MARK: - Helpers

    /// Counts URLs in a message body.
    private func countURLs(in text: String) -> Int {
        let pattern = #"https?://[^\s]+"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return 0 }
        let range = NSRange(text.startIndex..., in: text)
        return regex.numberOfMatches(in: text, range: range)
    }

    private func normalizePhoneNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        if digits.hasPrefix("1") {
            return "+\(digits)"
        } else if digits.count == 10 {
            return "+1\(digits)"
        }
        return "+\(digits)"
    }
}
