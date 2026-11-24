package online.db1k.safering.android.ui.threat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ThreatActionScreen — the most critical screen in GMG Shield.
 *
 * # Critical Safety Rule (M4)
 * This screen MUST drive HUMAN ACTION. It MUST NEVER present a "you're safe,
 * proceed" terminal state that closes the loop on the AI's verdict.
 * There is NO screen state that ends at "safe."
 *
 * # Design Principles
 * - Every enum case routes to a human action
 * - The dial action targets the SAVED number only (never the incoming/suspect number)
 * - Large buttons (≥48dp) for senior-friendly touch targets
 * - High contrast colors for risk indicators
 * - TalkBack labels on all interactive elements
 * - No terminal "safe/proceed" state
 *
 * # Accessibility (M10)
 * - All buttons ≥48dp for touch targets
 * - TalkBack labels (semantics contentDescription) on all interactive elements
 * - High contrast colors for risk indicators
 * - Dynamic Type compatible via sp units
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = threatColor(recommendedAction).copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header — risk indicator
            ThreatHeader(recommendedAction, callerLabel)

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button — always visible, always drives human action
            ThreatActionButton(
                recommendedAction = recommendedAction,
                savedContact = savedContact,
                userOptedIn = userOptedIn,
                onHumanAction = onHumanAction
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Guidance section
            ThreatGuidance(recommendedAction)

            Spacer(modifier = Modifier.height(16.dp))

            // Footer — always actionable, never terminal
            ThreatFooter(onHumanAction, recommendedAction)
        }
    }
}

// MARK: - Header Section

