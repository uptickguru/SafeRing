package online.db1k.safering.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.repository.ScamRepository

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
                val dayMs = 24L * 60L * 60L * 1000L
                val scamCount = repository.getAllScamNumbers().first().size
                val callLogCount = db.callLogDao().getRecentCount(
                    System.currentTimeMillis() - dayMs
                )

                // Proxy last sync from newest local scam row update
                var isStale = false
                var lastSync: Long? = null
                runCatching {
                    val lastSyncPref: Long? = db.scamNumberDao().getLastUpdatedAt()
                    if (lastSyncPref != null) {
                        lastSync = lastSyncPref
                        val ageMs: Long = System.currentTimeMillis() - lastSyncPref
                        isStale = ageMs > dayMs
                    } else {
                        isStale = true
                    }
                }

                _uiState.update {
                    it.copy(
                        protectedNumbers = scamCount,
                        scamCount = scamCount,
                        blockedToday = callLogCount,
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
