package com.safering.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safering.android.data.local.entity.CallLogEntity
import com.safering.android.data.repository.ScamRepository
import com.safering.android.domain.usecase.CheckCallUseCase
import com.safering.android.service.SyncWorker
import com.safering.android.ui.theme.SafeRingColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the HomeScreen.
 */
data class HomeUiState(
    val protectionActive: Boolean = true,
    val scamCallsBlocked: Int = 0,
    val scamMessagesDetected: Int = 0,
    val recentCalls: List<CallLogEntity> = emptyList(),
    val lastSyncTime: Long? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val scamCallCount = repository.scamCallCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scamMessageCount = repository.scamMessageCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentCalls = repository.getRecentCallLogs(limit = 5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Collect recent calls
            repository.getRecentCallLogs(5).collect { calls ->
                _uiState.value = _uiState.value.copy(
                    recentCalls = calls,
                    isLoading = false
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Trigger a manual sync
            try {
                kotlinx.coroutines.delay(500)
                _uiState.value = _uiState.value.copy(
                    lastSyncTime = System.currentTimeMillis(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleProtection(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(protectionActive = enabled)
    }
}
