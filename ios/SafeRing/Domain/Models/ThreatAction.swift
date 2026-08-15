import Foundation

/// ThreatAction — the recommended human action when a threat is detected.
///
/// # Critical Safety Rule
/// This enum MUST drive HUMAN ACTION. There is NO terminal "safe/proceed" state.
/// Every case routes to a human action that keeps the loop open.
///
/// # Threat Actions
/// - CALL_SAVED_CONTACT: Don't call back this number. Call {SavedContact} on their real number.
/// - ASK_FAMILY_PASSWORD: Prompt to ask them your family password (no field that transmits it).
/// - LOOP_TRUSTED_CONTACT: Alert the trusted contact (M5).
/// - DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
/// - LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee. Keeps "Verify with trusted contact" visible.
///
enum ThreatAction: String, Codable, CaseIterable {
    /// Call the saved contact instead of the suspicious caller.
    /// The dial action targets the SAVED number only, never the incoming/suspect number.
    case callSavedContact(SavedContact)

    /// Prompt to ask the caller for your family password.
    /// No field that transmits the password — see M6.
    case askFamilyPassword

    /// Alert the trusted contact (M5).
    case loopTrustedContact

    /// Don't respond to this message. It could be a scam attempt.
    case doNotReply

    /// This is NOT a guarantee of safety. Keeps "Verify with trusted contact" visible.
    case looksOkStillVerify
}

// MARK: - Saved Contact

/// A saved contact that can be called instead of the suspicious caller.
///
/// # Security
/// The `savedNumber` is the REAL, SAVED number from the user's contacts.
/// It is NEVER the incoming/suspect number. This is a critical safety rule.
struct SavedContact: Identifiable, Codable, Equatable {
    /// Unique identifier for the saved contact.
    let id: UUID

    /// Display name of the saved contact (e.g., "John", "Mom").
    let displayName: String

    /// The real, saved phone number to dial.
    /// This is NEVER the incoming/suspect number.
    let savedNumber: String
}

// MARK: - Preview

#Preview {
    ThreatActionView(
        recommendedAction: .callSavedContact(.init(id: .uuid(), displayName: "Mom", savedNumber: "+1234567890")),
        callerLabel: "Unknown",
        savedContact: .init(id: .uuid(), displayName: "Mom", savedNumber: "+1234567890"),
        userOptedIn: true,
        numberHash: "abc123",
        wasBlocked: false
    )
}
