package online.db1k.safering.android.ui.lessons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LessonsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("If someone asks for money", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("1. Hang up. Do not call back the number on the screen.")
        Text("2. Ask for your family password. If they do not know it, they are not family.")
        Text("3. Tap Help so your person gets a text.")
        Text("4. Call your person on their saved number or FaceTime — never the incoming caller.")
        Text("5. Banks, the IRS, and Apple will not ask you to buy gift cards.")
        Spacer(Modifier.height(16.dp))
        Text("No login required. This page stays on the phone.", style = MaterialTheme.typography.bodySmall)
    }
}
