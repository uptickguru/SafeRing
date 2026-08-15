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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import online.db1k.safering.android.ui.history.CallHistoryScreen
import online.db1k.safering.android.ui.home.HomeScreen
import online.db1k.safering.android.ui.home.HomeViewModel
import online.db1k.safering.android.ui.report.ReportScreen
import online.db1k.safering.android.ui.settings.SettingsScreen
import online.db1k.safering.android.ui.theme.SafeRingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SafeRingApp
        val homeViewModel = HomeViewModel(app.repository, app.database)

        setContent {
            SafeRingTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

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
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(viewModel = homeViewModel)
                            1 -> CallHistoryScreen()
                            2 -> ReportScreen()
                            3 -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
