package online.db1k.safering.android.ui.check

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.service.OnDeviceScamChecker
import online.db1k.safering.android.service.ScamCheckResult
import online.db1k.safering.android.service.ScamVerdict
import online.db1k.safering.android.service.SmsIntake
import online.db1k.safering.android.ui.theme.Ivory
import online.db1k.safering.android.util.PhoneNumberUtils

@Composable
fun PasteCheckScreen(
    trustedName: String,
    initialText: String = "",
    initialSender: String = "",
    onHelp: () -> Unit,
    onCall: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var sender by remember { mutableStateOf(initialSender) }
    var result by remember { mutableStateOf<ScamCheckResult?>(null) }
    var foundPhones by remember { mutableStateOf<List<String>>(emptyList()) }

    fun runCheck() {
        val r = OnDeviceScamChecker.check(text)
        result = r
        val extracted = PhoneNumberUtils.extractPhones(text)
        foundPhones = extracted
        if (sender.isBlank() && extracted.isNotEmpty()) {
            sender = PhoneNumberUtils.pretty(extracted.first())
        }
        SmsIntake.recordCheck(
            context = context,
            body = text,
            senderRaw = sender.ifBlank { extracted.firstOrNull() },
            result = r,
            storeBody = false
        )
    }

    LaunchedEffect(initialText) {
        if (initialText.isNotBlank() && result == null) {
            text = initialText
            if (initialSender.isNotBlank()) sender = initialSender
            runCheck()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Check this", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste a text or email. It stays on this phone. Add who it was from if you know.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = sender,
            onValueChange = { sender = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("check_from"),
            label = { Text("From / return number (optional)") },
            placeholder = { Text("e.g. (727) 555-1212") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("check_body"),
            placeholder = { Text("Paste here") }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { runCheck() },
            enabled = text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("check_run")
        ) { Text("Check this") }

        if (foundPhones.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Numbers found in the message", fontWeight = FontWeight.SemiBold)
            foundPhones.forEach { p ->
                Text("· ${PhoneNumberUtils.pretty(p)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Never call a number from a suspicious text — call your person on the number you saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "This is not a guarantee. When money is involved, get your person.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    r.reasons.forEach { Text("• $it") }
                    if (r.urls.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Links", fontWeight = FontWeight.SemiBold)
                        r.urls.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (r.verdict != ScamVerdict.LOOKS_OKAY) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                            Text("Get ${trustedName.ifBlank { "my person" }}")
                        }
                        OutlinedButton(onClick = onCall, modifier = Modifier.fillMaxWidth()) {
                            Text("Call them for real")
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                            Text("Still verify with my person")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}
