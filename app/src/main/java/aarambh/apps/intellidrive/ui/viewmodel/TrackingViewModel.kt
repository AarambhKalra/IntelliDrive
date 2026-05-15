package aarambh.apps.intellidrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aarambh.apps.intellidrive.data.model.LiveLocation
import aarambh.apps.intellidrive.data.repository.SessionRepository
import aarambh.apps.intellidrive.data.repository.TrackingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data class Success(val location: LiveLocation?, val hasActiveSession: Boolean) : TrackingUiState
    data class Error(val message: String) : TrackingUiState
}

class TrackingViewModel(
    private val trackingRepository: TrackingRepository = TrackingRepository(),
    private val sessionRepository: SessionRepository = SessionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrackingUiState>(TrackingUiState.Loading)
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun startTracking(learnerId: String) {
        viewModelScope.launch {
            _uiState.update { TrackingUiState.Loading }
            
            // Assuming we fetch the learner's active session first
            sessionRepository.getDriveSessions(learnerId)
                .onSuccess { sessions ->
                    val activeSession = sessions.find { !it.wasCompleted && it.status == "active" }
                    if (activeSession != null) {
                        trackSession(activeSession.sessionId)
                    } else {
                        // Fallback: show last known session or no active session
                        val lastSession = sessions.maxByOrNull { it.endTime }
                        if (lastSession != null) {
                            trackSession(lastSession.sessionId, false)
                        } else {
                            _uiState.update { TrackingUiState.Success(null, false) }
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { TrackingUiState.Error(error.message ?: "Failed to find session") }
                }
        }
    }

    private fun trackSession(sessionId: String, isActive: Boolean = true) {
        viewModelScope.launch {
            trackingRepository.getLiveLocation(sessionId).collect { location ->
                _uiState.update { TrackingUiState.Success(location, isActive) }
            }
        }
    }
}
