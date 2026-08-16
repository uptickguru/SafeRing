package online.db1k.safering.android.ui.check

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.service.OnDeviceScamChecker
import online.db1k.safering.android.service.ScamCheckResult
import online.db1k.safering.android.service.ScamVerdict

@Composable
fun PasteCheckScreen(
    trustedName: String,
    onHelp: () -> Unit,
    onCall: () -> Unit,
    onClose: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScamCheckResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Check this", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste a text, email, or anything someone sent you. It stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            placeholder = { Text("Paste here") }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { result = OnDeviceScamChecker.check(text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Check this") }

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
