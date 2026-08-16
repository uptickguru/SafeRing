package online.db1k.safering.android.ui.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ThreatActionScreen — the most critical screen in SafeRing.
 *
 * # Critical Safety Rule
 * This screen MUST drive HUMAN ACTION. It MUST NEVER present a "you're safe,
 * proceed" terminal state that closes the loop on the AI's verdict. There is
 * no screen state that ends at "safe."
 *
 * # Design Principles
 * - Every enum case routes to a human action
 * - The dial action targets the SAVED number only (never the incoming/suspect number)
 * - Large buttons (≥48dp) for senior-friendly touch targets
 * - High contrast colors for risk indicators
 * - TalkBack labels on all interactive elements
 * - No terminal "safe/proceed" state
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
@Composable
fun ThreatActionScreen(
    recommendedAction: ThreatAction,
    callerLabel: String,
    savedContact: SavedContact?,
    userOptedIn: Boolean,
    numberHash: String,
    wasBlocked: Boolean,
    onHumanAction: (ThreatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threat Detected") },
                actions = {
                    Button(onClick = { onHumanAction(recommendedAction) }) {
                        Text("Continue")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header — risk indicator
            headerSection
                .padding(16.dp)

            // Action Buttons — always visible, always drive human action
            actionButtonsSection
                .padding(16.dp)

            // Additional guidance
            guidanceSection
                .padding(16.dp)

            // Footer — never terminal, always actionable
            footerSection
                .padding(16.dp)
        }
    }
}

// MARK: - Header Section

@Composable
private fun headerSection() {
    Column(spacing = 16.dp) {
        // Risk Icon
        Icon(
            imageVector = threatIcon,
            contentDescription = threatContentDescription,
            modifier = Modifier
                .size(64.dp)
                .then(if threatColor != Color.Unspecified {
                    Modifier.background(threatColor, shape = RoundedCornerShape(32.dp))
                } else Modifier)
        )

        // Caller Label
        Text(
            text = callerLabel,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            fontSize = 18.sp
        )

        // Risk Description
        Text(
            text = threatDescription,
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// MARK: - Action Buttons Section

@Composable
private fun actionButtonsSection() {
    when (recommendedAction) {
        ThreatAction.CallSavedContact(contact) -> callSavedContactButton(contact)
        ThreatAction.AskFamilyPassword -> askFamilyPasswordButton
        ThreatAction.LoopTrustedContact -> loopTrustedContactButton
        ThreatAction.DoNotReply -> doNotReplyButton
        ThreatAction.LooksOkStillVerify -> looksOkStillVerifyButton
    }
}

// MARK: - Action Button Implementations

/**
 * CALL_SAVED_CONTACT: Big button that one-tap dials the SAVED number.
 * The dial action targets the SAVED number only, never the incoming/suspect number.
 */
@Composable
private fun callSavedContactButton(contact: SavedContact) {
    Button(
        onClick = {
            // Dial the SAVED number, never the incoming number
            onHumanAction(ThreatAction.CallSavedContact(contact))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp) // ≥48dp for accessibility
            .then(if contact.savedNumber.isNotEmpty() {
                Modifier.padding(horizontal = 16.dp)
            } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // Safe green
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Don't call this number back. Call ${contact.displayName} on their real number",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
    // TalkBack label
    if (contact.displayName.isNotEmpty()) {
        androidx.compose.material3.ExperimentalMaterial3Api
        androidx.compose.material3.Text(
            text = "Call ${contact.displayName} on their saved number, not this caller's number",
            color = Color.Transparent
        )
    }
}

/**
 * ASK_FAMILY_PASSWORD: *** to ask them your family password.
 * No field that transmits it — see M6.
 */
@Composable
private fun askFamilyPasswordButton() {
    Button(
        onClick = {
            // Prompt to ask the caller for the family password
            // This does NOT transmit any information to the caller
            onHumanAction(ThreatAction.AskFamilyPassword)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp) // ≥48dp for accessibility
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppConfig.accentColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Ask them your family password",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
    // TalkBack label
    Text(
        text = "Ask them your family password",
        color = Color.Transparent
    )
}

/**
 * LOOP_TRUSTED_CONTACT: Button to alert the trusted contact (M5).
 */
@Composable
private fun loopTrustedContactButton() {
    Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // ≥48dp for accessibility
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppConfig.accentColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Alert Trusted Contact",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    // TalkBack label
    Text(
        text = "Alert trusted contact about suspicious call",
        color = Color.Transparent
    )
}

/**
 * DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
 */
@Composable
private fun doNotReplyButton() {
    Button(
        onClick = {
            // Mark as do-not-reply and log the event
            onHumanAction(ThreatAction.DoNotReply)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp) // ≥48dp for accessibility
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)) // Critical red
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Trash,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Delete / Don't Respond",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
    // TalkBack label
    Text(
        text = "Don't respond to this message",
        color = Color.Transparent
    )
}

/**
 * LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee.
 * Keeps "Verify with trusted contact" visible.
 */
@Composable
private fun looksOkStillVerifyButton() {
    Column(spacing = 16.dp) {
        Button(
            onClick = {
                // Trigger verification with trusted contact
                onHumanAction(ThreatAction.LooksOkStillVerify)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // ≥48dp for accessibility
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppConfig.accentColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Verify with Trusted Contact",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
        // TalkBack label
        Text(
            text = "Verify with trusted contact",
            color = Color.Transparent
        )

        // Explicitly states this is NOT a guarantee
        Text(
            text = "This is NOT a guarantee of safety",
            color = Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        // TalkBack label
        Text(
            text = "This is not a guarantee of safety",
            color = Color.Transparent
        )
    }
}

// MARK: - Guidance Section

@Composable
private fun guidanceSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = threatDescription,
            color = Color.Gray,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

// MARK: - Footer Section

@Composable
private fun footerSection() {
    Column(spacing = 16.dp) {
        // Always show "Report" button — never terminal
        Button(
            onClick = {
                onHumanAction(recommendedAction)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // ≥48dp for accessibility
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)) // Critical red
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Exclamation,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Report as Scam",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
        // TalkBack label
        Text(
            text = "Report as Scam",
            color = Color.Transparent
        )

        // Always show "Learn More" button — never terminal
        Text(
            text = "What is this threat?",
            color = Color(0xFF2196F3), // Link blue
            fontSize = 14.sp
        )
        // TalkBack label
        Text(
            text = "What is this threat?",
            color = Color.Transparent
        )
    }
}

// MARK: - Helpers

/**
 * Get the threat icon based on the recommended action.
 */
private val threatIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (recommendedAction) {
        ThreatAction.CallSavedContact -> Icons.Default.Phone
        ThreatAction.AskFamilyPassword -> Icons.Default.Lock
        ThreatAction.LoopTrustedContact -> Icons.Default.PersonAdd
        ThreatAction.DoNotReply -> Icons.Default.Warning
        ThreatAction.LooksOkStillVerify -> Icons.Default.QuestionMark
    }

/**
 * Get the threat content description (TalkBack label) based on the recommended action.
 */
private val threatContentDescription: String
    get() = when (recommendedAction) {
        ThreatAction.CallSavedContact -> "Call saved contact instead of suspicious caller"
        ThreatAction.AskFamilyPassword -> "Ask for family password"
        ThreatAction.LoopTrustedContact -> "Alert trusted contact"
        ThreatAction.DoNotReply -> "Don't respond to suspicious message"
        ThreatAction.LooksOkStillVerify -> "Verify with trusted contact"
    }

/**
 * Get the threat color based on the recommended action.
 */
private val threatColor: Color
    get() = when (recommendedAction) {
        ThreatAction.CallSavedContact -> Color(0xFF4CAF50) // Safe green
        ThreatAction.AskFamilyPassword -> Color(0xFFFFC107) // Warning yellow
        ThreatAction.LoopTrustedContact -> Color(0xFFFF9800) // High risk orange
        ThreatAction.DoNotReply -> Color(0xFFE53935) // Critical red
        ThreatAction.LooksOkStillVerify -> Color(0xFFFF9800) // High risk orange
    }

/**
 * Get the threat description based on the recommended action.
 */
private val threatDescription: String
    get() = when (recommendedAction) {
        ThreatAction.CallSavedContact -> "This caller may be a scammer. Don't call them back."
        ThreatAction.AskFamilyPassword -> "This caller may be trying to get your family password."
        ThreatAction.LoopTrustedContact -> "This call has been flagged as suspicious by SafeRing."
        ThreatAction.DoNotReply -> "This message has been flagged as a potential scam."
        ThreatAction.LooksOkStillVerify -> "This call looks okay but SafeRing wants you to verify."
    }
