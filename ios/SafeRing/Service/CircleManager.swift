import Foundation

/// CircleManager — manages the trusted circle feature.
///
/// # Security
/// - Phone numbers are NEVER stored as plaintext. They are hashed with
///   HMAC-SHA256 using a per-install secret key.
/// - Alert payloads are REDACTED — only category + reason + who asked for help.
///   NEVER include full phone numbers, message bodies, or account details.
///
/// # BOTH-PARTY Opt-In
/// - The protected user must invite a contact.
/// - The contact must explicitly ACCEPT before any alert can be sent.
/// - Either party can revoke (DELETE /v1/circle/{id}) anytime.
///
/// # Contact Limit
/// - Free tier: 2 contacts max
/// - Plus tier: higher limit (read from entitlement)
///
/// # Circuit-Breaker
/// - Prominent "Someone's asking me for money — help me check" button
/// - Loops the trusted contact and shows the money-safety checklist
/// - Only fires when the user explicitly taps it
///
final class CircleManager {

    // MARK: - Properties

    private let apiClient: ApiClient
    private let storage: UserDefaults
    private let circleRepository: CircleRepository

    // MARK: - Initializer

    init(
        apiClient: ApiClient,
        storage: UserDefaults = .standard,
        circleRepository: CircleRepository
    ) {
        self.apiClient = apiClient
        self.storage = storage
        self.circleRepository = circleRepository
    }

    // MARK: - Public API

    /// Invites a contact to the trusted circle.
    ///
    /// # Security
    /// The phoneHash is HMAC-SHA256 — NEVER store or send plaintext numbers.
    ///
    /// - Parameters:
    ///   - phoneHash: HMAC-SHA256 hash of the contact's phone number.
    ///   - phonePrefix: The phone prefix (e.g., "+1").
    ///   - displayName: Display name of the contact.
    /// - Returns: CircleInviteResponse with the invitation ID.
    /// - Throws: CircleError if the invitation fails.
    func inviteContact(
        phoneHash: String,
        phonePrefix: String,
        displayName: String
    ) async throws -> CircleInviteResponse {
        let invite = CircleInviteRequest(
            phoneHash: phoneHash,
            phonePrefix: phonePrefix,
            displayName: displayName
        )

        let response = try await apiClient.inviteCircleContact(invite)

        // Cache the invitation locally
        circleRepository.saveInvitation(response.invitationId)

        Logger.shared.info(
            "Invitation sent: \(response.invitationId) status: \(response.status)",
            category: .circle
        )

        return response
    }

    /// Accepts an invitation to the trusted circle.
    ///
    /// - Parameters:
    ///   - invitationId: The invitation ID to accept.
    /// - Returns: CircleAcceptResponse confirming acceptance.
    /// - Throws: CircleError if the acceptance fails.
    func acceptInvitation(invitationId: String) async throws -> CircleAcceptResponse {
        let accept = CircleAcceptRequest(invitationId: invitationId)

        let response = try await apiClient.acceptCircleContact(accept)

        // Update local cache
        if let cached = circleRepository.getInvitation(invitationId) {
            cached.isAccepted = true
            cached.acceptedAt = Date().timeIntervalSince1970
            circleRepository.updateInvitation(cached)
        }

        Logger.shared.info(
            "Invitation accepted: \(invitationId)",
            category: .circle
        )

        return response
    }

    /// Revokes a trusted circle membership.
    ///
    /// Either party can revoke (DELETE /v1/circle/{id}) anytime.
    ///
    /// - Parameter invitationId: The invitation ID to revoke.
    /// - Returns: CircleRevokeResponse confirming revocation.
    /// - Throws: CircleError if the revocation fails.
    func revokeInvitation(invitationId: String) async throws -> CircleRevokeResponse {
        let revoke = CircleRevokeRequest(invitationId: invitationId)

        let response = try await apiClient.revokeCircleContact(revoke)

        // Remove from local cache
        circleRepository.deleteInvitation(invitationId)

        Logger.shared.info(
            "Invitation revoked: \(invitationId)",
            category: .circle
        )

        return response
    }

