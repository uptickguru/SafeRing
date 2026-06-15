package online.db1k.safering.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        val homeViewModel = HomeViewModel(app.repository)

        setContent {
            SafeRingTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Phone, contentDescription = "History") },
                                label = { Text("History") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Warning, contentDescription = "Report") },
                                label = { Text("Report") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
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
