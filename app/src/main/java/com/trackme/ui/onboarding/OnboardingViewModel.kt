package com.trackme.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val useKg: Boolean = true,
    val goal: String = "BUILD_MUSCLE",
    val isSaving: Boolean = false,
)

val PREF_USE_KG = booleanPreferencesKey("use_kg")
val PREF_GOAL = stringPreferencesKey("fitness_goal")
val PREF_ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    fun setUnit(useKg: Boolean) = _uiState.update { it.copy(useKg = useKg) }
    fun setGoal(goal: String) = _uiState.update { it.copy(goal = goal) }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            dataStore.edit { prefs ->
                prefs[PREF_USE_KG] = _uiState.value.useKg
                prefs[PREF_GOAL] = _uiState.value.goal
                prefs[PREF_ONBOARDING_DONE] = true
            }
            _uiState.update { it.copy(isSaving = false) }
            onDone()
        }
    }
}
