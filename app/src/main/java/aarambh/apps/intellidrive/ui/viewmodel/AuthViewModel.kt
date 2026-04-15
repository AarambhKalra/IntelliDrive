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

    fun register(name: String, email: String, password: String, role: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank() || role.isBlank()) {
            _uiState.update { AuthUiState.Error("All fields are required") }
            return
        }
        if (password.length < 6) {
            _uiState.update { AuthUiState.Error("Password must be at least 6 characters") }
            return
        }

        _uiState.update { AuthUiState.Loading }

        viewModelScope.launch {
            repository.register(name.trim(), email.trim(), password, role)
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

    fun resetState() {
        _uiState.update { AuthUiState.Idle }
    }

    fun signOut() {
        repository.signOut()
        _uiState.update { AuthUiState.Idle }
    }
}
