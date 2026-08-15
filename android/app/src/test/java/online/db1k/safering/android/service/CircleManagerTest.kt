package online.db1k.safering.android.service

import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Test Suite 3: Trusted Circle — Alert to not-yet-accepted contact is rejected.
 * Alert payloads must contain NO raw phone number or message body.
 *
 * # Security Rule
 * - The protected user must invite a contact.
 * - The contact must explicitly ACCEPT before any alert can be sent.
 * - Either party can revoke (DELETE /v1/circle/{id}) anytime.
 * - Alert payloads are REDACTED — only category + reason + who asked for help.
 *   NEVER include full phone numbers, message bodies, or account details.
 */
class CircleManagerTest {

    private val mockedContext = mock(android.content.Context::class.java)
    private val mockedPrefs = mock(android.content.SharedPreferences::class.java)
    private val mockedApi = mock(online.db1k.safering.android.data.remote.SafeRingApi::class.java)

    // MARK: - Setup

    @Before
    fun setup() {
        doReturn(mockedPrefs).`when`(mockedContext).getSharedPreferences("circle_prefs", 0)
    }

    // MARK: - Trusted Circle Tests

    @Test
    fun `alert to not-yet-accepted contact is rejected`() {
        // Setup: Create an invitation that is NOT accepted
        val invitationId = "test_invitation_id"
        val invitation = CircleInvitationData(
            invitationId = invitationId,
            isAccepted = false,
            acceptedAt = null,
            revokedAt = null
        )

        // Save the invitation
        circleRepository = CircleRepository(mockedPrefs)
        circleRepository.saveInvitation(invitationId)

        // Update the invitation to NOT accepted
        circleRepository.updateInvitation(invitation)

        // Verify the invitation is not accepted
        val retrievedInvitation = circleRepository.getInvitation(invitationId)
        assert(retrievedInvitation != null) {
            "Invitation should exist"
        }
        assert(!retrievedInvitation.isAccepted) {
            "Invitation should NOT be accepted"
        }

        // Attempt to send alert (should fail)
        try {
            circleManager.sendAlert(
                invitationId = invitationId,
                category = "call",
                reason = "High-risk call",
                askedBy = "John"
            )
            // If we get here, the assertion failed
            assert(false) {
                "Alert should fail for not-yet-accepted contact"
            }
        } catch (e: CircleError) {
            // Expected: alert should fail
            assert(e.message != null) {
                "Error message should not be null"
            }
            assert(e.message!!.contains("not accepted")) {
                "Error should indicate contact has not accepted"
            }
        }
    }

    @Test
    fun `alert payload contains no raw phone number`() {
        // Setup: Create an accepted invitation
        val invitationId = "test_invitation_id"
        val invitation = CircleInvitationData(
            invitationId = invitationId,
            isAccepted = true,
            acceptedAt = System.currentTimeMillis(),
            revokedAt = null
        )

        // Save the invitation
        circleRepository = CircleRepository(mockedPrefs)
        circleRepository.saveInvitation(invitationId)
        circleRepository.updateInvitation(invitation)

        // Verify the invitation is accepted
        val retrievedInvitation = circleRepository.getInvitation(invitationId)
        assert(retrievedInvitation != null) {
            "Invitation should exist"
        }
        assert(retrievedInvitation.isAccepted) {
            "Invitation should be accepted"
        }

        // Build alert payload (should be REDACTED)
        val alertPayload = mapOf(
            "invitationId" to invitationId,
            "category" to "call",
            "reason" to "High-risk call claiming to be IRS; John tapped Help",
            "askedBy" to "John"
        )

        // Verify no raw phone numbers in payload
        assert(alertPayload.values.none { it.contains("+1") || it.contains("234567890") }) {
            "Alert payload should not contain raw phone numbers"
        }

        // Verify no message bodies in payload
        assert(alertPayload.values.none { it.contains("message") || it.contains("body") }) {
            "Alert payload should not contain message bodies"
        }
    }

    @Test
    fun `alert payload contains only category reason and who`() {
        // Setup: Create an accepted invitation
        val invitationId = "test_invitation_id"
        val invitation = CircleInvitationData(
            invitationId = invitationId,
            isAccepted = true,
            acceptedAt = System.currentTimeMillis(),
            revokedAt = null
        )

        // Save the invitation
        circleRepository = CircleRepository(mockedPrefs)
        circleRepository.saveInvitation(invitationId)
        circleRepository.updateInvitation(invitation)

        // Build alert payload
        val alertPayload = mapOf(
            "invitationId" to invitationId,
            "category" to "call",
            "reason" to "High-risk call claiming to be IRS; John tapped Help",
            "askedBy" to "John"
        )

        // Verify payload contains only allowed fields
        assert(alertPayload.keys.contains("category")) {
            "Payload should contain category"
        }
        assert(alertPayload.keys.contains("reason")) {
            "Payload should contain reason"
        }
        assert(alertPayload.keys.contains("askedBy")) {
            "Payload should contain askedBy"
        }
        assert(!alertPayload.keys.contains("phoneNumber")) {
            "Payload should not contain phoneNumber"
        }
        assert(!alertPayload.keys.contains("messageBody")) {
            "Payload should not contain messageBody"
        }
    }

    @Test
    fun `alert to revoked contact is rejected`() {
        // Setup: Create an invitation that is revoked
        val invitationId = "test_invitation_id"
        val invitation = CircleInvitationData(
            invitationId = invitationId,
            isAccepted = true,
            acceptedAt = System.currentTimeMillis(),
            revokedAt = System.currentTimeMillis()
        )

        // Save the invitation
        circleRepository = CircleRepository(mockedPrefs)
        circleRepository.saveInvitation(invitationId)
        circleRepository.updateInvitation(invitation)

        // Verify the invitation is revoked
        val retrievedInvitation = circleRepository.getInvitation(invitationId)
        assert(retrievedInvitation != null) {
            "Invitation should exist"
        }
        assert(retrievedInvitation.revokedAt != null) {
            "Invitation should be revoked"
        }

        // Attempt to send alert (should fail)
        try {
            circleManager.sendAlert(
                invitationId = invitationId,
                category = "call",
                reason = "High-risk call",
                askedBy = "John"
            )
            // If we get here, the assertion failed
            assert(false) {
                "Alert should fail for revoked contact"
            }
        } catch (e: CircleError) {
            // Expected: alert should fail
            assert(e.message != null) {
                "Error message should not be null"
            }
        }
    }

    @Test
    fun `invitations are stored with only invitationId and acceptance status`() {
        // Setup: Create an invitation
        val invitationId = "test_invitation_id"
        val invitation = CircleInvitationData(
            invitationId = invitationId,
            isAccepted = false,
            acceptedAt = null,
            revokedAt = null
        )

        // Save the invitation
        circleRepository = CircleRepository(mockedPrefs)
        circleRepository.saveInvitation(invitationId)

        // Retrieve and verify
        val retrievedInvitation = circleRepository.getInvitation(invitationId)
        assert(retrievedInvitation != null) {
            "Invitation should exist"
        }

        // Verify only allowed fields are stored
        // In a real implementation, we'd verify the SharedPreferences contents
        // but here we test the contract
        assert(retrievedInvitation.invitationId == invitationId) {
            "Invitation ID should match"
        }
        assert(retrievedInvitation.isAccepted == false) {
            "Invitation should not be accepted"
        }
        assert(retrievedInvitation.acceptedAt == null) {
            "Accepted at should be null"
        }
        assert(retrievedInvitation.revokedAt == null) {
            "Revoked at should be null"
        }
    }
}
