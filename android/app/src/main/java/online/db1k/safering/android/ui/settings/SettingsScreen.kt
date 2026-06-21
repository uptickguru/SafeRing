package online.db1k.safering.android.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var autoBlock by remember { mutableStateOf(true) }
    var smsBodyStorage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Call Screening Section ──────────────────────────────
        Text(
            text = "Call Screening",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingToggle(
                    title = "Auto-Block Scam Calls",
                    subtitle = "Automatically block calls with high scam confidence",
                    checked = autoBlock,
                    onCheckedChange = { autoBlock = it }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingToggle(
                    title = "Show Scam Alert Notifications",
                    subtitle = "Display notifications for suspected scam calls",
                    checked = true,
                    onCheckedChange = {}
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── SMS Section ─────────────────────────────────────────
        Text(
            text = "SMS Protection",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingToggle(
                    title = "Store SMS Body",
                    subtitle = "Store message body text for SMS protection (not hashed)",
                    checked = smsBodyStorage,
                    onCheckedChange = { smsBodyStorage = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Permissions Section ─────────────────────────────────
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                PermissionButton(
                    title = "Call Screening Access",
                    subtitle = "Required to detect and block scam calls in real-time",
                    onClick = { openCallScreeningSettings(context) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                PermissionButton(
                    title = "Phone Permission",
                    subtitle = "Required to read incoming call details",
                    onClick = { openAppSettings(context) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── About Section ───────────────────────────────────────
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Version", "1.0.0")
                InfoRow("Privacy", "Zero PII — only SHA-256 hashes sent")
                InfoRow("Backend", "safering.deathbyathousand.com")
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun openCallScreeningSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS).apply {
            // On most devices, this opens the default apps settings
            // Users need to navigate to Call Screening > SafeRing
        }
        context.startActivity(intent)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
