package com.safering.android.ui.screens.callhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safering.android.data.local.entity.CallLogEntity
import com.safering.android.ui.components.RiskBadge
import com.safering.android.ui.components.RiskDot
import com.safering.android.ui.theme.SafeRingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Call history screen showing all logged calls with scam risk flags.
 *
 * Provides filtering by risk level and chronological sorting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen() {
    var selectedFilter by remember { mutableStateOf("all") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call History",
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
        ) {
            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    label = { Text("All", fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                FilterChip(
                    selected = selectedFilter == "scam",
                    onClick = { selectedFilter = "scam" },
                    label = { Text("Scam", fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SafeRingColors.RiskHigh.copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = selectedFilter == "safe",
                    onClick = { selectedFilter = "safe" },
                    label = { Text("Safe", fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SafeRingColors.RiskSafe.copy(alpha = 0.2f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Call list (using sample data for MVP — Room Flow integration in production)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sampleCalls = remember {
                    generateSampleCallLogs()
                }

                val filteredCalls = when (selectedFilter) {
                    "scam" -> sampleCalls.filter { it.isScam }
                    "safe" -> sampleCalls.filter { !it.isScam && it.riskScore == 0f }
                    else -> sampleCalls
                }

                if (filteredCalls.isEmpty()) {
                    item {
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
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.height(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No calls found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredCalls) { call ->
                        CallHistoryItem(call = call)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryItem(call: CallLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Risk indicator
            RiskBadge(
                riskScore = call.riskScore,
                showText = false,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Call details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.scamLabel ?: "Unknown Caller",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatDateTime(call.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            call.isScam -> "Scam Call"
                            call.riskScore > 0f -> "Suspicious"
                            else -> "Safe"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            call.isScam -> SafeRingColors.RiskHigh
                            call.riskScore > 0f -> SafeRingColors.RiskMedium
                            else -> SafeRingColors.RiskSafe
                        },
                        fontWeight = FontWeight.Medium
                    )
                    if (call.wasBlocked) {
                        Text(
                            text = "• Blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeRingColors.RiskHigh
                        )
                    }
                }
            }

            // Risk score
            Text(
                text = "${(call.riskScore * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    call.isScam -> SafeRingColors.RiskHigh
                    call.riskScore > 0f -> SafeRingColors.RiskMedium
                    else -> SafeRingColors.RiskSafe
                }
            )
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Generate sample call logs for UI development.
 * In production, this data comes from Room DB via CallLogDao.
 */
private fun generateSampleCallLogs(): List<CallLogEntity> {
    val now = System.currentTimeMillis()
    return listOf(
        CallLogEntity(
            id = 1,
            hash = "sample1",
            riskScore = 0.92f,
            scamLabel = "IRS Impersonation",
            isScam = true,
            wasBlocked = true,
            timestamp = now - 3_600_000 // 1 hour ago
        ),
        CallLogEntity(
            id = 2,
            hash = "sample2",
            riskScore = 0.0f,
            scamLabel = "Mom",
            isScam = false,
            timestamp = now - 7_200_000 // 2 hours ago
        ),
        CallLogEntity(
            id = 3,
            hash = "sample3",
            riskScore = 0.45f,
            scamLabel = "Unknown Number",
            isScam = false,
            timestamp = now - 86_400_000 // 1 day ago
        ),
        CallLogEntity(
            id = 4,
            hash = "sample4",
            riskScore = 0.88f,
            scamLabel = "Tech Support Scam",
            isScam = true,
            wasBlocked = true,
            timestamp = now - 172_800_000 // 2 days ago
        ),
        CallLogEntity(
            id = 5,
            hash = "sample5",
            riskScore = 0.0f,
            scamLabel = "Dr. Smith",
            isScam = false,
            timestamp = now - 259_200_000 // 3 days ago
        )
    )
}
