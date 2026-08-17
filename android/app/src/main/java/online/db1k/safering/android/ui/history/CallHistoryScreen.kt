package online.db1k.safering.android.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.local.models.CallLogEntity
import online.db1k.safering.android.data.local.models.SmsLogEntity
import online.db1k.safering.android.ui.theme.CallSage
import online.db1k.safering.android.ui.theme.HelpBurgundy
import online.db1k.safering.android.ui.theme.Ink
import online.db1k.safering.android.ui.theme.Ivory
import online.db1k.safering.android.ui.theme.Mute
import online.db1k.safering.android.ui.theme.UnsureBronze
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History shows senior-safe labels + risk — never raw caller/SMS numbers.
 * Numbers are HMAC-hashed at intake.
 */
@Composable
fun CallHistoryScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val calls by db.callLogDao().getRecentCallLogs(100).collectAsState(initial = emptyList())
    val sms by db.smsLogDao().getRecentSmsLogs(50).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "History",
            modifier = Modifier.testTag("history_title"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            text = "Labels only — numbers stay private on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Mute,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (calls.isEmpty() && sms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("history_empty"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No activity yet", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Screened calls and message checks appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mute
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (calls.isNotEmpty()) {
                    item {
                        Text("Calls", fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    items(calls, key = { "c${it.id}" }) { row ->
                        CallRow(row)
                    }
                }
                if (sms.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Message checks", fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    items(sms, key = { "s${it.id}" }) { row ->
                        SmsRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRow(row: CallLogEntity) {
    val accent = when {
        row.wasBlocked -> HelpBurgundy
        row.riskScore >= 0.5 -> UnsureBronze
        else -> CallSage
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.callerName ?: "Caller", fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    "${row.riskLabel} · ${formatTime(row.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mute
                )
                Text(
                    "ref ${row.numberHash.take(8)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mute.copy(alpha = 0.7f)
                )
            }
            Text(
                if (row.wasBlocked) "Silenced" else "OK",
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SmsRow(row: SmsLogEntity) {
    val accent = when {
        row.wasBlocked || row.riskScore >= 0.6 -> HelpBurgundy
        row.riskScore >= 0.3 -> UnsureBronze
        else -> CallSage
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(row.riskLabel, fontWeight = FontWeight.SemiBold, color = accent)
            Text(formatTime(row.timestamp), style = MaterialTheme.typography.bodySmall, color = Mute)
            row.scamType?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Ink)
            }
            Text(
                "ref ${row.numberHash.take(8)}…",
                style = MaterialTheme.typography.labelSmall,
                color = Mute.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatTime(ts: Long): String {
    return SimpleDateFormat("MMM d · h:mm a", Locale.US).format(Date(ts))
}
