import Foundation

/// Use case: Check an incoming or outgoing call for scam risk.
///
/// Steps:
/// 1. Hash the raw phone number using SHA-256.
/// 2. Query the local cache via repository (instant).
/// 3. If cache miss or stale, query the remote API.
/// 4. Optionally run the on-device ML number classifier.
/// 5. Return a `CallRisk` domain model.
///
/// # Zero PII
/// The raw phone number is hashed immediately and never leaves the device.
/// Only the SHA-256 hash is used for lookups.
///
final class CheckCallUseCase {

    // MARK: - Properties

    private let repository: ScamRepository
    private let numberClassifier: NumberClassifier?

    // MARK: - Initializer

    init(
        repository: ScamRepository,
        numberClassifier: NumberClassifier? = nil
    ) {
        self.repository = repository
        self.numberClassifier = numberClassifier
    }

    // MARK: - Execution

    /// Executes a full scam check on the given phone number.
    ///
    /// - Parameter rawNumber: The raw phone number string (e.g., "+15551234567").
    ///   This is immediately hashed — never persisted or transmitted in plaintext.
    /// - Returns: A CallRisk domain model with the assessment.
    /// - Throws: CheckCallError if the check cannot be completed.
    func execute(rawNumber: String) async throws -> CallRisk {
        // 1. Normalize and hash the phone number
        let normalized = normalizePhoneNumber(rawNumber)
        let hash = HashUtils.sha256(normalized)

        Logger.shared.info(
            "Checking call risk for hash: \(hash.prefix(8))...",
            category: .useCase
        )

        // 2. Build risk assessment
        let assessment: (score: Double, label: String?, confidence: Double, source: CallRisk.RiskSource)

        do {
            let result = try await repository.checkNumber(hash: hash)
            assessment = (
                score: result.riskScore,
                label: result.scamLabel,
                confidence: result.confidence,
                source: {
                    switch result.source {
                    case .localCache: return .localCache
                    case .remote:     return .remoteAPI
                    case .staleCache: return .staleCache
                    }
                }()
            )
        } catch {
            // 3. Fallback: try on-device ML classifier if available
            if let classifier = numberClassifier {
                let mlResult = classifier.classify(numberHash: hash)
                assessment = (
                    score: mlResult.riskScore,
                    label: mlResult.scamLabel,
                    confidence: mlResult.confidence,
                    source: .mlModel
                )
            } else {
                throw CheckCallError.checkFailed(underlying: error)
            }
        }

        // 4. Return domain model
        return CallRisk(
            hashedPhoneNumber: hash,
            score: assessment.score,
            confidence: assessment.confidence,
            scamType: assessment.label,
            isConfirmed: assessment.score >= AppConfig.autoBlockThreshold,
            shouldBlock: assessment.score >= AppConfig.autoBlockThreshold,
            source: assessment.source,
            assessedAt: Date()
        )
    }

    // MARK: - Helpers

    /// Normalizes a phone number to E.164 format for consistent hashing.
    /// Removes spaces, dashes, parentheses, and ensures + prefix.
    /// - Parameter number: Raw phone number string.
    /// - Returns: Normalized number string ready for hashing.
    private func normalizePhoneNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        // If number doesn't start with country code, assume +1 (US)
        if digits.hasPrefix("1") {
            return "+\(digits)"
        } else if digits.count == 10 {
            return "+1\(digits)"
        } else {
            return "+\(digits)"
        }
    }
}

// MARK: - Errors

enum CheckCallError: LocalizedError {
    case checkFailed(underlying: Error)

    var errorDescription: String? {
        switch self {
        case .checkFailed(let error):
            return "Call check failed: \(error.localizedDescription)"
        }
    }
}
