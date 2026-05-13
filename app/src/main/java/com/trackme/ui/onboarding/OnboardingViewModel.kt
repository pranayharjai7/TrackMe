package com.trackme.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val useKg: Boolean = true,
    val goal: String = DEFAULT_FITNESS_GOAL,
    val isSaving: Boolean = false,
    val isAlreadyDone: Boolean = false,
    val currentStep: Int = 1,
)

val PREF_USE_KG = booleanPreferencesKey("use_kg")
val PREF_GOAL = stringPreferencesKey("fitness_goal")
val PREF_INPUT_STYLE = stringPreferencesKey("input_style")
val PREF_ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")

const val DEFAULT_USE_KG = true
const val DEFAULT_FITNESS_GOAL = "BUILD_MUSCLE"
const val DEFAULT_INPUT_STYLE = "TAP_EXPAND"

/**
 * ViewModel responsible for first-run app preference setup.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Read onboarding completion state from DataStore.
 * - Collect the user's preferred units and fitness goal.
 * - Persist onboarding completion without touching workout or backend state.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            if (prefs[PREF_ONBOARDING_DONE] == true) {
                _uiState.update {
                    it.copy(
                        isAlreadyDone = true,
                        useKg = prefs[PREF_USE_KG] ?: DEFAULT_USE_KG,
                        goal = prefs[PREF_GOAL] ?: DEFAULT_FITNESS_GOAL,
                    )
                }
            }
        }
    }

    fun setUnit(useKg: Boolean) = _uiState.update { it.copy(useKg = useKg) }
    fun setGoal(goal: String) = _uiState.update { it.copy(goal = goal) }
    fun nextStep() = _uiState.update { it.copy(currentStep = it.currentStep + 1) }

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
