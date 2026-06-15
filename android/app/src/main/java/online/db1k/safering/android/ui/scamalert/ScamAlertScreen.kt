package online.db1k.safering.android.ui.scamalert

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.ui.theme.CriticalRed
import online.db1k.safering.android.ui.theme.WarningYellow

@Composable
fun ScamAlertOverlay(
    riskLabel: String,
    riskScore: Double,
    callerName: String,
    scamType: String,
    onDismiss: () -> Unit,
    onBlock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (riskScore >= 0.85) CriticalRed else WarningYellow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warning icon
            Text(
                text = if (riskScore >= 0.85) "🚨" else "⚠️",
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = riskLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Caller: $callerName",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (scamType.isNotEmpty()) {
                Text(
                    text = "Type: $scamType",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Risk score bar
            LinearProgressIndicator(
                progress = { riskScore.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Risk Score: ${"%.0f".format(riskScore * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
                Button(
                    onClick = onBlock,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Block Number")
                }
            }
        }
    }
}
