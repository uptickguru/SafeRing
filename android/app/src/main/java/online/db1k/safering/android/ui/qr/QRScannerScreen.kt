package online.db1k.safering.android.ui.qr

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: QRScannerViewModel = viewModel(factory = QRScannerViewModel.Factory),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 24.sp) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasCameraPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                ) {
                    Text(
                        "Camera permission required to scan QR codes.\n\nTip: You can also paste a URL below to check it.",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF856404)
                    )
                }
            }

            when {
                uiState.isChecking -> {
                    CircularProgressIndicator()
                    Text("Checking URL safety...")
                }

                uiState.scannedURL != null && !uiState.isChecking -> {
                    ResultView(uiState)
                    Button(onClick = { viewModel.reset() }) {
                        Text("Scan Another")
                    }
                }

                else -> {
                    Text(
                        "QR Scanner",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Camera-based QR scanning requires CameraX + ML Kit.\nUse the paste check below for quick URL verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Paste URL fallback
            var pasteText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                label = { Text("Paste URL to check") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (pasteText.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.checkURL(pasteText)
                        pasteText = ""
                    }
                ) {
                    Text("Check URL")
                }
            }

            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        uiState.error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // History
            if (uiState.history.isNotEmpty()) {
                Text(
                    "Recent Scans",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(uiState.history.take(5)) { result ->
                        HistoryRow(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultView(state: QRScannerUiState) {
    val score = state.riskScore ?: 0.0
    val (icon, color, label) = when {
        score < 0.3 -> Triple("✓", Color(0xFF4CAF50), "Safe")
        score < 0.7 -> Triple("⚠", Color(0xFFFF9800), "Suspicious")
        else -> Triple("✗", Color(0xFFF44336), "Dangerous")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 60.sp, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.SemiBold)
        Text(
            "Risk Score: ${(score * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("URL:", style = MaterialTheme.typography.labelSmall)
                Text(
                    state.scannedURL ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (state.indicators.isNotEmpty()) {
            Text("Indicators:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
            state.indicators.forEach { indicator ->
                Text("• $indicator", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryRow(result: QRScannerUiState.ScanResult) {
    val color = when {
        result.riskScore < 0.3 -> Color(0xFF4CAF50)
        result.riskScore < 0.7 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            result.url,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            timeFormat.format(Date(result.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
