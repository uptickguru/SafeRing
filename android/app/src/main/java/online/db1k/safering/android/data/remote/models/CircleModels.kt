package online.db1k.safering.android.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a trusted circle contact.
 *
 * # Security
 * Phone numbers are NEVER stored as plaintext. They are hashed with
 * HMAC-SHA256 using a per-install secret key. Only the hash is sent to
 * the server and displayed to the user.
 */
data class CircleContact(
    @SerializedName("id") val id: String,
    @SerializedName("inviter_name") val inviterName: String,
    @SerializedName("inviter_phone_hash") val inviterPhoneHash: String,
    @SerializedName("inviter_phone_prefix") val inviterPhonePrefix: String,
    @SerializedName("inviter_display_name") val inviterDisplayName: String,
    @SerializedName("is_accepted") val isAccepted: Boolean,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("accepted_at") val acceptedAt: Long? = null,
    @SerializedName("revoked_at") val revokedAt: Long? = null
)

/**
 * Request to invite a contact to the trusted circle.
 *
 * # Security
 * The phoneHash is HMAC-SHA256 — NEVER store or send plaintext numbers.
 */
data class CircleInviteRequest(
    @SerializedName("phone_hash") val phoneHash: String,
    @SerializedName("phone_prefix") val phonePrefix: String,
    @SerializedName("display_name") val displayName: String
)

/**
 * Response from POST /v1/circle/invite.
 */
data class CircleInviteResponse(
    @SerializedName("invitation_id") val invitationId: String,
    @SerializedName("status") val status: String,
    @SerializedName("error") val error: String? = null
)

/**
 * Request to accept an invitation.
 */
data class CircleAcceptRequest(
    @SerializedName("invitation_id") val invitationId: String
)

/**
 * Response from POST /v1/circle/accept.
 */
data class CircleAcceptResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("error") val error: String? = null
)

/**
 * Request to revoke a circle membership.
 */
data class CircleRevokeRequest(
    @SerializedName("invitation_id") val invitationId: String
)

/**
 * Response from DELETE /v1/circle/{id}.
 */
data class CircleRevokeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("error") val error: String? = null
)

/**
 * Request for a trusted circle alert.
 *
 * # Security
 * The alert payload is REDACTED — it contains ONLY category + reason + who asked for help.
 * NEVER include full phone numbers, message bodies, or account details.
 */
data class CircleAlertRequest(
    @SerializedName("invitation_id") val invitationId: String,
    @SerializedName("category") val category: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("asked_by") val askedBy: String
)

/**
 * Response from POST /v1/circle/alert.
 */
data class CircleAlertResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("error") val error: String? = null
)

/**
 * Represents the user's subscription tier.
 */
data class Entitlement(
    @SerializedName("is_entitled") val isEntitled: Boolean,
    @SerializedName("tier") val tier: String,
    @SerializedName("scan_quota") val scanQuota: Int = 0,
    @SerializedName("scan_used") val scanUsed: Int = 0
) {
    /**
     * Whether the user has exceeded their monthly scan quota.
     */
    val isQuotaExceeded: Boolean
        get() = scanUsed >= scanQuota
}
