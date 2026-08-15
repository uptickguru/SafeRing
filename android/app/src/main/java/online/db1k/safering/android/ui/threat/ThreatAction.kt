package online.db1k.safering.android.ui.threat

/**
 * ThreatAction — the recommended human action when a threat is detected.
 *
 * # Critical Safety Rule
 * This enum MUST drive HUMAN ACTION. There is NO terminal "safe/proceed" state.
 * Every case routes to a human action that keeps the loop open.
 *
 * # Threat Actions
 * - CALL_SAVED_CONTACT: Don't call back this number. Call {SavedContact} on their real number.
 * - ASK_FAMILY_PASSWORD: *** to ask them your family password (no field that transmits it).
 * - LOOP_TRUSTED_CONTACT: Alert the trusted contact (M5).
 * - DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
 * - LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee. Keeps "Verify with trusted contact" visible.
 *
 * # Accessibility
 * - All buttons ≥48dp for touch targets
 * - TalkBack labels on all interactive elements
 * - High contrast colors for risk indicators
 * - Dynamic Type compatible
 *
 * # Security
 * Phone numbers are hashed with HMAC-SHA256 (not plain SHA-256) before any
 * network call. HMAC uses a per-install secret key provisioned at enrollment,
 * making the hash computationally infeasible to reverse.
 *
 * # Threat Model
 * Plain SHA-256(number) is NOT anonymization — the search space (~10^10)
 * makes it trivially reversible. HMAC-SHA256 with a secret key provides
 * pseudonymization, making it computationally infeasible to recover the
 * original number from the hash.
 *
 * # Defer Heavy Analysis
 * The screening callback must return quickly. Heavy operations (ML analysis,
 * trusted circle checks, HITL flow) are deferred to a WorkManager job.
 *
 */
sealed class ThreatAction {
    /**
     * Call the saved contact instead of the suspicious caller.
     * The dial action targets the SAVED number only, never the incoming/suspect number.
     *
     * @param contact The saved contact to call instead
     */
    data class CallSavedContact(val contact: SavedContact) : ThreatAction()

    /**
     * Prompt to ask the caller for your family password.
     * No field that transmits the password — see M6.
     */
    object AskFamilyPassword : ThreatAction()

    /**
     * Alert the trusted contact (M5).
     */
    object LoopTrustedContact : ThreatAction()

    /**
     * Don't respond to this message. It could be a scam attempt.
     */
    object DoNotReply : ThreatAction()

    /**
     * This is NOT a guarantee of safety. Keeps "Verify with trusted contact" visible.
     */
    object LooksOkStillVerify : ThreatAction()
}

/**
 * A saved contact that can be called instead of the suspicious caller.
 *
 * # Security
 * The `savedNumber` is the REAL, SAVED number from the user's contacts.
 * It is NEVER the incoming/suspect number. This is a critical safety rule.
 */
data class SavedContact(
    val id: Long = 0,
    val displayName: String,
    val savedNumber: String
)
