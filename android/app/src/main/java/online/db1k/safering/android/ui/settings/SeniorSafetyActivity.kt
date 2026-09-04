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
                title = { Text("Falls & senior safety") },
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
                "GMG Shield is for scams and getting your person. Falls use phone/watch tools Apple and Google already built.",
                style = MaterialTheme.typography.bodyLarge
            )
            Tip("Apple Watch Fall Detection", "If the senior has an Apple Watch: Settings → SOS → Fall Detection. GMG Shield does not replace this.")
            Tip("iPhone Crash Detection / Emergency SOS", "Practice side-button SOS with family. Medical ID in Health app.")
            Tip("Android Medical info", "Settings → Safety & emergency → Medical information + emergency contacts.")
            Tip("Manual HELP", "Home screen giant HELP texts your person if you can reach the phone.")
            Tip("Home basics", "Night lights, clear rugs, phone charging by the bed — high impact, no app required.")
            Text(
                "We do not claim phone-only AI fall detection as a product feature.",
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
