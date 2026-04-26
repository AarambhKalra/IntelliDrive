package aarambh.apps.intellidrive.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import aarambh.apps.intellidrive.data.model.DriveSession
import aarambh.apps.intellidrive.data.model.LiveLocation
import aarambh.apps.intellidrive.data.repository.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository()

    private val _liveLocation = MutableStateFlow<LiveLocation?>(null)
    val liveLocation: StateFlow<LiveLocation?> = _liveLocation.asStateFlow()

    private var liveObserverJob: Job? = null

    private var currentSessionId: String? = null

    private val _sessionCompletedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val sessionCompletedEvent: kotlinx.coroutines.flow.SharedFlow<Int> = _sessionCompletedEvent.asSharedFlow()

    // ── Student Actions ───────────────────────────────────────────────────────

    fun startSession(studentId: String, routeId: String, trainingDay: Int) {
        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        val session = DriveSession(
            sessionId = sessionId,
            studentId = studentId,
            routeId = routeId,
            trainingDay = trainingDay,
            startTime = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveDriveSession(session)
        }
    }

    fun completeSession(studentId: String) {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            repository.saveDriveSession(DriveSession(
                sessionId = sessionId,
                studentId = studentId,
                endTime = System.currentTimeMillis(),
                wasCompleted = true
            ))
            repository.graduateTrainingDay(studentId).onSuccess { newDay ->
                _sessionCompletedEvent.tryEmit(newDay)
            }
        }
        currentSessionId = null
    }

    // ── Parent / Instructor Actions ──────────────────────────────────────────

    fun startObservingStudent(studentId: String) {
        liveObserverJob?.cancel()
        liveObserverJob = viewModelScope.launch {
            repository.observeLiveLocation(studentId).collect { result ->
                result.onSuccess { loc ->
                    _liveLocation.value = loc
                }
            }
        }
    }

    fun stopObservingStudent() {
        liveObserverJob?.cancel()
        liveObserverJob = null
        _liveLocation.value = null
    }
}
