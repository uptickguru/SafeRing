@file:OptIn(ExperimentalComposeUiApi::class)
package online.db1k.safering.android

import androidx.compose.ui.ExperimentalComposeUiApi
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import online.db1k.safering.android.service.HelpReason
import online.db1k.safering.android.service.HelpSignaler
import online.db1k.safering.android.service.HouseholdStore
import online.db1k.safering.android.ui.check.PasteCheckScreen
import online.db1k.safering.android.ui.history.CallHistoryScreen
import online.db1k.safering.android.ui.home.HomeScreen
import online.db1k.safering.android.ui.lessons.LessonsScreen
import online.db1k.safering.android.ui.onboarding.OnboardingScreen
import online.db1k.safering.android.ui.report.ReportScreen
import online.db1k.safering.android.ui.settings.SettingsScreen
import online.db1k.safering.android.ui.theme.SafeRingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SafeRingTheme {
                val household = remember { HouseholdStore.get(this@MainActivity) }
                val signaler = remember { HelpSignaler(this@MainActivity, household) }
                var onboarded by remember { mutableStateOf(household.hasCompletedOnboarding && household.isConfigured) }
                var selectedTab by remember { mutableIntStateOf(0) }
                var showCheck by remember { mutableStateOf(false) }
                var showAfterCall by remember { mutableStateOf(false) }

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

                if (!onboarded) {
                    OnboardingScreen(onFinished = { onboarded = true })
                    return@SafeRingTheme
                }

                if (showCheck) {
                    PasteCheckScreen(
                        trustedName = household.trustedContactName,
                        onHelp = {
                            signaler.send(HelpReason.PASTE_SCAM)
                            showCheck = false
                        },
                        onCall = {
                            signaler.callSaved()
                            showCheck = false
                        },
                        onClose = { showCheck = false }
                    )
                    return@SafeRingTheme
                }

                Scaffold(
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                modifier = Modifier.testTag("tab_home")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Phone, contentDescription = "History") },
                                label = { Text("History") },
                                modifier = Modifier.testTag("tab_history")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Warning, contentDescription = "Report") },
                                label = { Text("Report") },
                                modifier = Modifier.testTag("tab_report")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("tab_settings")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                icon = { Icon(Icons.Default.Person, contentDescription = "Lessons") },
                                label = { Text("Lessons") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(onOpenCheck = { showCheck = true })
                            1 -> CallHistoryScreen()
                            2 -> ReportScreen()
                            3 -> SettingsScreen()
                            4 -> LessonsScreen()
                        }
                    }
                }

                if (showAfterCall) {
                    AlertDialog(
                        onDismissRequest = { showAfterCall = false },
                        title = { Text("A call just ended") },
                        text = {
                            Text("We do not know who it was. If anyone asked for money, passwords, or secrecy, get ${household.trustedContactName.ifBlank { "your person" }}.")
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
}
