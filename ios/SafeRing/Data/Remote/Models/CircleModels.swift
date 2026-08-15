import Foundation

// MARK: - Circle Models

/// Represents a trusted circle contact.
///
/// # Security
/// Phone numbers are NEVER stored as plaintext. They are hashed with
/// HMAC-SHA256 using a per-install secret key. Only the hash is sent to
/// the server and displayed to the user.
struct CircleContact: Identifiable, Codable, Equatable {
    /// Unique identifier for this circle membership.
    let id: String

    /// The protected user's name (the one who invited them).
    let inviterName: String

    /// The protected user's phone hash (HMAC-SHA256).
    /// NEVER store or display the plaintext number.
    let inviterPhoneHash: String

    /// The protected user's phone prefix (e.g., "+1") for display only.
    let inviterPhonePrefix: String

    /// Display name of the protected user.
    let inviterDisplayName: String

    /// Whether the contact has accepted the invitation.
    let isAccepted: Bool

    /// When the invitation was created (Unix timestamp).
    let createdAt: TimeInterval

    /// When the invitation was accepted (Unix timestamp, if accepted).
    let acceptedAt: TimeInterval?

    /// When the invitation was revoked (Unix timestamp, if revoked).
    let revokedAt: TimeInterval?

    enum CodingKeys: String, CodingKey {
        case id
        case inviterName
        case inviterPhoneHash = "inviter_phone_hash"
        case inviterPhonePrefix = "inviter_phone_prefix"
        case inviterDisplayName = "inviter_display_name"
        case isAccepted = "is_accepted"
        case createdAt = "created_at"
        case acceptedAt = "accepted_at"
        case revokedAt = "revoked_at"
    }
}

/// Request to invite a contact to the trusted circle.
struct CircleInviteRequest: Codable {
    /// The phone hash of the contact to invite (HMAC-SHA256).
    /// NEVER store or send the plaintext number.
    let phoneHash: String

    /// The phone prefix of the contact (e.g., "+1").
    let phonePrefix: String

    /// Display name of the contact.
    let displayName: String
}

/// Response from POST /v1/circle/invite.
struct CircleInviteResponse: Codable {
    /// The invitation ID.
    let invitationId: String

    /// Status of the invitation.
    let status: String

    /// Error message if the invitation failed.
    let error: String?
}

/// Request to accept an invitation.
struct CircleAcceptRequest: Codable {
    /// The invitation ID to accept.
    let invitationId: String
}

/// Response from POST /v1/circle/accept.
struct CircleAcceptResponse: Codable {
    /// Whether the acceptance was successful.
    let success: Bool

    /// Error message if the acceptance failed.
    let error: String?
}

/// Request to revoke a circle membership.
struct CircleRevokeRequest: Codable {
    /// The invitation ID to revoke.
    let invitationId: String
}

/// Response from DELETE /v1/circle/{id}.
struct CircleRevokeResponse: Codable {
    /// Whether the revocation was successful.
    let success: Bool

    /// Error message if the revocation failed.
    let error: String?
}

/// Request for a trusted circle alert.
///
/// # Security
/// The alert payload is REDACTED — it contains ONLY category + reason + who asked for help.
/// NEVER include full phone numbers, message bodies, or account details.
struct CircleAlertRequest: Codable {
    /// The invitation ID.
    let invitationId: String

    /// The category of the threat (e.g., "call", "sms", "money").
    let category: String

    /// A short, redacted reason (e.g., "High-risk call claiming to be IRS; John tapped Help").
    /// NEVER include full phone numbers, message bodies, or account details.
    let reason: String

    /// Who asked for help (display name).
    let askedBy: String
}

/// Response from POST /v1/circle/alert.
struct CircleAlertResponse: Codable {
    /// Whether the alert was sent successfully.
    let success: Bool

    /// Error message if the alert failed.
    let error: String?
}

// MARK: - Entitlement Models

/// Represents the user's subscription tier.
struct Entitlement: Codable {
    /// Whether the user is entitled (has a valid subscription).
    let isEntitled: Bool

    /// The subscription tier ("free" or "plus").
    let tier: String

    /// Scan quota for the current month (e.g., 10 for free, 100 for plus).
    let scanQuota: Int

    /// Number of scans used this month.
    let scanUsed: Int

    /// Whether the user has exceeded their monthly scan quota.
    var isQuotaExceeded: Bool {
        return scanUsed >= scanQuota
    }
}

// MARK: - Errors

enum CircleError: LocalizedError {
    case invitationFailed(String)
    case acceptanceFailed(String)
    case revocationFailed(String)
    case alertFailed(String)
    case contactLimitReached
    case notEntitled

    var errorDescription: String? {
        switch self {
        case .invitationFailed(let error):
            return "Invitation failed: \(error)"
        case .acceptanceFailed(let error):
            return "Acceptance failed: \(error)"
        case .revocationFailed(let error):
            return "Revocation failed: \(error)"
        case .alertFailed(let error):
            return "Alert failed: \(error)"
        case .contactLimitReached:
            return "Contact limit reached"
        case .notEntitled:
            return "Not entitled"
        }
    }
}
