package online.db1k.safering.android.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.db1k.safering.android.service.ExceptionalCaptureService
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.service.UnwantedReportService
import online.db1k.safering.android.ui.theme.SafeRingTheme
import online.db1k.safering.android.util.ShieldAnalytics

class InvestigateReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeRingTheme {
                InvestigateReportScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestigateReportScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    val scope = rememberCoroutineScope()
    var sender by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Investigate / Report") },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("Close") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Paste full message + sender. Report = iOS SMS/Call Reporting path. Investigate = exceptional OSINT.")
            OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("Sender number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Full message") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
            )
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status)
            Button(
                onClick = {
                    busy = true
                    status = "Sending report…"
                    scope.launch {
                        val r = UnwantedReportService.submit(context, sender, body, note = note)
                        status = r.fold({ "Reported OK ($it)" }, { "Report failed: ${it.message}" })
                        ShieldAnalytics.event(context, "unwanted_report", mapOf("ok" to r.isSuccess.toString()))
                        busy = false
                    }
                },
                enabled = !busy && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Report full message to GMG") }
            OutlinedButton(
                onClick = {
                    if (!household.exceptionalCaptureEnabled) {
                        status = "Turn on Exceptional investigation in Settings first."
                        return@OutlinedButton
                    }
                    busy = true
                    status = "Investigating…"
                    scope.launch {
                        val r = ExceptionalCaptureService.submit(
                            context = context,
                            senderRaw = sender,
                            messageBody = body,
                            note = note,
                            householdLabel = household.ownerDisplayName.ifBlank { "android-lab" }
                        )
                        status = r.fold({ "Investigate OK case=$it" }, { "Investigate failed: ${it.message}" })
                        ShieldAnalytics.event(context, "exceptional_capture", mapOf("ok" to r.isSuccess.toString()))
                        busy = false
                    }
                },
                enabled = !busy && body.isNotBlank() && sender.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Investigate (exceptional)") }
        }
    }
}