    /// Sends a REDACTED trusted circle alert.
    ///
    /// # Security
    /// The alert payload is REDACTED — it contains ONLY category + reason + who asked for help.
    /// NEVER include full phone numbers, message bodies, or account details.
    ///
    /// - Parameters:
    ///   - invitationId: The invitation ID.
    ///   - category: The category of the threat (e.g., "call", "sms", "money").
    ///   - reason: A short, redacted reason (e.g., "High-risk call claiming to be IRS; John tapped Help").
    ///   - askedBy: Who asked for help (display name).
    /// - Returns: CircleAlertResponse confirming delivery.
    /// - Throws: CircleError if the alert fails.
    func sendAlert(
        invitationId: String,
        category: String,
        reason: String,
        askedBy: String
    ) async throws -> CircleAlertResponse {
        // Verify the contact has accepted
        guard let invitation = circleRepository.getInvitation(invitationId),
              invitation.isAccepted else {
            throw CircleError.alertFailed("Contact has not accepted the invitation")
        }

        // Build REDACTED alert payload
        // NEVER include full phone numbers, message bodies, or account details
        let alert = CircleAlertRequest(
            invitationId: invitationId,
            category: category,
            reason: reason,
            askedBy: askedBy
        )

        let response = try await apiClient.sendCircleAlert(alert)

        Logger.shared.info(
            "Circle alert sent: \(invitationId) category: \(category) reason: \(reason)",
            category: .circle
        )

        return response
    }

    /// Checks if the user has the money-safety checklist visible.
    ///
    /// This is the circuit-breaker: a prominent "Someone's asking me for money —
    /// help me check" button that loops the trusted contact and shows the
    /// money-safety checklist before the user acts.
    ///
    /// - Returns: Bool indicating whether the checklist is shown.
    func shouldShowMoneySafetyChecklist() -> Bool {
        // This is triggered when the user taps the prominent button
        // and is meant to show the checklist before taking action
        return true
    }

    /// Gets the circle contacts for the user.
    ///
    /// - Returns: Array of CircleContact.
    func getCircleContacts() -> [CircleContact] {
        return circleRepository.getCircleContacts()
    }

    /// Gets the number of accepted contacts.
    ///
    /// - Returns: Int count of accepted contacts.
    func getAcceptedContactCount() -> Int {
        return circleRepository.getAcceptedContactCount()
    }

    /// Checks if the user is entitled (has a valid subscription).
    ///
    /// - Returns: Bool indicating entitlement status.
    func isEntitled() async throws -> Bool {
        let entitlement = try await apiClient.getEntitlement()
        return entitlement.isEntitled
    }

    /// Checks if the user is on the Plus tier.
    ///
    /// - Returns: Bool indicating Plus tier status.
    func isPlusTier() async throws -> Bool {
        let entitled = try await isEntitled()
        return entitled
    }

    /// Gets the contact limit for the user's tier.
    ///
    /// - Returns: Int max number of contacts.
    func getContactLimit() async throws -> Int {
        let plus = try await isPlusTier()
        return plus ? 5 : 2 // Free tier: 2, Plus tier: 5
    }
}

// MARK: - CircleRepository

/// Repository for managing circle invitations locally.
final class CircleRepository {

    private let storage: UserDefaults

    init(storage: UserDefaults = .standard) {
        self.storage = storage
    }

    /// Saves an invitation to local storage.
    func saveInvitation(_ invitationId: String) {
        let data = CircleInvitationData(invitationId: invitationId)
        storage.set(data, forKey: "circle_invitation_\(invitationId)")
    }

    /// Gets an invitation from local storage.
    func getInvitation(_ invitationId: String) -> CircleInvitationData? {
        return storage.object(forKey: "circle_invitation_\(invitationId)") as? CircleInvitationData
    }

    /// Updates an invitation in local storage.
    func updateInvInvitationData {
        storage.set(invitation, forKey: "circle_invitation_\(invitation.invitationId)")
    }

    /// Deletes an invitation from local storage.
    func deleteInvitation(_ invitationId: String) {
        storage.removeObject(forKey: "circle_invitation_\(invitationId)")
    }

    /// Gets all circle contacts.
    ///
    /// - Returns: Array of CircleContact.
    func getCircleContacts() -> [CircleContact] {
        // Parse all stored invitations
        let allInvitations = storage.allObjects
        return allInvitations.compactMap { $0 as? CircleInvitationData }
    }

    /// Gets the number of accepted contacts.
    ///
    /// - Returns: Int count of accepted contacts.
    func getAcceptedContactCount() -> Int {
        return getCircleContacts().filter { $0.isAccepted }.count
    }
}

// MARK: - Supporting Models

/// Data structure for storing circle invitations locally.
struct CircleInvitationData: Codable {
    let invitationId: String
    let isAccepted: Bool
    let acceptedAt: TimeInterval?
    let revokedAt: TimeInterval?
}
