package com.trackme.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.data.auth.SessionManager
import com.trackme.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel responsible for authentication screen state.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Validate user-entered credentials before calling Supabase.
 * - Execute email and Google sign-in/sign-up flows.
 * - Trigger sync after sign-in so local Room data catches up with the backend.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithEmail(email: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit = {}) {
        val validationError = validateEmailPassword(email, password)
        if (validationError != null) {
            setError(validationError)
            return
        }

        runAuthRequest(
            fallbackError = "Sign in failed",
            onSuccess = {
                onSuccess(false)
            },
        ) {
            signInWithEmailImpl(supabase, email.trim(), password)
            sessionManager.registerCurrentSession()
            syncManager.runInitialSync()
        }
    }

    fun signUpWithEmail(email: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit = {}) {
        val validationError = validateEmailPassword(email, password)
        if (validationError != null) {
            setError(validationError)
            return
        }

        runAuthRequest(
            fallbackError = "Sign up failed",
            onSuccess = { onSuccess(true) },
        ) {
            signUpWithEmailImpl(supabase, email.trim(), password)
            if (supabase.auth.currentSessionOrNull() != null) {
                sessionManager.registerCurrentSession()
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: (isNewUser: Boolean) -> Unit = {}) {
        runAuthRequest(
            fallbackError = "Google sign in failed",
            onSuccess = {
                onSuccess(false)
            },
        ) {
            signInWithGoogleIdTokenImpl(supabase, idToken)
            sessionManager.registerCurrentSession()
            syncManager.runInitialSync()
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun setError(msg: String) = _uiState.update { it.copy(isLoading = false, error = msg) }

    /**
     * Runs one Supabase auth request and owns loading/error transitions.
     *
     * Side effects:
     * - Updates AuthUiState before and after the request.
     * - Invokes onSuccess only after Supabase confirms success.
     */
    private fun runAuthRequest(
        fallbackError: String,
        onSuccess: () -> Unit,
        request: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { request() }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: fallbackError) }
            }
        }
    }

    private fun validateEmailPassword(email: String, password: String): String? = when {
        email.isBlank() -> "Email cannot be empty"
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
