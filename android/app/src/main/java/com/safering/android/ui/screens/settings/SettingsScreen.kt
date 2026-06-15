package com.safering.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safering.android.ui.components.BigToggle

/**
 * Settings screen with big toggles for seniors.
 *
 * Designed for simplicity:
 * - Single toggle for core features
 * - "Make it smarter" for detailed settings
 * - Large text and touch targets throughout
 * - No account, no login, no complexity
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var callScreeningEnabled by remember { mutableStateOf(true) }
    var smsScanningEnabled by remember { mutableStateOf(true) }
    var speechAnalysisEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var autoBlockEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Protection section
            SectionHeader(title = "Protection")

            BigToggle(
                text = "Call Screening",
                checked = callScreeningEnabled,
                onCheckedChange = { callScreeningEnabled = it },
                description = "Check incoming calls against scam database"
            )

            BigToggle(
                text = "SMS Scanning",
                checked = smsScanningEnabled,
                onCheckedChange = { smsScanningEnabled = it },
                description = "Classify incoming SMS messages for scams"
            )

            BigToggle(
                text = "Mid-Call Speech Analysis",
                checked = speechAnalysisEnabled,
                onCheckedChange = { speechAnalysisEnabled = it },
                description = "Detect scam language during calls (on-device, privacy-safe)"
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "Alerts & Actions")

            BigToggle(
                text = "Notifications",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it },
                description = "Show alerts for detected scams"
            )

            BigToggle(
                text = "Auto-Block Scammers",
                checked = autoBlockEnabled,
                onCheckedChange = { autoBlockEnabled = it },
                description = "Automatically block high-risk calls"
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "About SafeRing")

            // About card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "SafeRing v1.0.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI-powered scam call & SMS detection for seniors.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🔒 Zero PII Guarantee",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No account needed. No personal data collected. " +
                                "Phone numbers are hashed before any network call. " +
                                "Audio analysis runs entirely on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Data is cached locally for offline protection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Privacy notice
            Text(
                text = "SafeRing does not collect, store, or transmit any personal information. " +
                        "Phone numbers are anonymized via SHA-256 hashing. " +
                        "Audio analysis is performed on-device and never recorded or transmitted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
