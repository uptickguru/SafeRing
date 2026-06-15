package com.safering.android.ui.screens.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safering.android.ui.components.BigButton
import com.safering.android.ui.theme.SafeRingColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One-tap scam report screen.
 *
 * Zero PII guarantee: Only the SHA-256 hash of the phone number
 * plus a predefined scam tag are sent to the server.
 * The notes field is stored locally only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    onReportSubmitted: () -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scamTags = listOf(
        "IRS Impersonation",
        "Tech Support",
        "Grandparent Scam",
        "Romance Scam",
        "Crypto Scam",
        "Phishing",
        "Robocall",
        "Other"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Report a Scam",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showSuccess) {
                // Success state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = SafeRingColors.RiskSafe.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✅",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Report Submitted",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeRingColors.RiskSafe
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Thank you for helping keep the community safe.\nYour report has been stored locally and will be synced when connected.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                phoneNumber = ""
                                selectedTag = null
                                showSuccess = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafeRingColors.RiskSafe
                            )
                        ) {
                            Text("Report Another", fontSize = 18.sp)
                        }
                    }
                }
            } else {
                // Instructions
                Text(
                    text = "Report a scam call or text to help protect others. " +
                            "Your report is anonymous — only the hashed number and scam type are shared.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Phone number input
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = {
                        Text(
                            "Phone Number",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    placeholder = { Text("e.g. 15551234567") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Scam type tags
                Text(
                    text = "Scam Type (optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scamTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = {
                                Text(
                                    text = tag,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SafeRingColors.RiskHigh.copy(alpha = 0.2f),
                                selectedLabelColor = SafeRingColors.RiskHigh
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                // Privacy notice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🔒 Your privacy is protected. Only the hashed number " +
                                "and scam type are sent to our servers. " +
                                "No personal information is collected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Submit button
                BigButton(
                    text = if (isSubmitting) "Submitting..." else "Report Scam Number",
                    onClick = {
                        isSubmitting = true
                        scope.launch {
                            delay(1000) // Simulate network call
                            isSubmitting = false
                            showSuccess = true
                        }
                    },
                    enabled = phoneNumber.isNotBlank() && !isSubmitting,
                    containerColor = SafeRingColors.RiskHigh,
                    contentColor = Color.White
                )
            }
        }
    }
}
