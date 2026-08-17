package online.db1k.safering.android.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.service.PhoneRoles
import online.db1k.safering.android.service.SignalChannel
import online.db1k.safering.android.service.TripwireNotifier
import online.db1k.safering.android.service.SmsNotificationListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    var owner by remember { mutableStateOf(household.ownerDisplayName) }
    var setupTick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val refresh = setupTick
    val screeningOn = PhoneRoles.holdsCallScreening(context)
    val notifyOn = TripwireNotifier.canNotify(context)
    val contactsOn = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { setupTick++ }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { setupTick++ }
    var trustedName by remember { mutableStateOf(household.trustedContactName) }
    var trustedNumber by remember { mutableStateOf(household.trustedContactNumber) }
    var password by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(household.preferredChannel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.testTag("settings_title"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Your person", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(value = owner, onValueChange = { owner = it; household.ownerDisplayName = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = trustedName, onValueChange = { trustedName = it; household.trustedContactName = it }, label = { Text("Trusted person name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = trustedNumber, onValueChange = { trustedNumber = it; household.trustedContactNumber = it }, label = { Text("Trusted person number") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("How to reach them", style = MaterialTheme.typography.titleSmall)
                SignalChannel.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = channel == option,
                            onClick = {
                                channel = option
                                household.preferredChannel = option
                            }
                        )
                        Column {
                            Text(option.title)
                            Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("New family password") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { household.familyPassword = password.trim(); password = "" },
                    enabled = password.trim().length >= 3,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save password on this phone") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Safety checklist", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingToggle("SafeRing is call screening app", "Contacts ring. Unknown is silenced.", household.callScreeningConfirmed) { household.callScreeningConfirmed = it }
                SettingToggle("Carrier scam block is on", "T-Mobile Scam Shield, AT&T ActiveArmor, Verizon Call Filter", household.carrierProtectionConfirmed) { household.carrierProtectionConfirmed = it }
                SettingToggle("Silence unknown is OK", "Unknown callers should not ring", household.silenceUnknownConfirmed) { household.silenceUnknownConfirmed = it }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Text alerts (Android)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val nlsOn = SmsNotificationListener.isEnabled(context)
                SettingToggle(
                    "Capture numbers from text alerts",
                    "Best-effort. Reads Messages notifications only when you turn on Notification access. Stores a private fingerprint, not the full inbox.",
                    household.smsNotificationCaptureEnabled && nlsOn
                ) { on ->
                    household.smsNotificationCaptureEnabled = on
                    if (on && !nlsOn) {
                        SmsNotificationListener.openSettings(context)
                    }
                    setupTick++
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (nlsOn) "Notification access: ON" else "Notification access: OFF — tap Open Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!nlsOn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            household.smsNotificationCaptureEnabled = true
                            SmsNotificationListener.openSettings(context)
                            setupTick++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Notification access") }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Give the phone permission",
            modifier = Modifier.testTag("call_screening_section"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (screeningOn) "Caller ID & spam: on" else "Caller ID & spam: off")
                Text(
                    "Settings → Default apps → Caller ID & spam → SafeRing. Contacts still ring. Unknown is silenced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { roleLauncher.launch(PhoneRoles.requestCallScreeningIntent(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (screeningOn) "Change spam app" else "Make SafeRing the spam app") }

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (contactsOn) "Contacts: allowed" else "Contacts: needed")
                Text(
                    "So your people still ring. We do not upload the address book.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(if (notifyOn) "Notifications: allowed" else "Notifications: needed")
                Text(
                    "After we silence an unknown call, you get Get my person without opening the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val needed = buildList {
                            if (!contactsOn) add(Manifest.permission.READ_CONTACTS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifyOn) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (needed.isEmpty()) openAppSettings(context)
                        else permissionLauncher.launch(needed.toTypedArray())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow Contacts and notifications") }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Suspicious texts: in Messages, tap Share → SafeRing. We do not read your SMS inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                InfoRow("Version", "1.0.17")
                InfoRow("Privacy", "Family password and trusted number stay on this phone. Help texts are redacted.")
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
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
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
