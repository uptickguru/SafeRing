import Foundation

/// ViewModel for the ThreatActionScreen.
///
/// # Critical Safety Rule
/// This ViewModel MUST drive HUMAN ACTION. It MUST NEVER present a "you're safe,
/// proceed" terminal state that closes the loop on the AI's verdict. There is
/// no screen state that ends at "safe."
///
/// # Threat Actions
/// - CALL_SAVED_CONTACT: Don't call back this number. Call {SavedContact} on their real number.
/// - ASK_FAMILY_PASSWORD: *** to ask them your family password (no field that transmits it).
/// - LOOP_TRUSTED_CONTACT: Alert the trusted contact (M5).
/// - DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
/// - LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee. Keeps "Verify with trusted contact" visible.
///
/// # Security
/// Phone numbers are hashed with HMAC-SHA256 (not plain SHA-256) before any
/// network call. HMAC uses a per-install secret key provisioned at enrollment,
/// making the hash computationally infeasible to reverse.
///
/// # Threat Model
/// Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
/// makes it trivially reversible. HMAC-SHA256 with a secret key provides
/// pseudonymization, making it computationally infeasible to recover the
/// original number from the hash.
///
/// # Defer Heavy Analysis
/// The screening callback must return quickly. Heavy operations (ML analysis,
/// trusted circle checks, HITL flow) are deferred to a WorkManager job.
class ThreatActionViewModel {

    // MARK: - State

    private(set) var recommendedAction: ThreatAction?
    private(set) var callerLabel: String = ""
    private(set) var savedContact: SavedContact?
    private(set) var userOptedIn: Bool = true
    private(set) var numberHash: String = ""
    private(set) var wasBlocked: Bool = false

    // MARK: - Initializer

    init() {}

    // MARK: - Actions

    /// Set the recommended action from the threat detection system.
    /// This is called when a threat is detected.
    func setRecommendedAction(_ action: ThreatAction) {
        recommendedAction = action
        Logger.info("Threat action set: \(action.rawValue)", Logger.Category.CALL)
    }

    /// Set the caller label (display name).
    func setCallerLabel(_ label: String) {
        callerLabel = label
    }

    /// Set the saved contact to call instead.
    func setSavedContact(_ contact: SavedContact) {
        savedContact = contact
    }

    /// Set whether the user has opted into trusted circle alerts.
    func setUserOptedIn(_ optedIn: Bool) {
        userOptedIn = optedIn
    }

    /// Set the number hash for event reporting.
    func setNumberHash(_ hash: String) {
        numberHash = hash
    }

    /// Set whether the call was blocked.
    func setWasBlocked(_ blocked: Bool) {
        wasBlocked = blocked
    }

    /// Perform the recommended human action.
    /// This is called when the user taps a button on the ThreatActionScreen.
    func performHumanAction() {
        guard let action = recommendedAction else { return }
        Logger.info("Human action performed: \(action.rawValue)", Logger.Category.CALL)
    }

    /// Open the phone app with the saved number (for CALL_SAVED_CONTACT).
    /// This dials the SAVED number only, never the incoming/suspect number.
    func openPhoneApp(savedNumber: String) {
        // In production, this would open the phone app with the dialer
        // Pre-fill the saved number, never the incoming number
        Logger.info("Opening phone app with saved number: \(savedNumber)", Logger.Category.CALL)
    }

    /// Perform the family password prompt (for ASK_FAMILY_PASSWORD).
    /// This does NOT transmit any information to the caller.
    func performFamilyPasswordPrompt() {
        // In production, this would show a UI prompt to ask the caller
        // for the family password without transmitting any information
        Logger.info("Family password prompt triggered (M6)", Logger.Category.CALL)
    }

    /// Trigger a trusted circle alert (for LOOP_TRUSTED_CONTACT / LOOKS_OK_STILL_VERIFY).
    /// Only fires if the user has opted in (M5).
    func triggerTrustedCircleAlert() {
        if userOptedIn {
            Logger.info("Trusted circle alert triggered (M5)", Logger.Category.CALL)
        }
    }

    /// Report the threat as a scam.
    func reportAsScam() {
        // In production, this would report the threat to the server
        Logger.info("Reported as scam", Logger.Category.CALL)
    }
}


