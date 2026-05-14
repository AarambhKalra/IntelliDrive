package aarambh.apps.intellidrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aarambh.apps.intellidrive.data.model.User
import aarambh.apps.intellidrive.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI state ─────────────────────────────────────────────────────────────────

sealed interface AuthUiState {
    /** No operation in progress. */
    data object Idle : AuthUiState

    /** Network / Firebase call is running. */
    data object Loading : AuthUiState

    /** Auth + Firestore fetch succeeded; carries the full user record. */
    data class Success(val user: User) : AuthUiState

    /** Something went wrong; carries a human-readable message. */
    data class Error(val message: String) : AuthUiState
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ── Login ─────────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { AuthUiState.Error("Email and password cannot be empty") }
            return
        }

        _uiState.update { AuthUiState.Loading }

        viewModelScope.launch {
            repository.login(email.trim(), password)
                .onSuccess { user -> _uiState.update { AuthUiState.Success(user) } }
                .onFailure { ex ->
                    _uiState.update {
                        AuthUiState.Error(ex.message ?: "Login failed. Please try again.")
                    }
                }
        }
    }

    // ── Register ──────────────────────────────────────────────────────────────

    fun register(
        name: String,
        email: String,
        password: String,
        age: String,
        role: String,
        childId: String,
        isConfirmed: Boolean
    ) {
        val ageInt = age.toIntOrNull() ?: 0

        if (name.isBlank() || email.isBlank() || password.isBlank() || role.isBlank()) {
            _uiState.update { AuthUiState.Error("All fields are required") }
            return
        }

        if (role == "student") {
            if (ageInt < 16) {
                _uiState.update { AuthUiState.Error("You must be at least 16 years old to register as a student") }
                return
            }
            if (!isConfirmed) {
                _uiState.update { AuthUiState.Error("Please confirm that the information provided is true") }
                return
            }
        }

        if (role == "parent" && childId.isBlank()) {
            _uiState.update { AuthUiState.Error("Child ID is required for parent accounts") }
            return
        }
        if (password.length < 6) {
            _uiState.update { AuthUiState.Error("Password must be at least 6 characters") }
            return
        }

        _uiState.update { AuthUiState.Loading }

        viewModelScope.launch {
            repository.register(
                name.trim(),
                email.trim().lowercase(),
                password,
                ageInt,
                role,
                childId.trim()
            )
                .onSuccess { user -> _uiState.update { AuthUiState.Success(user) } }
                .onFailure { ex ->
                    _uiState.update {
                        AuthUiState.Error(ex.message ?: "Registration failed. Please try again.")
                    }
                }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Auto-login if a Firebase session already exists. */
    fun checkExistingSession() {
        val uid = repository.currentUserId ?: return
        _uiState.update { AuthUiState.Loading }
        viewModelScope.launch {
            runCatching { repository.fetchUser(uid) }
                .onSuccess { user -> _uiState.update { AuthUiState.Success(user) } }
                .onFailure { _uiState.update { AuthUiState.Idle } }
        }
    }

    /** Refreshes the user's data from Firestore (e.g., after a training day graduation). */
    fun refreshUser() {
        val uid = repository.currentUserId ?: return
        viewModelScope.launch {
            runCatching { repository.fetchUser(uid) }
                .onSuccess { user -> _uiState.update { AuthUiState.Success(user) } }
        }
    }

    fun resetState() {
        _uiState.update { AuthUiState.Idle }
    }

    fun signOut() {
        repository.signOut()
        _uiState.update { AuthUiState.Idle }
    }

    /** Manually updates the student's training day. */
    fun updateTrainingDay(newDay: Int) {
        val uid = repository.currentUserId ?: return
        viewModelScope.launch {
            // We use the SessionRepository logic here via the AuthRepository if we wanted, 
            // but for simplicity let's just use Firestore directly or add to repository.
            // Actually, SessionRepository already has graduateTrainingDay, let's add a setter.
            aarambh.apps.intellidrive.data.repository.SessionRepository().setTrainingDay(uid, newDay)
                .onSuccess { refreshUser() }
        }
    }
}
