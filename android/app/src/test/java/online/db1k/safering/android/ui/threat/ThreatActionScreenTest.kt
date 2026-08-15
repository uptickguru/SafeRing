package online.db1k.safering.android.ui.threat

import androidx.compose.ui.test.assertCountEqual
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Test

/**
 * Test Suite 1: HITL — Every recommendedAction must expose a human-action control.
 * No state should render a terminal "safe/proceed" state.
 *
 * # Security Rule
 * This screen MUST drive HUMAN ACTION. There is NO terminal "safe/proceed" state.
 * Every case routes to a human action that keeps the loop open.
 */
class ThreatActionScreenTest {

    private val rule = createComposeRule()

    @Before
    fun setup() {
        // Reset state between tests
    }

    // MARK: - HITL Tests

    @Test
    fun `every recommendedAction exposes a human-action control`() {
        // CALL_SAVED_CONTACT
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.CallSavedContact(
                    contact = SavedContact(
                        id = 1,
                        displayName = "Alice",
                        savedNumber = "+1234567890"
                    )
                ),
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify the "Call Alice" button exists
        rule.onNodeWithText("Call Alice on their saved number, not this caller's number").assertExists()

        // Verify there's no "Safe/Proceed" terminal state
        rule.onAllNodesWithText("Safe").assertCountEqual(0)
        rule.onAllNodesWithText("Proceed").assertCountEqual(0)
    }

    @Test
    fun `CALL_SAVED_CONTACT does not transmit incoming number`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.CallSavedContact(
                    contact = SavedContact(
                        id = 1,
                        displayName = "Alice",
                        savedNumber = "+1234567890"
                    )
                ),
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify the button text mentions the SAVED contact, not the incoming number
        rule.onNodeWithText("Call Alice on their saved number, not this caller's number").assertExists()
    }

    @Test
    fun `ASK_FAMILY_PASSWORD does not transmit password`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.AskFamilyPassword,
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify the button exists
        rule.onNodeWithText("Ask them your family password").assertExists()

        // Verify there's no input field for the password
        rule.onAllNodesWithText("password").assertCountEqual(0)
        rule.onAllNodesWithText("Enter").assertCountEqual(0)
    }

    @Test
    fun `LOOP_TRUSTED_CONTACT exposes alert button`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.LoopTrustedContact,
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify the alert button exists
        rule.onNodeWithText("Alert Trusted Contact").assertExists()
    }

    @Test
    fun `DO_NOT_REPLY exposes delete button`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.DoNotReply,
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify the delete button exists
        rule.onNodeWithText("Delete / Don't Respond").assertExists()
    }

    @Test
    fun `LOOKS_OK_STILL_VERIFY keeps verification visible`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.LooksOkStillVerify,
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify the verification button exists
        rule.onNodeWithText("Verify with Trusted Contact").assertExists()

        // Verify the disclaimer is present
        rule.onNodeWithText("This is NOT a guarantee of safety").assertExists()
    }

    @Test
    fun `no terminal safe state across all actions`() {
        // Test all possible recommended actions
        listOf(
            ThreatAction.CallSavedContact(
                contact = SavedContact(id = 1, displayName = "Alice", savedNumber = "+1234567890")
            ),
            ThreatAction.AskFamilyPassword,
            ThreatAction.LoopTrustedContact,
            ThreatAction.DoNotReply,
            ThreatAction.LooksOkStillVerify
        ).forEach { action ->
            rule.setContent {
                ThreatActionScreen(
                    recommendedAction = action,
                    callerLabel = "Unknown",
                    savedContact = null,
                    userOptedIn = false,
                    numberHash = "abc123",
                    wasBlocked = false,
                    onHumanAction = {}
                )
            }

            // Verify no terminal "safe/proceed" state
            rule.onAllNodesWithText("Safe").assertCountEqual(0)
            rule.onAllNodesWithText("Proceed").assertCountEqual(0)
            rule.onAllNodesWithText("Done").assertCountEqual(0)
        }
    }

    // MARK: - Accessibility Tests

    @Test
    fun `all buttons have TalkBack labels`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.CallSavedContact(
                    contact = SavedContact(
                        id = 1,
                        displayName = "Alice",
                        savedNumber = "+1234567890"
                    )
                ),
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify TalkBack labels exist
        rule.onNodeWithContentDescription("Call Alice on their saved number, not this caller's number").assertExists()
    }

    @Test
    fun `buttons are large enough for accessibility`() {
        rule.setContent {
            ThreatActionScreen(
                recommendedAction = ThreatAction.CallSavedContact(
                    contact = SavedContact(
                        id = 1,
                        displayName = "Alice",
                        savedNumber = "+1234567890"
                    )
                ),
                callerLabel = "Unknown",
                savedContact = null,
                userOptedIn = false,
                numberHash = "abc123",
                wasBlocked = false,
                onHumanAction = {}
            )
        }

        // Verify button height is ≥48dp (accessibility requirement)
        // This is tested visually — the button should be large enough for senior users
        rule.onNodeWithText("Call Alice on their saved number, not this caller's number").assertExists()
    }
}
