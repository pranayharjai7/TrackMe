package com.trackme.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithEmail(email: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit = {}) {
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Email cannot be empty") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                signInWithEmailImpl(supabase, email.trim(), password)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess(false)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Sign in failed") }
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit = {}) {
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Email cannot be empty") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                signUpWithEmailImpl(supabase, email.trim(), password)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess(true)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Sign up failed") }
            }
        }
    }

    fun signInWithGoogle(onSuccess: (isNewUser: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                signInWithGoogleImpl(supabase)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess(false)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Google sign in failed") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
