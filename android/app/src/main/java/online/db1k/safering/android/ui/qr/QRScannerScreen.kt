package online.db1k.safering.android.ui.qr

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: QRScannerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                ) {
                    Text(
                        "Camera permission required to scan QR codes",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF856404)
                    )
                }
            } else {
                when (val state = uiState) {
                    is QRScannerUiState.Scanning -> {
                        Text(
                            "Point your camera at a QR code",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        ) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                onQRCodeScanned = { url ->
                                    viewModel.checkURL(url)
                                }
                            )
                        }
                    }

                    is QRScannerUiState.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            "Checking URL safety...",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    is QRScannerUiState.Result -> {
                        RiskIndicator(
                            score = state.riskScore,
                            url = state.url
                        )

                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Scan Another")
                        }
                    }

                    is QRScannerUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                state.message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // History section
            if (uiState.history.isNotEmpty()) {
                Text(
                    "Recent Scans",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.history.take(5)) { result ->
                        HistoryItem(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onQRCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                val preview = Preview.Builder().build()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        val scanner = BarcodeScanning.getClient()

                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    barcode.rawValue?.let { onQRCodeScanned(it) }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        preview.setSurfaceProvider(surfaceProvider)
                    } catch (e: Exception) {
                        // Handle error
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

@Composable
private fun RiskIndicator(score: Double, url: String) {
    val (icon, color, label) = when {
        score < 0.3 -> Triple("✓", Color(0xFF4CAF50), "Safe")
        score < 0.7 -> Triple("⚠", Color(0xFFFF9800), "Suspicious")
        else -> Triple("✗", Color(0xFFF44336), "Dangerous")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            icon,
            fontSize = 60.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )

        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )

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
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(result: QRScannerViewModel.ScanResult) {
    val color = when {
        result.riskScore < 0.3 -> Color(0xFF4CAF50)
        result.riskScore < 0.7 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

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
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )

        Text(
            result.url,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        Text(
            result.timestamp.toString().substring(11, 16),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
