import Foundation
import SwiftData

/// SwiftData model for SMS messages that have been scanned for scam content.
///
/// # Zero PII Policy
/// - The `hashedSenderNumber` stores a **SHA-256 hash**, never the raw sender number.
/// - The `messageBody` is ephemeral — used for on-device ML classification only
///   and is NOT persisted to the database unless the user explicitly chooses to
///   save it for record-keeping.
/// - By default, `storedMessageBody` is nil; the user can opt in to storing bodies
///   in settings.
///
@Model
final class SmsLog {

    // MARK: - Attributes

    @Attribute(.unique) var id: UUID

    /// SHA-256 hash of the sender's phone number (hex-encoded).
    var hashedSenderNumber: String

    /// User-facing sender label (e.g., "Unknown", contact name).
    /// Never stored as PII — local convenience only.
    var senderLabel: String

    /// The message body is ONLY stored if the user has explicitly opted in
    /// to saving SMS content for reviewing classified messages.
    /// Default is nil (no body stored).
    var storedMessageBody: String?

    /// Classification result from the on-device ML classifier.
    var classification: SmsClassification

    /// Confidence score from the ML model (0.0–1.0).
    var confidence: Double

    /// Scam type if classified as a scam (e.g., "Phishing", "IRS").
    var scamLabel: String?

    /// When the message was received.
    var receivedAt: Date

    /// Whether the user has seen/acknowledged this message.
    var isAcknowledged: Bool

    /// Whether this message was auto-moved to junk by the system.
    var wasAutoFiltered: Bool

    // MARK: - Initializer

    init(
        hashedSenderNumber: String,
        senderLabel: String = "Unknown",
        storedMessageBody: String? = nil,
        classification: SmsClassification = .unknown,
        confidence: Double = 0.0,
        scamLabel: String? = nil,
        receivedAt: Date = Date(),
        isAcknowledged: Bool = false,
        wasAutoFiltered: Bool = false
    ) {
        self.id = UUID()
        self.hashedSenderNumber = hashedSenderNumber
        self.senderLabel = senderLabel
        self.storedMessageBody = storedMessageBody
        self.classification = classification
        self.confidence = confidence
        self.scamLabel = scamLabel
        self.receivedAt = receivedAt
        self.isAcknowledged = isAcknowledged
        self.wasAutoFiltered = wasAutoFiltered
    }
}

// MARK: - Classification Types

extension SmsLog {
    /// On-device SMS classification result.
    enum SmsClassification: String, Codable, CaseIterable {
        case unknown = "Pending"
        case legitimate = "Legitimate"
        case spam = "Spam"
        case scam = "Scam"

        var displayName: String { rawValue }
    }
}
