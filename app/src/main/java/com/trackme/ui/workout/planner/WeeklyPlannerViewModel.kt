package com.trackme.ui.workout.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.model.WorkoutPlan
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.GetActivePlanUseCase
import com.trackme.domain.usecase.SaveWorkoutDayUseCase
import com.trackme.domain.usecase.SaveWorkoutPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyPlannerUiState(
    val activePlan: WorkoutPlan? = null,
    val days: List<WorkoutDay> = emptyList(),
    val isCreatingPlan: Boolean = false,
    val showNewPlanDialog: Boolean = false,
    val newPlanName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeeklyPlannerViewModel @Inject constructor(
    private val getActivePlan: GetActivePlanUseCase,
    private val workoutRepository: WorkoutRepository,
    private val savePlan: SaveWorkoutPlanUseCase,
    private val saveDay: SaveWorkoutDayUseCase,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val userId get() = supabase.auth.currentSessionOrNull()?.user?.id ?: ""

    private val _uiState = MutableStateFlow(WeeklyPlannerUiState())
    val uiState: StateFlow<WeeklyPlannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getActivePlan(userId)
                .onEach { plan -> _uiState.update { it.copy(activePlan = plan) } }
                .flatMapLatest { plan ->
                    if (plan != null) workoutRepository.getDaysForPlan(plan.id)
                    else flowOf(emptyList())
                }
                .collect { days -> _uiState.update { it.copy(days = days) } }
        }
    }

    fun showNewPlanDialog() = _uiState.update { it.copy(showNewPlanDialog = true) }
    fun dismissNewPlanDialog() = _uiState.update { it.copy(showNewPlanDialog = false, newPlanName = "") }
    fun onNewPlanNameChange(name: String) = _uiState.update { it.copy(newPlanName = name) }

    fun createPlan() {
        val name = _uiState.value.newPlanName.trim().ifEmpty { return }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPlan = true) }
            savePlan(userId, name)
            _uiState.update { it.copy(isCreatingPlan = false, showNewPlanDialog = false, newPlanName = "") }
        }
    }

    fun addDay(dayOfWeek: DayOfWeek, name: String) {
        val planId = _uiState.value.activePlan?.id ?: return
        viewModelScope.launch { saveDay(planId, userId, dayOfWeek, name) }
    }
}
