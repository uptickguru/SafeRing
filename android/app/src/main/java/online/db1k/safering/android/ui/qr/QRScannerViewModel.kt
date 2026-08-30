package online.db1k.safering.android.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.util.Logger

data class QRScannerUiState(
    val isChecking: Boolean = false,
    val scannedURL: String? = null,
    val riskScore: Double? = null,
    val category: String? = null,
    val indicators: List<String> = emptyList(),
    val error: String? = null,
    val history: List<ScanResult> = emptyList()
) {
    data class ScanResult(
        val url: String,
        val riskScore: Double,
        val category: String,
        val timestamp: Long = System.currentTimeMillis()
    )
}

class QRScannerViewModel : ViewModel() {

    private val api: SafeRingApi = SafeRingApi.create()

    private val _uiState = MutableStateFlow(QRScannerUiState())
    val uiState: StateFlow<QRScannerUiState> = _uiState.asStateFlow()

    fun checkURL(url: String) {
        _uiState.update { it.copy(isChecking = true, error = null, scannedURL = url) }

        viewModelScope.launch {
            try {
                val response = api.checkURL(url)
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        riskScore = response.risk,
                        category = response.label ?: if (response.risk < 0.3) "safe" else if (response.risk < 0.7) "suspicious" else "dangerous",
                        indicators = response.tags,
                        history = listOf(
                            QRScannerUiState.ScanResult(url, response.risk, response.label ?: "unknown")
                        ) + it.history.take(9)
                    )
                }
                Logger.info("QR scanned: $url risk=${response.risk}")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isChecking = false, error = "Failed to check URL: ${e.localizedMessage ?: e.message}")
                }
                Logger.debug("QR check failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = QRScannerUiState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QRScannerViewModel() as T
            }
        }
    }
}
