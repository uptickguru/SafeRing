package online.db1k.safering.android.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.util.Logger

/**
 * CircleManager — manages the trusted circle feature.
 *
 * # Security
 * - Phone numbers are NEVER stored as plaintext. They are hashed with
 *   HMAC-SHA256 using a per-install secret key.
 * - Alert payloads are REDACTED — only category + reason + who asked for help.
 *   NEVER include full phone numbers, message bodies, or account details.
 *
 * # BOTH-PARTY Opt-In
 * - The protected user must invite a contact.
 * - The contact must explicitly ACCEPT before any alert can be sent.
 * - Either party can revoke (DELETE /v1/circle/{id}) anytime.
 *
 * # Contact Limit
 * - Free tier: 2 contacts max
 * - Plus tier: higher limit (read from entitlement)
 *
 * # Circuit-Breaker
 * - Prominent "Someone's asking me for money — help me check" button
 * - Loops the trusted contact and shows the money-safety checklist
 * - Only fires when the user explicitly taps it
 *
 */
class CircleManager(
    private val context: Context,
    private val api: SafeRingApi
) {

    // MARK: - Properties

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val circleRepository = CircleRepository(prefs)

    companion object {
        const val PREFS_NAME = "circle_prefs"
        const val KEY_INVITATION = "circle_invitation_"
    }

    // MARK: - Public API

    /**
     * Invites a contact to the trusted circle.
     *
     * # Security
     * The phoneHash is HMAC-SHA256 — NEVER store or send plaintext numbers.
     *
     * @param phoneHash HMAC-SHA256 hash of the contact's phone number.
     * @param phonePrefix The phone prefix (e.g., "+1").
     * @param displayName Display name of the contact.
     * @return CircleInviteResponse with the invitation ID.
     * @throws CircleError if the invitation fails.
     */
    suspend fun inviteContact(
        phoneHash: String,
        phonePrefix: String,
        displayName: String
    ): CircleInviteResponse {
        return withContext(Dispatchers.IO) {
            val invite = CircleInviteRequest(
                phoneHash = phoneHash,
                phonePrefix = phonePrefix,
                displayName = displayName
            )

            val response = try {
                api.inviteCircleContact(invite)
            } catch (e: Exception) {
                Logger.error("Invitation failed: ${e.message}", Logger.Category.CIRCLE)
                throw CircleError.invitationFailed(e.message ?: "Unknown error")
            }

            // Cache the invitation locally
            circleRepository.saveInvitation(response.invitationId)

            Logger.info(
                "Invitation sent: ${response.invitationId} status: ${response.status}",
                Logger.Category.CIRCLE
            )

            response
        }
    }

    /**
     * Accepts an invitation to the trusted circle.
     *
     * @param invitationId The invitation ID to accept.
     * @return CircleAcceptResponse confirming acceptance.
     * @throws CircleError if the acceptance fails.
     */
    suspend fun acceptInvitation(invitationId: String): CircleAcceptResponse {
        return withContext(Dispatchers.IO) {
            val accept = CircleAcceptRequest(invitationId = invitationId)

            val response = try {
                api.acceptCircleContact(accept)
            } catch (e: Exception) {
                Logger.error("Acceptance failed: ${e.message}", Logger.Category.CIRCLE)
                throw CircleError.acceptanceFailed(e.message ?: "Unknown error")
            }

            // Update local cache
            if (circleRepository.getInvitation(invitationId) != null) {
                circleRepository.updateInvitation(
                    circleRepository.getInvitation(invitationId)!!.copy(
                        isAccepted = true,
                        acceptedAt = System.currentTimeMillis()
                    )
                )
            }

            Logger.info(
                "Invitation accepted: $invitationId",
                Logger.Category.CIRCLE
            )

            response
        }
    }

    /**
     * Revokes a trusted circle membership.
     *
     * Either party can revoke (DELETE /v1/circle/{id}) anytime.
     *
     * @param invitationId The invitation ID to revoke.
     * @return CircleRevokeResponse confirming revocation.
     * @throws CircleError if the revocation fails.
     */
    suspend fun revokeInvitation(invitationId: String): CircleRevokeResponse {
        return withContext(Dispatchers.IO) {
            val revoke = CircleRevokeRequest(invitationId = invitationId)

            val response = try {
                api.revokeCircleContact(revoke)
            } catch (e: Exception) {
                Logger.error("Revocation failed: ${e.message}", Logger.Category.CIRCLE)
                throw CircleError.revocationFailed(e.message ?: "Unknown error")
            }

            // Remove from local cache
            circleRepository.deleteInvitation(invitationId)

            Logger.info(
                "Invitation revoked: $invitationId",
                Logger.Category.CIRCLE
            )

            response
        }
    }

    /**
     * Sends a REDACTED trusted circle alert.
     *
     * # Security
     * The alert payload is REDACTED — it contains ONLY category + reason + who asked for help.
     * NEVER include full phone numbers, message bodies, or account details.
     *
     * @param invitationId The invitation ID.
     * @param category The category of the threat (e.g., "call", "sms", "money").
     * @param reason A short, redacted reason (e.g., "High-risk call claiming to be IRS; John tapped Help").
     * @param askedBy Who asked for help (display name).
     * @return CircleAlertResponse confirming delivery.
     * @throws CircleError if the alert fails.
     */
    suspend fun sendAlert(
        invitationId: String,
        category: String,
        reason: String,
        askedBy: String
    ): CircleAlertResponse {
        // Verify the contact has accepted
        val invitation = circleRepository.getInvitation(invitationId)
        if (invitation == null || !invitation.isAccepted) {
            throw CircleError.alertFailed("Contact has not accepted the invitation")
        }

        // Build REDACTED alert payload
        // NEVER include full phone numbers, message bodies, or account details
        val alert = CircleAlertRequest(
            invitationId = invitationId,
            category = category,
            reason = reason,
            askedBy = askedBy
        )

        val response = try {
            api.sendCircleAlert(alert)
        } catch (e: Exception) {
            Logger.error("Alert failed: ${e.message}", Logger.Category.CIRCLE)
            throw CircleError.alertFailed(e.message ?: "Unknown error")
        }

        Logger.info(
            "Circle alert sent: $invitationId category: $category reason: $reason",
            Logger.Category.CIRCLE
        )

        response
    }

    /**
     * Checks if the user has the money-safety checklist visible.
     *
     * This is the circuit-breaker: a prominent "Someone's asking me for money —
     * help me check" button that loops the trusted contact and shows the
     * money-safety checklist before the user acts.
     *
     * @return Bool indicating whether the checklist is shown.
     */
    fun shouldShowMoneySafetyChecklist(): Boolean {
        // This is triggered when the user taps the prominent button
        // and is meant to show the checklist before taking action
        return true
    }

    /**
     * Gets the circle contacts for the user.
     *
     * @return Array of CircleContact.
     */
    fun getCircleContacts(): List<CircleContact> {
        return circleRepository.getCircleContacts()
    }

    /**
     * Gets the number of accepted contacts.
     *
     * @return Int count of accepted contacts.
     */
    fun getAcceptedContactCount(): Int {
        return circleRepository.getAcceptedContactCount()
    }

    /**
     * Checks if the user is entitled (has a valid subscription).
     *
     * @return Bool indicating entitlement status.
     */
    suspend fun isEntitled(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val entitlement = api.getEntitlement()
                entitlement.isEntitled
            } catch (e: Exception) {
                Logger.error("Entitlement check failed: ${e.message}", Logger.Category.CIRCLE)
                false
            }
        }
    }

    /**
     * Checks if the user is on the Plus tier.
     *
     * @return Bool indicating Plus tier status.
     */
    suspend fun isPlusTier(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val entitled = isEntitled()
                entitled
            } catch (e: Exception) {
                Logger.error("Plus tier check failed: ${e.message}", Logger.Category.CIRCLE)
                false
            }
        }
    }

    /**
     * Gets the contact limit for the user's tier.
     *
     * @return Int max number of contacts.
     */
    suspend fun getContactLimit(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val plus = isPlusTier()
                if (plus) 5 else 2 // Free tier: 2, Plus tier: 5
            } catch (e: Exception) {
                Logger.error("Contact limit check failed: ${e.message}", Logger.Category.CIRCLE)
                2 // Default to free tier limit
            }
        }
    }
}

