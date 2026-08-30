package online.db1k.safering.android.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.util.Logger
import java.time.Instant
import javax.inject.Inject

data class QRScannerUiState(
    val isChecking: Boolean = false,
    val scannedURL: String? = null,
    val riskScore: Double? = null,
    val error: String? = null,
    val history: List<ScanResult> = emptyList()
) {
    companion object {
        val Scanning = QRScannerUiState()
        val Checking = QRScannerUiState(isChecking = true)
    }

    data class ScanResult(
        val url: String,
        val riskScore: Double,
        val timestamp: Instant = Instant.now()
    )

    class Result(score: Double, url: String) : QRScannerUiState(
        riskScore = score,
        scannedURL = url
    )

    class Error(message: String) : QRScannerUiState(error = message)
}

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val api: SafeRingApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<QRScannerUiState>(QRScannerUiState.Scanning)
    val uiState: StateFlow<QRScannerUiState> = _uiState.asStateFlow()

    fun checkURL(url: String) {
        _uiState.value = QRScannerUiState.Checking

        viewModelScope.launch {
            try {
                val response = api.checkURL(url)
                val result = QRScannerUiState.Result(response.risk_score, url)
                _uiState.value = result.copy(
                    history = listOf(
                        QRScannerUiState.ScanResult(url, response.risk_score)
                    ) + _uiState.value.history
                )

                Logger.info("QR scanned: $url, risk=${response.risk_score}")
            } catch (e: Exception) {
                Logger.debug("QR check failed: ${e.message}")
                _uiState.value = QRScannerUiState.Error("Failed to check URL: ${e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = QRScannerUiState.Scanning
    }
}
