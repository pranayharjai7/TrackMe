package com.trackme.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val healthConnectAvailable: Boolean = false,
    val healthConnectConnected: Boolean = false,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = supabase.auth.currentSessionOrNull()?.user
            val hcAvailable = healthRepository.isHealthConnectAvailable()
            val hcConnected = if (hcAvailable) healthRepository.hasHealthConnectPermissions() else false

            _uiState.update {
                it.copy(
                    displayName = user?.userMetadata?.get("full_name")?.toString()?.trim('"') ?: "",
                    email = user?.email ?: "",
                    healthConnectAvailable = hcAvailable,
                    healthConnectConnected = hcConnected,
                )
            }
        }
    }

    fun syncHealthConnect(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            runCatching { healthRepository.syncFromHealthConnect(userId) }
            _uiState.update { it.copy(isSyncing = false, lastSyncTime = System.currentTimeMillis()) }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            supabase.auth.signOut()
            onDone()
        }
    }
}
