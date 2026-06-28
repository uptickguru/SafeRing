package online.db1k.safering.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.util.AppConfig

data class HomeUiState(
    val protectedNumbers: Int = 0,
    val blockedToday: Int = 0,
    val scamCount: Int = 0,
    val isLoading: Boolean = false,
    val isExtensionActive: Boolean = false,
    val isDataStale: Boolean = false,
    val lastSyncTime: Long? = null
)

class HomeViewModel(
    private val repository: ScamRepository,
    private val db: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val scamCount = repository.getAllScamNumbers().first().size
                val callLogCount = db.callLogDao().getRecentCount(
                    System.currentTimeMillis() - 24L * 60 * 60 * 1000
                )

                // Check if data is stale (last sync > 24 hours ago)
                var isStale = false
                var lastSync: Long? = null
                runCatching {
                    val lastSyncVal = db.scamNumberDao().getLastUpdateTime()
                    if (lastSyncVal != null) {
                        lastSync = lastSyncVal
                        isStale = (System.currentTimeMillis() - lastSyncVal) > 24L * 3600 * 1000
                    } else {
                        isStale = true
                    }
                }

                _uiState.update {
                    it.copy(
                        protectedNumbers = scamCount,
                        scamCount = scamCount,
                        blockedToday = callLogCount.toInt(),
                        isLoading = false,
                        isDataStale = isStale,
                        lastSyncTime = lastSync
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isDataStale = true) }
            }
        }
    }

    class Factory(
        private val repository: ScamRepository,
        private val db: AppDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, db) as T
        }
    }
}
