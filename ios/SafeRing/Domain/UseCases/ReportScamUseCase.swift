import Foundation

/// Use case: Submit a user report for a scam phone number.
///
/// # Zero PII
/// Only the SHA-256 hash of the phone number is submitted to the API.
/// The user optionally provides a scam type tag — no personal data.
///
/// # Flow
/// 1. Hash the raw phone number.
/// 2. Create a ReportRequest with the hash and scam type.
/// 3. Submit via the repository.
/// 4. Log the result for the UI.
///
final class ReportScamUseCase {

    // MARK: - Properties

    private let repository: ScamRepository

    // MARK: - Initializer

    init(repository: ScamRepository) {
        self.repository = repository
    }

    // MARK: - Execution

    /// Reports a phone number as a scam.
    ///
    /// - Parameters:
    ///   - rawNumber: The raw phone number string (will be hashed immediately).
    ///   - scamTag: Tag identifying the scam type (e.g., "IRS", "Tech Support").
    /// - Returns: A confirmation message.
    /// - Throws: ReportError if submission fails.
    func execute(rawNumber: String, scamTag: String) async throws -> String {
        let normalized = normalizePhoneNumber(rawNumber)
        let hash = HashUtils.sha256(normalized)

        Logger.shared.info(
            "User reporting scam: hash=\(hash.prefix(8))..., tag=\(scamTag)",
            category: .useCase
        )

        do {
            let response = try await repository.reportScam(hash: hash, tag: scamTag)

            if response.success {
                Logger.shared.info(
                    "Scam report submitted successfully. Total reports: \(response.totalReports ?? 0)",
                    category: .useCase
                )
                return "Thank you for reporting this number. Your report helps protect others in your community. 🛡️"
            } else {
                throw ReportError.reportRejected(message: response.message)
            }
        } catch let error as ReportError {
            throw error
        } catch {
            Logger.shared.error(
                "Failed to submit scam report: \(error.localizedDescription)",
                category: .useCase
            )
            throw ReportError.reportFailed(underlying: error)
        }
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

// MARK: - Errors

enum ReportError: LocalizedError {
    case reportFailed(underlying: Error)
    case reportRejected(message: String)

    var errorDescription: String? {
        switch self {
        case .reportFailed(let error):
            return "Report submission failed: \(error.localizedDescription)"
        case .reportRejected(let message):
            return "Report rejected: \(message)"
        }
    }
}
