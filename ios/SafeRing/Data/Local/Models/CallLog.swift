import Foundation
import SwiftData

/// SwiftData model for the user's incoming and outgoing call history.
///
/// # Zero PII Policy
/// The `hashedPhoneNumber` stores a **SHA-256 hash**, never the raw phone number.
/// The raw number is ephemeral — used only for on-device matching and immediately
/// discarded after hashing.
///
@Model
final class CallLog {

    // MARK: - Attributes

    /// SHA-256 hash of the phone number (hex-encoded).
    @Attribute(.unique) var id: UUID

    /// SHA-256 hash of the caller's phone number (hex-encoded).
    var hashedPhoneNumber: String

    /// Display label for the caller (e.g., "Unknown", "John" from Contacts).
    /// Never stored as PII — this is a user-facing convenience label only.
    var callerLabel: String

    /// Direction of the call.
    var direction: CallDirection

    /// Result of scam screening.
    var screeningResult: ScreeningResult

    /// Risk score returned by the scam detection system (0.0–1.0).
    var riskScore: Double

    /// Scam type label if identified as a scam (e.g., "IRS-Impression").
    var scamLabel: String?

    /// Duration of the call in seconds.
    var duration: TimeInterval

    /// When the call occurred.
    var timestamp: Date

    /// Whether the user has acknowledged/seen this log entry.
    var isAcknowledged: Bool

    // MARK: - Initializer

    init(
        hashedPhoneNumber: String,
        callerLabel: String = "Unknown",
        direction: CallDirection = .incoming,
        screeningResult: ScreeningResult = .unknown,
        riskScore: Double = 0.0,
        scamLabel: String? = nil,
        duration: TimeInterval = 0,
        timestamp: Date = Date(),
        isAcknowledged: Bool = false
    ) {
        self.id = UUID()
        self.hashedPhoneNumber = hashedPhoneNumber
        self.callerLabel = callerLabel
        self.direction = direction
        self.screeningResult = screeningResult
        self.riskScore = riskScore
        self.scamLabel = scamLabel
        self.duration = duration
        self.timestamp = timestamp
        self.isAcknowledged = isAcknowledged
    }
}

// MARK: - Supporting Enums

extension CallLog {
    /// Direction of the call relative to the user.
    enum CallDirection: String, Codable, CaseIterable {
        case incoming = "Incoming"
        case outgoing = "Outgoing"
        case missed = "Missed"
    }

    /// Result of the scam screening check.
    enum ScreeningResult: String, Codable, CaseIterable {
        case unknown = "Unknown"
        case safe = "Safe"
        case suspicious = "Suspicious"
        case blocked = "Blocked"
        case scam = "Scam"
    }
}
