package com.trackme.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.data.health.HcSdkStatus
import com.trackme.domain.repository.HealthRepository
import com.trackme.domain.usecase.ClearLocalUserDataUseCase
import com.trackme.ui.onboarding.PREF_GOAL
import com.trackme.ui.onboarding.PREF_INPUT_STYLE
import com.trackme.ui.onboarding.PREF_USE_KG
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val hcStatus: HcSdkStatus = HcSdkStatus.NEEDS_INSTALL,
    val healthConnectConnected: Boolean = false,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false,
    val useKg: Boolean = true,
    val fitnessGoal: String = "BUILD_MUSCLE",
    val inputStyle: String = "TAP_EXPAND",
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val supabase: SupabaseClient,
    private val dataStore: DataStore<Preferences>,
    private val clearLocalUserData: ClearLocalUserDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = supabase.auth.currentSessionOrNull()?.user
            val hcStatus = healthRepository.getHealthConnectStatus()
            val hcConnected = if (hcStatus == HcSdkStatus.AVAILABLE) healthRepository.hasHealthConnectPermissions() else false
            val avatarUrl = user?.userMetadata?.get("avatar_url")?.toString()?.trim('"')
                ?: user?.userMetadata?.get("picture")?.toString()?.trim('"')
            _uiState.update {
                it.copy(
                    displayName = user?.userMetadata?.get("full_name")?.toString()?.trim('"') ?: "",
                    email = user?.email ?: "",
                    avatarUrl = avatarUrl,
                    hcStatus = hcStatus,
                    healthConnectConnected = hcConnected,
                )
            }
        }

        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update {
                    it.copy(
                        useKg = prefs[PREF_USE_KG] ?: true,
                        fitnessGoal = prefs[PREF_GOAL] ?: "BUILD_MUSCLE",
                        inputStyle = prefs[PREF_INPUT_STYLE] ?: "TAP_EXPAND",
                    )
                }
            }
        }
    }

    fun onPermissionResult(grantedPermissions: Set<String>) {
        viewModelScope.launch {
            val required = healthRepository.getRequiredPermissions()
            val allGranted = required.all { it in grantedPermissions }
            if (allGranted) {
                _uiState.update { it.copy(healthConnectConnected = true) }
                syncHealthConnect()
            } else {
                // Some permissions denied — recheck actual state
                recheckHealthConnect()
            }
        }
    }

    fun recheckHealthConnect() {
        viewModelScope.launch {
            val hcStatus = healthRepository.getHealthConnectStatus()
            val hcConnected = if (hcStatus == HcSdkStatus.AVAILABLE) healthRepository.hasHealthConnectPermissions() else false
            val wasConnected = _uiState.value.healthConnectConnected
            _uiState.update { it.copy(hcStatus = hcStatus, healthConnectConnected = hcConnected) }
            if (!wasConnected && hcConnected) {
                syncHealthConnect()
            }
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
            _uiState.update { it.copy(isSyncing = true) }
            val result = runCatching { healthRepository.syncFromHealthConnect(userId) }
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncTime = if (result.isSuccess) System.currentTimeMillis() else it.lastSyncTime,
                )
            }
        }
    }

    fun savePreferences(useKg: Boolean, fitnessGoal: String, inputStyle: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PREF_USE_KG] = useKg
                prefs[PREF_GOAL] = fitnessGoal
                prefs[PREF_INPUT_STYLE] = inputStyle
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            clearLocalUserData()
            supabase.auth.signOut()
            onDone()
        }
    }
}
