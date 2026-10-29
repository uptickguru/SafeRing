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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import online.db1k.safering.android.service.AppModeStore
import online.db1k.safering.android.ui.theme.Appearance
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.service.SubscriptionGate
import online.db1k.safering.android.service.SafeCallCaretaker
import online.db1k.safering.android.service.StatusIcon
import online.db1k.safering.android.MainActivity
import online.db1k.safering.android.service.PhoneRoles
import online.db1k.safering.android.service.SignalChannel
import online.db1k.safering.android.service.TripwireNotifier
import online.db1k.safering.android.util.ShieldAnalytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(peopleOnly: Boolean = false, onModeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val household = remember { HouseholdStore.get(context) }
    val mode = remember { AppModeStore.get(context) }
    var role by remember { mutableStateOf(mode.role) }
    var plan by remember { mutableStateOf(mode.plan) }
    var density by remember { mutableStateOf(mode.displayDensity) }
    var seniorPin by remember { mutableStateOf("") }
    var seniorPin2 by remember { mutableStateOf("") }
    var unlockPin by remember { mutableStateOf("") }
    var showUnlock by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf(AppModeStore.Role.CARETAKER) }
    var modeError by remember { mutableStateOf<String?>(null) }
    var clearLockAfterUnlock by remember { mutableStateOf(false) }
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
    ) {
        setupTick++
        StatusIcon.show(context)
    }
    var trustedName by remember { mutableStateOf(household.trustedContactName) }
    var trustedNumber by remember { mutableStateOf(household.trustedContactNumber) }
    var password by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(household.preferredChannel) }
    val scope = rememberCoroutineScope()
    var safeCallSummary by remember { mutableStateOf("Tap refresh") }
    var safeCallBusy by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

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

        if (!peopleOnly) {
            Text("Phone mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("I am using this phone as")
                    AppModeStore.Role.entries.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = role == r,
                                onClick = {
                                    if (r == role) return@RadioButton
                                    if (role == AppModeStore.Role.SENIOR && mode.seniorLockEnabled && mode.hasSeniorLockPin && r != AppModeStore.Role.SENIOR) {
                                        pendingRole = r
                                        clearLockAfterUnlock = false
                                        unlockPin = ""
                                        showUnlock = true
                                    } else {
                                        mode.role = r
                                        role = r
                                        onModeChanged()
                                    }
                                }
                            )
                            Column {
                                Text(r.title)
                                Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (SubscriptionGate.enabled && role == AppModeStore.Role.CARETAKER) {
                    Text("Plan")
                    AppModeStore.Plan.entries.forEach { pl ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = plan == pl, onClick = { plan = pl; mode.plan = pl })
                            Column {
                                Text(pl.title)
                                Text(pl.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    }
                    Text("Display", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text("Follows the phone. Turn this on if the dark screen is hard to see.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Keep this phone light", modifier = Modifier.weight(1f))
                        Switch(
                            checked = Appearance.forceLight.value,
                            onCheckedChange = { Appearance.setForceLight(context, it) }
                        )
                    }
                    AppModeStore.DisplayDensity.entries.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = density == d,
                                onClick = {
                                    mode.displayDensity = d
                                    density = d
                                    onModeChanged()
                                }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(d.title, fontWeight = FontWeight.Medium)
                                Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (role == AppModeStore.Role.SENIOR) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Personal lock (6 digits)", fontWeight = FontWeight.SemiBold)
                        Text("Stops someone switching this phone out of Personal mode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = seniorPin, onValueChange = { if (it.filter(Char::isDigit).length <= 6) seniorPin = it.filter(Char::isDigit) }, label = { Text("New 6-digit code") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = seniorPin2, onValueChange = { if (it.filter(Char::isDigit).length <= 6) seniorPin2 = it.filter(Char::isDigit) }, label = { Text("Confirm code") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                modeError = null
                                if (seniorPin.length == 6 && seniorPin == seniorPin2) {
                                    if (mode.setSeniorLockPin(seniorPin)) {
                                        seniorPin = ""; seniorPin2 = ""
                                    } else modeError = "Could not save code"
                                } else modeError = "Enter the same 6 digits twice"
                            },
                            enabled = seniorPin.length == 6,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Personal lock code") }
                        if (mode.hasSeniorLockPin) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Personal lock on")
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = mode.seniorLockEnabled, onCheckedChange = { mode.seniorLockEnabled = it })
                            }
                            TextButton(onClick = {
                                pendingRole = role
                                clearLockAfterUnlock = true
                                unlockPin = ""
                                showUnlock = true
                            }) { Text("Remove Personal lock") }
                        }
                    }
                    modeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text("Home and lock screen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Call safety", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Press and hold the home screen, then Widgets, then HumanAnswer. On a newer phone the same widget can sit on the lock screen. It opens the app. It does not listen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                var topBar by remember { mutableStateOf(StatusIcon.isOn(context)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Icon on the top bar")
                        Text(
                            "Quiet. No sound. The phone may ask to allow alerts once.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = topBar,
                        onCheckedChange = { on ->
                            topBar = on
                            StatusIcon.setOn(context, on)
                            if (on && !StatusIcon.allowed(context)) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

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
                Text(
                    "Text only. The message opens in the phone's text app. No other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LaunchedEffect(Unit) {
                    if (household.preferredChannel != SignalChannel.SMS) {
                        household.preferredChannel = SignalChannel.SMS
                        channel = SignalChannel.SMS
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
                SettingToggle("HumanAnswer is call screening app", "Contacts ring. Unknown is silenced.", household.callScreeningConfirmed) { household.callScreeningConfirmed = it }
                SettingToggle("Carrier scam block is on", "T-Mobile Scam Shield, AT&T ActiveArmor, Verizon Call Filter", household.carrierProtectionConfirmed) { household.carrierProtectionConfirmed = it }
                SettingToggle("Silence unknown is OK", "Unknown callers should not ring", household.silenceUnknownConfirmed) { household.silenceUnknownConfirmed = it }
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
                    "Settings → Default apps → Caller ID & spam → HumanAnswer. Contacts still ring. Unknown is silenced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        PhoneRoles.requestCallScreeningIntent(context)?.let { roleLauncher.launch(it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (screeningOn) "Change spam app" else "Make HumanAnswer the spam app") }

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
                    "Suspicious texts: in Messages, tap Share → HumanAnswer. We do not read your SMS inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Filter words", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { context.startActivity(Intent(context, FilterWordsActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open filter words") }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Block list", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { context.startActivity(Intent(context, BlockListActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open block list") }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Investigate & report", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingToggle(
                    "Exceptional investigation",
                    "Family OSINT assist — paste number + full message. Seeds local block.",
                    household.exceptionalCaptureEnabled
                ) { household.exceptionalCaptureEnabled = it; setupTick++ }
                Button(
                    onClick = {
                        ShieldAnalytics.event(context, "investigate_open")
                        context.startActivity(Intent(context, InvestigateReportActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Investigate a number / report full message") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("SafeCall (caretaker)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Lab: Android = caretaker, iPhone = senior. Status / Approve / Drop on live edge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(safeCallSummary, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            safeCallBusy = true
                            scope.launch {
                                val s = SafeCallCaretaker.fetchStatus(context)
                                safeCallSummary = s.summary + if (s.pending) " · pending" else ""
                                safeCallBusy = false
                            }
                        },
                        enabled = !safeCallBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Refresh") }
                    Button(
                        onClick = {
                            safeCallBusy = true
                            scope.launch {
                                val r = SafeCallCaretaker.approve(context)
                                safeCallSummary = r.fold({ "Approved" }, { "Approve failed: ${it.message}" })
                                safeCallBusy = false
                            }
                        },
                        enabled = !safeCallBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Approve") }
                }
                OutlinedButton(
                    onClick = {
                        safeCallBusy = true
                        scope.launch {
                            val r = SafeCallCaretaker.drop(context)
                            safeCallSummary = r.fold({ "Drop sent" }, { "Drop failed: ${it.message}" })
                            safeCallBusy = false
                        }
                    },
                    enabled = !safeCallBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Drop / hang up bridge") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Learn and print", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ShieldAnalytics.event(context, "feature_demo_open"); context.startActivity(Intent(context, FeatureDemoActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Feature demo — how each tool works") }
                OutlinedButton(
                    onClick = { ShieldAnalytics.event(context, "print_card_open"); context.startActivity(Intent(context, ProtectionCardActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Print protection card") }
                if (role == AppModeStore.Role.CARETAKER) {
                    OutlinedButton(
                        onClick = {
                            ShieldAnalytics.event(context, "falls_tips_open")
                            context.startActivity(Intent(context, SeniorSafetyActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Fall check-in") }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, TrustedNoticesActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Notifications") }
                }
                Text(
                    "Cheap Android: screen lock ON, Caller ID role ON, Play Protect ON, install only from family.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(onClick = { showResetConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start over — clear and onboard again")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── About Section ───────────────────────────────────────
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val ver = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.21"
                } catch (_: Exception) { "1.0.21" }
                InfoRow("Version", ver)
                InfoRow("Support", "kevinasbury.com")
                InfoRow("Privacy", "Family password and trusted number stay on this phone. Help texts are redacted.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Human in the loop: Approve / HELP / money decisions stay with people.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kevinasbury.com")))
                }, modifier = Modifier.fillMaxWidth()) { Text("Privacy, terms, and help") }
            }
        }
    }


    if (showUnlock) {
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text("Enter Personal lock code") },
            text = {
                Column {
                    Text("This phone is locked in Personal mode.")
                    OutlinedTextField(
                        value = unlockPin,
                        onValueChange = { if (it.filter(Char::isDigit).length <= 6) unlockPin = it.filter(Char::isDigit) },
                        label = { Text("6-digit code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (mode.verifySeniorLockPin(unlockPin)) {
                        if (clearLockAfterUnlock) {
                            mode.clearSeniorLockPin()
                        } else {
                            mode.role = pendingRole
                            role = pendingRole
                            onModeChanged()
                        }
                        showUnlock = false
                        unlockPin = ""
                        modeError = null
                    } else {
                        modeError = "Wrong code. Personal mode stays locked."
                        showUnlock = false
                    }
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlock = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Start over?") },
            text = { Text("Clears household setup on this phone so you can onboard again.") },
            confirmButton = {
                TextButton(onClick = {
                    AppModeStore.get(context).reset()
                    household.reset()
                    showResetConfirm = false
                    ShieldAnalytics.event(context, "onboarding_reset")
                    val i = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(i)
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
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
            // Users need to navigate to Call Screening > HumanAnswer
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