@Composable
private fun ThreatHeader(action: ThreatAction, callerLabel: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Risk Icon
        Icon(
            imageVector = threatIcon(action),
            contentDescription = threatContentDescription(action),
            modifier = Modifier.size(64.dp),
            tint = threatColor(action)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Caller Label
        Text(
            text = callerLabel,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Risk Description
        Text(
            text = threatDescription(action),
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// MARK: - Action Button Section

/**
 * Routes to the correct action button based on the recommended action.
 * Every case drives a human action — no terminal "safe" state.
 */
@Composable
private fun ThreatActionButton(
    recommendedAction: ThreatAction,
    savedContact: SavedContact?,
    userOptedIn: Boolean,
    onHumanAction: (ThreatAction) -> Unit
) {
    when (recommendedAction) {
        is ThreatAction.CallSavedContact -> {
            CallSavedContactButton(
                contact = recommendedAction.contact,
                onAction = { onHumanAction(recommendedAction) }
            )
        }
        is ThreatAction.AskFamilyPassword -> {
            AskFamilyPasswordButton(onAction = { onHumanAction(recommendedAction) })
        }
        is ThreatAction.LoopTrustedContact -> {
            LoopTrustedContactButton(onAction = { onHumanAction(recommendedAction) })
        }
        is ThreatAction.DoNotReply -> {
            DoNotReplyButton(onAction = { onHumanAction(recommendedAction) })
        }
        is ThreatAction.LooksOkStillVerify -> {
            LooksOkStillVerifyButton(onAction = { onHumanAction(recommendedAction) })
        }
    }
}

/**
 * CALL_SAVED_CONTACT: One-tap dials the SAVED number.
 * The dial action targets the SAVED number only, never the incoming/suspect number.
 */
@Composable
private fun CallSavedContactButton(contact: SavedContact, onAction: () -> Unit) {
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "Call ${contact.displayName} on their saved number, not this caller's number"
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
    ) {
        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Call ${contact.displayName} on their saved number",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Don't call this number back. Call ${contact.displayName} on their real number.",
        color = Color.Gray,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * ASK_FAMILY_PASSWORD: Prompt to ask them your family password.
 * No field that transmits it — see M6.
 */
@Composable
private fun AskFamilyPasswordButton(onAction: () -> Unit) {
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "Ask them your family password. No information is transmitted."
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Ask them your family password",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "This will NOT transmit any information to the caller.",
        color = Color.Gray,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * LOOP_TRUSTED_CONTACT: Button to alert the trusted contact (M5).
 */
@Composable
private fun LoopTrustedContactButton(onAction: () -> Unit) {
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "Alert trusted contact about this suspicious call"
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
    ) {
        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Alert Trusted Contact",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/**
 * DO_NOT_REPLY: Clear "Delete / don't respond" guidance.
 */
@Composable
private fun DoNotReplyButton(onAction: () -> Unit) {
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "Delete this message and don't respond"
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Delete / Don't Respond",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/**
 * LOOKS_OK_STILL_VERIFY: Explicitly states this is NOT a guarantee.
 * Keeps "Verify with trusted contact" visible.
 */
@Composable
private fun LooksOkStillVerifyButton(onAction: () -> Unit) {
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "Verify with trusted contact. This is not a guarantee of safety."
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
    ) {
        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Verify with Trusted Contact",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "⚠️ This is NOT a guarantee of safety. Always verify.",
        color = Color(0xFFE53935),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// MARK: - Guidance Section

@Composable
private fun ThreatGuidance(action: ThreatAction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What should I do?",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = guidanceText(action),
                color = Color.DarkGray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// MARK: - Footer Section

@Composable
private fun ThreatFooter(onHumanAction: (ThreatAction) -> Unit, action: ThreatAction) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Report button — always available, never terminal
        OutlinedButton(
            onClick = { onHumanAction(action) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Report this number as a scam" }
        ) {
            Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Report as Scam", fontWeight = FontWeight.Bold)
        }
    }
}

// MARK: - Helper Functions (pure functions, no state)

private fun threatIcon(action: ThreatAction): ImageVector = when (action) {
    is ThreatAction.CallSavedContact -> Icons.Default.Phone
    is ThreatAction.AskFamilyPassword -> Icons.Default.Lock
    is ThreatAction.LoopTrustedContact -> Icons.Default.PersonAdd
    is ThreatAction.DoNotReply -> Icons.Default.Warning
    is ThreatAction.LooksOkStillVerify -> Icons.Default.VerifiedUser
}

private fun threatContentDescription(action: ThreatAction): String = when (action) {
    is ThreatAction.CallSavedContact -> "Call saved contact instead of suspicious caller"
    is ThreatAction.AskFamilyPassword -> "Ask for family password"
    is ThreatAction.LoopTrustedContact -> "Alert trusted contact"
    is ThreatAction.DoNotReply -> "Don't respond to suspicious message"
    is ThreatAction.LooksOkStillVerify -> "Verify with trusted contact"
}

private fun threatColor(action: ThreatAction): Color = when (action) {
    is ThreatAction.CallSavedContact -> Color(0xFF4CAF50) // Safe green
    is ThreatAction.AskFamilyPassword -> Color(0xFFFFC107) // Warning yellow
    is ThreatAction.LoopTrustedContact -> Color(0xFFFF9800) // High risk orange
    is ThreatAction.DoNotReply -> Color(0xFFE53935) // Critical red
    is ThreatAction.LooksOkStillVerify -> Color(0xFFFF9800) // High risk orange
}

private fun threatDescription(action: ThreatAction): String = when (action) {
    is ThreatAction.CallSavedContact ->
        "This caller may be impersonating someone you know. Don't call them back."
    is ThreatAction.AskFamilyPassword ->
        "Ask them your family password to verify their identity."
    is ThreatAction.LoopTrustedContact ->
        "This call has been flagged as suspicious. Alert your trusted contact."
    is ThreatAction.DoNotReply ->
        "This message has been flagged as a potential scam. Do not respond."
    is ThreatAction.LooksOkStillVerify ->
        "This call looks okay, but GMG Shield recommends verification."
}

private fun guidanceText(action: ThreatAction): String = when (action) {
    is ThreatAction.CallSavedContact ->
        "Scammers often spoof trusted numbers. Call your contact on the number YOU saved — never the number that called you."
    is ThreatAction.AskFamilyPassword ->
        "Your family password is a secret phrase only your family knows. Ask the caller to say it. If they can't, it's not them. The password is NEVER stored or transmitted."
    is ThreatAction.LoopTrustedContact ->
        "Your trusted contact can help you evaluate this call. They'll receive an alert with the caller's information (no personal details shared)."
    is ThreatAction.DoNotReply ->
        "Scammers use urgency and fear. If something feels off, don't engage. Delete the message and report it."
    is ThreatAction.LooksOkStillVerify ->
        "Even calls that seem safe can be spoofed. A quick check with a trusted contact takes 30 seconds and could save you from a scam."
}
