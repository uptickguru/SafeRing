package online.db1k.safering.android.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.service.HelpReason
import online.db1k.safering.android.service.HelpSignaler
import online.db1k.safering.android.service.HouseholdStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenCheck: () -> Unit = {}
) {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    val signaler = remember { HelpSignaler(context, household) }
    var showPassword by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val refresh = tick

    val trusted = household.trustedContactName.ifBlank { "my person" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SafeRing", modifier = Modifier.testTag("home_title"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            if (household.isConfigured) "Tripwire ready" else "Needs setup",
            modifier = Modifier.testTag("home_subtitle"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Help texts $trusted at ${household.displayNumber}. Unknown callers are silenced when SafeRing is the screening app. Contacts still ring.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (household.lastHelpAt > 0) {
            val stamp = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(household.lastHelpAt))
            Text("Last alert $stamp · ${household.helpCount} sent", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { signaler.send(HelpReason.MONEY); tick++ },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(72.dp)
        ) {
            Text("Someone wants money — get my person", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { signaler.send(HelpReason.HELP); tick++ },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("I'm not sure — ping them anyway") }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { signaler.callSaved() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Call $trusted for real")
        }
        OutlinedButton(onClick = onOpenCheck, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Check a text or email")
        }
        OutlinedButton(onClick = { showPassword = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Ask them the family password")
        }

        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Phone & carrier", fontWeight = FontWeight.Bold)
                CheckLine("Call screening role", household.callScreeningConfirmed)
                CheckLine("Carrier scam block", household.carrierProtectionConfirmed)
                CheckLine("Silence unknown callers", household.silenceUnknownConfirmed)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("If they claim to be family", fontWeight = FontWeight.Bold)
                Text(
                    "Ask for the family password. Hang up if they stall. Then call $trusted on the saved number — never the incoming caller.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showPassword) {
        AlertDialog(
            onDismissRequest = { showPassword = false },
            confirmButton = { TextButton(onClick = { showPassword = false }) { Text("OK") } },
            title = { Text("Ask them the family password") },
            text = {
                Text(
                    if (household.hasFamilyPassword)
                        "Ask them first. Do not say it unless you are sure. Yours is: ${household.familyPassword}"
                    else
                        "No family password is saved. Add one in Settings."
                )
            }
        )
    }
}

@Composable
private fun CheckLine(title: String, on: Boolean) {
    Text(if (on) "✓ $title" else "○ $title")
}
