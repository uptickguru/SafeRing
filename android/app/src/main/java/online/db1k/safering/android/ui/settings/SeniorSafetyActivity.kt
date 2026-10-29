package online.db1k.safering.android.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.ui.theme.SafeRingTheme

class SeniorSafetyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeRingTheme {
                SeniorSafetyScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorSafetyScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fall check-in") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Close") } }
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
            Text(
                "HumanAnswer is for scam calls and getting your person. A fall check-in uses the tools already on the phone and watch. It does not replace them.",
                style = MaterialTheme.typography.bodyLarge
            )
            Tip("Watch fall detection", "If there is a watch: Settings, SOS, Fall Detection. HumanAnswer does not replace this.")
            Tip("Crash detection", "Practice the side-button emergency call with the family. Medical information stays in the phone's own Health or Safety settings.")
            Tip("Android medical info", "Settings, Safety and emergency, Medical information and emergency contacts.")
            Tip("HELP", "The home screen HELP button texts the trusted person if the phone can be reached.")
            Tip("Home basics", "Night lights, clear rugs, phone charging by the bed.")
            Text(
                "This phone does not claim to detect a fall by itself.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Tip(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
