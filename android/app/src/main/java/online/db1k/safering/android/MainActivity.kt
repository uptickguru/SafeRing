@file:OptIn(ExperimentalComposeUiApi::class)

package online.db1k.safering.android

import androidx.compose.ui.ExperimentalComposeUiApi
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import online.db1k.safering.android.service.HelpReason
import online.db1k.safering.android.service.HelpSignaler
import online.db1k.safering.android.service.AppModeStore
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.service.TripwireNotifier
import online.db1k.safering.android.ui.check.PasteCheckScreen
import online.db1k.safering.android.ui.history.CallHistoryScreen
import online.db1k.safering.android.ui.home.HomeScreen
import online.db1k.safering.android.ui.home.CaretakerHomeScreen
import online.db1k.safering.android.ui.onboarding.OnboardingScreen
import online.db1k.safering.android.ui.settings.SettingsScreen
import online.db1k.safering.android.ui.theme.Ivory
import online.db1k.safering.android.ui.theme.Mute
import online.db1k.safering.android.ui.theme.SafeRingTheme
import online.db1k.safering.android.ui.theme.SoftGold
import online.db1k.safering.android.util.PhoneNumberUtils

class MainActivity : ComponentActivity() {

    private var incomingSharedText by mutableStateOf<String?>(null)
    private var pendingCheckIn by mutableStateOf(false)
    private var pendingHelp by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)

        setContent {
            SafeRingTheme {
                val household = remember { HouseholdStore.get(this@MainActivity) }
                val mode = remember { AppModeStore.get(this@MainActivity) }
                var modeTick by remember { mutableIntStateOf(0) }
                @Suppress("UNUSED_VARIABLE") val modeRefresh = modeTick + mode.role.ordinal
                val signaler = remember { HelpSignaler(this@MainActivity, household) }
                var onboarded by remember { mutableStateOf(household.hasCompletedOnboarding && household.isConfigured) }
                var selectedTab by remember { mutableIntStateOf(0) }
                var showCheck by remember { mutableStateOf(false) }
                var showAfterCall by remember { mutableStateOf(false) }
                var checkText by remember { mutableStateOf("") }
                var checkSender by remember { mutableStateOf("") }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, onboarded) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && onboarded &&
                            household.consumeUnknownCallCheckIn()
                        ) {
                            showAfterCall = true
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(incomingSharedText, pendingCheckIn, pendingHelp, onboarded) {
                    if (!onboarded) return@LaunchedEffect
                    incomingSharedText?.let { shared ->
                        checkText = shared
                        // Share rarely includes sender; pull first phone from body if present
                        checkSender = PhoneNumberUtils.firstPhone(shared)?.let { PhoneNumberUtils.pretty(it) }.orEmpty()
                        showCheck = true
                        incomingSharedText = null
                    }
                    if (pendingCheckIn) {
                        showAfterCall = true
                        pendingCheckIn = false
                    }
                    if (pendingHelp) {
                        pendingHelp = false
                        signaler.send(HelpReason.AFTER_CALL)
                    }
                }

                if (!onboarded) {
                    OnboardingScreen(onFinished = { onboarded = true })
                    return@SafeRingTheme
                }

                if (showCheck) {
                    PasteCheckScreen(
                        trustedName = household.trustedContactName,
                        initialText = checkText,
                        initialSender = checkSender,
                        onHelp = {
                            signaler.send(HelpReason.PASTE_SCAM)
                            showCheck = false
                            checkText = ""
                            checkSender = ""
                        },
                        onCall = {
                            signaler.callSaved()
                            showCheck = false
                            checkText = ""
                            checkSender = ""
                        },
                        onClose = {
                            showCheck = false
                            checkText = ""
                            checkSender = ""
                        }
                    )
                    return@SafeRingTheme
                }

                Scaffold(
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                    containerColor = Ivory,
                    bottomBar = {
                        NavigationBar(
                            containerColor = Ivory,
                            contentColor = SoftGold,
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            val colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SoftGold,
                                selectedTextColor = SoftGold,
                                unselectedIconColor = Mute,
                                unselectedTextColor = Mute,
                                indicatorColor = SoftGold.copy(alpha = 0.12f)
                            )
                            val isCaretaker = mode.role == AppModeStore.Role.CARETAKER
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = colors,
                                modifier = Modifier.testTag("tab_home")
                            )
                            if (isCaretaker) {
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Person, contentDescription = "People") },
                                    label = { Text("People") },
                                    colors = colors,
                                    modifier = Modifier.testTag("tab_people")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Activity") },
                                    label = { Text("Activity") },
                                    colors = colors,
                                    modifier = Modifier.testTag("tab_activity")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    colors = colors,
                                    modifier = Modifier.testTag("tab_settings")
                                )
                            } else {
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Phone, contentDescription = "History") },
                                    label = { Text("History") },
                                    colors = colors,
                                    modifier = Modifier.testTag("tab_history")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    colors = colors,
                                    modifier = Modifier.testTag("tab_settings")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        val isCaretaker = mode.role == AppModeStore.Role.CARETAKER
                        when {
                            selectedTab == 0 && isCaretaker -> CaretakerHomeScreen()
                            selectedTab == 0 -> HomeScreen(
                                onOpenCheck = { showCheck = true },
                                onOpenSettings = { selectedTab = if (isCaretaker) 3 else 2 }
                            )
                            selectedTab == 1 && isCaretaker -> SettingsScreen(peopleOnly = true, onModeChanged = { selectedTab = 0; modeTick++ })
                            selectedTab == 1 -> CallHistoryScreen()
                            selectedTab == 2 && isCaretaker -> {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Activity", style = MaterialTheme.typography.headlineSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text("No pending alerts. When SafeCall notifies this caretaker, Approve / Deny appears here.")
                                }
                            }
                            else -> SettingsScreen(onModeChanged = { selectedTab = 0; modeTick++ })
                        }
                    }
                }

                if (showAfterCall) {
                    AlertDialog(
                        onDismissRequest = { showAfterCall = false },
                        title = { Text("A call just ended") },
                        text = {
                            Text(
                                "We do not show the number. If anyone asked for money, passwords, or secrecy, get ${household.trustedContactName.ifBlank { "your person" }}."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showAfterCall = false
                                signaler.send(HelpReason.AFTER_CALL)
                            }) { Text("Get my person") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAfterCall = false }) { Text("It was fine") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent == null) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(TripwireNotifier.EXTRA_SHARED_TEXT)
        if (!shared.isNullOrBlank()) {
            incomingSharedText = shared.trim()
        }
        if (intent.getBooleanExtra(TripwireNotifier.EXTRA_SHOW_CHECKIN, false)) {
            pendingCheckIn = true
        }
        if (intent.getStringExtra(TripwireNotifier.EXTRA_HELP_REASON) != null) {
            pendingHelp = true
        }
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(TripwireNotifier.EXTRA_SHARED_TEXT)
        intent.removeExtra(TripwireNotifier.EXTRA_SHOW_CHECKIN)
        intent.removeExtra(TripwireNotifier.EXTRA_HELP_REASON)
    }
}
