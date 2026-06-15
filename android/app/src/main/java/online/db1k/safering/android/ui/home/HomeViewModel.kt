package online.db1k.safering.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.data.remote.SafeRingApi

data class HomeUiState(
    val protectedNumbers: Int = 0,
    val blockedToday: Int = 0,
    val scamCount: Int = 0,
    val isLoading: Boolean = false,
    val isExtensionActive: Boolean = false
)

class HomeViewModel(
    private val repository: ScamRepository
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
                _uiState.update {
                    it.copy(
                        protectedNumbers = scamCount,
                        scamCount = scamCount,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    class Factory(
        private val repository: ScamRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
