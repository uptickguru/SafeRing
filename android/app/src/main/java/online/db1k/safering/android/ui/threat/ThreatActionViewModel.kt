package online.db1k.safering.android.ui.threat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.remote.models.EventRequest
import online.db1k.safering.android.util.Logger

/**
 * ViewModel for the ThreatActionScreen.
 *
 * # Critical Safety Rule
 * This ViewModel MUST drive HUMAN ACTION. It MUST NEVER present a "you're safe,
 * proceed" terminal state that closes the loop on the AI's verdict. There is
 * no screen state that ends at "safe."
 *
 * # Threat Actions
 * - CALL_SAVED_CONTACT: Don't call back this number. Call {SavedContact} on their real number.
 * - ASK_FAMILY_PASSWORD: *** to ask them your family password (no field that transmits it).
 * - LOOP_TRUSTED_CONTACT: Alert the trusted contact (M5).
 * - DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
 * - LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee. Keeps "Verify with trusted contact" visible.
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
class ThreatActionViewModel(
    private val database: AppDatabase,
    private val api: SafeRingApi
) : ViewModel() {

    // MARK: - State

    private var _recommendedAction: ThreatAction? = null
    val recommendedAction: ThreatAction? get() = _recommendedAction

    private var _callerLabel: String = ""
    val callerLabel: String get() = _callerLabel

    private var _savedContact: SavedContact? = null
    val savedContact: SavedContact? get() = _savedContact

    private var _userOptedIn: Boolean = true
    val userOptedIn: Boolean get() = _userOptedIn

    private var _numberHash: String = ""
    val numberHash: String get() = _numberHash

    private var _wasBlocked: Boolean = false
    val wasBlocked: Boolean get() = _wasBlocked

    // MARK: - Actions

    /**
     * Set the recommended action from the threat detection system.
     * This is called when a threat is detected.
     */
    fun setRecommendedAction(action: ThreatAction) {
        _recommendedAction = action
        Logger.info("Threat action set: ${action::class.simpleName}", Logger.Category.CALL)
    }

    /**
     * Set the caller label (display name).
     */
    fun setCallerLabel(label: String) {
        _callerLabel = label
    }

    /**
     * Set the saved contact to call instead.
     */
    fun setSavedContact(contact: SavedContact) {
        _savedContact = contact
    }

    /**
     * Set whether the user has opted into trusted circle alerts.
     */
    fun setUserOptedIn(optedIn: Boolean) {
        _userOptedIn = optedIn
    }

    /**
     * Set the number hash for event reporting.
     */
    fun setNumberHash(hash: String) {
        _numberHash = hash
    }

    /**
     * Set whether the call was blocked.
     */
    fun setWasBlocked(blocked: Boolean) {
        _wasBlocked = blocked
    }

    /**
     * Perform the recommended human action.
     * This is called when the user taps a button on the ThreatActionScreen.
     */
    fun performHumanAction() {
        val action = _recommendedAction ?: return
        Logger.info("Human action performed: ${action::class.simpleName}", Logger.Category.CALL)
    }

    /**
     * Open the phone app with the saved number (for CALL_SAVED_CONTACT).
     * This dials the SAVED number only, never the incoming/suspect number.
     */
    fun openPhoneApp(savedNumber: String) {
        // In production, this would open the phone app with the dialer
        // Pre-fill the saved number, never the incoming number
        Logger.info("Opening phone app with saved number: $savedNumber", Logger.Category.CALL)
    }

    /**
     * Perform the family password prompt (for ASK_FAMILY_PASSWORD).
     * This does NOT transmit any information to the caller.
     */
    fun performFamilyPasswordPrompt() {
        // In production, this would show a UI prompt to ask the caller
        // for the family password without transmitting any information
        Logger.info("Family password prompt triggered (M6)", Logger.Category.CALL)
    }

    /**
     * Trigger a trusted circle alert (for LOOP_TRUSTED_CONTACT / LOOKS_OK_STILL_VERIFY).
     * Only fires if the user has opted in (M5).
     */
    fun triggerTrustedCircleAlert() {
        if (_userOptedIn) {
            Logger.info("Trusted circle alert triggered (M5)", Logger.Category.CALL)
        }
    }

    /**
     * Report the threat as a scam.
     */
    fun reportAsScam() {
        // In production, this would report the threat to the server
        Logger.info("Reported as scam", Logger.Category.CALL)
    }
}
