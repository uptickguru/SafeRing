import Foundation

/// Use case: Check an incoming SMS message for scam content.
///
/// # Zero PII
/// - The sender's phone number is hashed immediately.
/// - The message body is processed **entirely on-device** by the CoreML SMS classifier.
/// - The raw message text is NEVER transmitted to any server.
/// - If the user opts in, the body may be stored locally for review.
///
/// # Classification Pipeline
/// 1. **Keyword scan:** Fast regex-based check for known scam trigger phrases.
/// 2. **ML classifier:** On-device CoreML model for deeper semantic analysis.
/// 3. **Combined score:** Weighted combination of both approaches.
///
final class CheckSmsUseCase {

    // MARK: - Properties

    private let smsClassifier: SmsClassifier
    private let repository: ScamRepository?

    // MARK: - Known Scam Keywords

    /// Keywords/phrases strongly correlated with scam SMS messages.
    /// Used for the fast keyword pre-scan before ML inference.
    private static let scamKeywords: [String] = [
        "social security", "ssn", "irs", "tax refund", "tax credit",
        "gift card", "wire transfer", "western union", "moneygram",
        "you won", "congratulations", "prize", "lottery", "inheritance",
        "account suspended", "verify account", "unusual activity",
        "click here", "urgent action", "limited time", "expire",
        "bank account", "routing number", "credit card", "debit card",
        "medicare", "medicaid", "health insurance",
        "tech support", "microsoft", "apple support", "virus detected",
        "grandson", "granddaughter", "family emergency",
        "amazon order", "fedex delivery", "usps tracking",
        "cryptocurrency", "bitcoin", "investment opportunity",
        "work from home", "make money fast", "passive income",
        "dating", "romance", "single", "lonely",
        "stimulus", "government grant", "free money",
        "paypal", "venmo", "cashapp", "zelle",
    ]

    // MARK: - Initializer

    init(smsClassifier: SmsClassifier, repository: ScamRepository? = nil) {
        self.smsClassifier = smsClassifier
        self.repository = repository
    }

    // MARK: - Execution

    /// Executes a scam check on an SMS message.
    ///
    /// - Parameters:
    ///   - senderNumber: The raw sender phone number. Will be hashed immediately.
    ///   - messageBody: The raw SMS text. Processed on-device only.
    /// - Returns: SmsRisk domain model with classification.
    func execute(senderNumber: String, messageBody: String) async -> SmsRisk {
        let normalized = normalizePhoneNumber(senderNumber)
        let hash = HashUtils.sha256(normalized)

        Logger.shared.info(
            "Checking SMS from hash: \(hash.prefix(8))...",
            category: .useCase
        )

        // 1. Fast keyword pre-scan
        let keywordResult = scanKeywords(in: messageBody)

        // 2. On-device ML classification
        let mlResult = smsClassifier.classify(message: messageBody)

        // 3. Weighted combination
        let combined = combineResults(keyword: keywordResult, ml: mlResult)

        // 4. Determine classification
        let classification: SmsRisk.Classification
        let scamType: String?
        let confidence: Double

        if combined.scamScore > 0.7 {
            classification = .scam
            scamType = combined.scamType ?? "General Scam"
            confidence = combined.confidence
        } else if combined.spamScore > 0.6 {
            classification = .spam
            scamType = nil
            confidence = combined.confidence
        } else if combined.legitimateScore > 0.8 {
            classification = .legitimate
            scamType = nil
            confidence = combined.confidence
        } else {
            classification = .unknown
            scamType = nil
            confidence = combined.confidence
        }

        return SmsRisk(
            hashedSenderNumber: hash,
            messageBody: messageBody,
            classification: classification,
            confidence: confidence,
            scamType: scamType,
            triggerPhrases: keywordResult.triggeredPhrases,
            assessedAt: Date(),
            source: .mlModel
        )
    }

    // MARK: - Keyword Scan

    /// Scans the message body for known scam trigger phrases.
    /// Returns matched phrases and a preliminary risk score.
    private func scanKeywords(in body: String) -> KeywordResult {
        let lowercased = body.lowercased()
        var matched: [String] = []
        var score: Double = 0.0

        for phrase in Self.scamKeywords {
            if lowercased.contains(phrase) {
                matched.append(phrase)
                score += 0.1 // Each match adds 0.1 to risk
            }
        }

        // Cap keyword score at 0.6 (ML provides the rest)
        score = min(score, 0.6)

        return KeywordResult(
            triggeredPhrases: matched,
            scamScore: score
        )
    }

    // MARK: - Result Combination

    /// Combines keyword scan results with ML classification for a final assessment.
    private func combineResults(
        keyword: KeywordResult,
        ml: SmsClassifierResult
    ) -> CombinedResult {
        // Weight: keyword 30%, ML 70%
        let finalScamScore = (keyword.scamScore * 0.3) + (ml.scamScore * 0.7)
        let finalConfidence = (0.5 * 0.3) + (ml.confidence * 0.7)

        // Determine scam type from ML or keyword context
        let scamType = ml.scamLabel ?? (keyword.scamScore > 0.3 ? "Keyword-Matched" : nil)

        // Spam score — primarily ML
        let spamScore = ml.spamScore

        // Legitimate score (inverse of scam + spam)
        let legitimateScore = max(0, 1.0 - finalScamScore - spamScore)

        return CombinedResult(
            scamScore: finalScamScore,
            spamScore: spamScore,
            legitimateScore: legitimateScore,
            confidence: finalConfidence,
            scamType: scamType
        )
    }

    // MARK: - Helpers

    private func normalizePhoneNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        if digits.hasPrefix("1") {
            return "+\(digits)"
        } else if digits.count == 10 {
            return "+1\(digits)"
        } else {
            return "+\(digits)"
        }
    }
}

// MARK: - Internal Types

private struct KeywordResult {
    let triggeredPhrases: [String]
    let scamScore: Double
}

struct SmsClassifierResult {
    let scamScore: Double
    let spamScore: Double
    let legitimateScore: Double
    let confidence: Double
    let scamLabel: String?
}

private struct CombinedResult {
    let scamScore: Double
    let spamScore: Double
    let legitimateScore: Double
    let confidence: Double
    let scamType: String?
}
