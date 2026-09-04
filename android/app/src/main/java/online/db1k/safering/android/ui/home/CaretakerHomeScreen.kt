package online.db1k.safering.android.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.db1k.safering.android.service.AppModeStore
import online.db1k.safering.android.service.HelpReason
import online.db1k.safering.android.service.HelpSignaler
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.ui.theme.CallSage
import online.db1k.safering.android.ui.theme.HelpBurgundy
import online.db1k.safering.android.ui.theme.Ink
import online.db1k.safering.android.ui.theme.Ivory
import online.db1k.safering.android.ui.theme.Mute
import online.db1k.safering.android.ui.theme.SoftGold
import online.db1k.safering.android.ui.theme.UnsureBronze

@Composable
fun CaretakerHomeScreen() {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    val mode = remember { AppModeStore.get(context) }
    val signaler = remember { HelpSignaler(context, household) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val refresh = tick

    val seniorName = household.ownerDisplayName.ifBlank { "your person" }
    val person = household.trustedContactName.ifBlank { "trusted contact" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GMG SHIELD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = SoftGold)
                Text("Trusted contact", fontSize = 24.sp, color = Ink)
            }
            AssistChip(
                onClick = {},
                label = { Text(if (household.isConfigured) "Linked · $person" else "Needs setup") }
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.7f),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mode.isFree) "Free · Protect + 1 trusted contact" else "Family plan",
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Their phone", fontWeight = FontWeight.SemiBold)
                Text("$seniorName · this phone is Trusted contact mode. Free = Protect + one trusted contact.", color = Mute)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Waiting for alerts", fontWeight = FontWeight.SemiBold)
                Text("When SafeCall is live, unknown attempts show for Approve / Deny. Until then, they use HELP and Protect on their phone.", color = Mute)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { signaler.callSaved(); tick++ },
            colors = ButtonDefaults.buttonColors(containerColor = CallSage),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Call $seniorName") }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { signaler.send(HelpReason.VERIFY); tick++ },
            colors = ButtonDefaults.buttonColors(containerColor = UnsureBronze),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Text check-in") }

        Spacer(Modifier.height(10.dp))
        if (mode.protectIncluded) {
            Button(
                onClick = {
                    context.startActivity(Intent(context, ProtectCallActivity::class.java))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HelpBurgundy),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Protect Call") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Switch to Personal mode in Settings if this is their everyday phone.", color = Mute, fontSize = 13.sp)
    }
}