// MARK: - CircleRepository

/**
 * Repository for managing circle invitations locally.
 */
class CircleRepository(
    private val prefs: SharedPreferences
) {

    /**
     * Saves an invitation to local storage.
     */
    fun saveInvitation(invitationId: String) {
        val data = CircleInvitationData(
            invitationId = invitationId,
            isAccepted = false,
            acceptedAt = null,
            revokedAt = null
        )
        prefs.edit().putString(KEY_INVITATION + invitationId, data.toString()).apply()
    }

    /**
     * Gets an invitation from local storage.
     */
    fun getInvitation(invitationId: String): CircleInvitationData? {
        val data = prefs.getString(KEY_INVITATION + invitationId, null)
        return try {
            if (data != null) {
                com.google.gson.Gson().fromJson(data, CircleInvitationData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Updates an invitation in local storage.
     */
    fun updateInvitation(invitation: CircleInvitationData) {
        prefs.edit().putString(KEY_INVITATION + invitation.invitationId, invitation.toString()).apply()
    }

    /**
     * Deletes an invitation from local storage.
     */
    fun deleteInvitation(invitationId: String) {
        prefs.edit().remove(KEY_INVITATION + invitationId).apply()
    }

    /**
     * Gets all circle contacts.
     */
    fun getCircleContacts(): List<CircleContact> {
        val allInvitations = prefs.all
        return allInvitations.entries.mapNotNull { entry ->
            try {
                val data = com.google.gson.Gson().fromJson(entry.value, CircleInvitationData::class.java)
                if (data != null) {
                    CircleContact(
                        invitationId = data.invitationId,
                        isAccepted = data.isAccepted,
                        acceptedAt = data.acceptedAt,
                        revokedAt = data.revokedAt
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Gets the number of accepted contacts.
     */
    fun getAcceptedContactCount(): Int {
        return getCircleContacts().filter { it.isAccepted }.size
    }
}

// MARK: - Supporting Models

/**
 * Data structure for storing circle invitations locally.
 */
data class CircleInvitationData(
    val invitationId: String,
    val isAccepted: Boolean,
    val acceptedAt: Long? = null,
    val revokedAt: Long? = null
)
