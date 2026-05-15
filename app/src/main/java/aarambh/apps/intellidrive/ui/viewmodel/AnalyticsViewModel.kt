package aarambh.apps.intellidrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aarambh.apps.intellidrive.data.model.DriveSession
import aarambh.apps.intellidrive.data.model.EventEntity
import aarambh.apps.intellidrive.data.repository.AnalyticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnerAnalytics(
    val sessionCount: Int = 0,
    val latestSafetyScore: Int = 100,
    val totalHarshBraking: Int = 0,
    val totalOverspeed: Int = 0,
    val sessions: List<DriveSession> = emptyList(),
    val events: List<EventEntity> = emptyList()
)

sealed interface AnalyticsUiState {
    data object Idle : AnalyticsUiState
    data object Loading : AnalyticsUiState
    data class Success(val analytics: LearnerAnalytics) : AnalyticsUiState
    data class Error(val message: String) : AnalyticsUiState
}

class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository = AnalyticsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyticsUiState>(AnalyticsUiState.Idle)
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    fun loadAnalytics(learnerId: String) {
        viewModelScope.launch {
            _uiState.update { AnalyticsUiState.Loading }
            try {
                val sessionsResult = analyticsRepository.getRecentSessions(learnerId, 10)
                val eventsResult = analyticsRepository.getRecentEvents(learnerId, 50)

                if (sessionsResult.isSuccess && eventsResult.isSuccess) {
                    val sessions = sessionsResult.getOrNull() ?: emptyList()
                    val events = eventsResult.getOrNull() ?: emptyList()

                    val sessionCount = sessions.size
                    val latestScore = sessions.firstOrNull()?.safetyScore ?: 100
                    val harshBrakingCount = sessions.sumOf { it.harshBrakingCount }
                    val overspeedCount = sessions.sumOf { it.overspeedingCount }

                    val analytics = LearnerAnalytics(
                        sessionCount = sessionCount,
                        latestSafetyScore = latestScore,
                        totalHarshBraking = harshBrakingCount,
                        totalOverspeed = overspeedCount,
                        sessions = sessions,
                        events = events
                    )
                    _uiState.update { AnalyticsUiState.Success(analytics) }
                } else {
                    val sErr = sessionsResult.exceptionOrNull()?.message ?: ""
                    val eErr = eventsResult.exceptionOrNull()?.message ?: ""
                    _uiState.update { AnalyticsUiState.Error("Failed to load: $sErr | $eErr") }
                }
            } catch (e: Exception) {
                _uiState.update { AnalyticsUiState.Error(e.message ?: "Unknown error") }
            }
        }
    }
}
