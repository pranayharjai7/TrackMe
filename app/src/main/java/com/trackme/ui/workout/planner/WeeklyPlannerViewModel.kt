package com.trackme.ui.workout.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.model.WorkoutPlan
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.GetActivePlanUseCase
import com.trackme.domain.repository.ExerciseRepository
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
    val exerciseCounts: Map<String, Int> = emptyMap(),
    val isCreatingPlan: Boolean = false,
    val showNewPlanDialog: Boolean = false,
    val newPlanName: String = "",
    val routineTextToShare: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeeklyPlannerViewModel @Inject constructor(
    private val getActivePlan: GetActivePlanUseCase,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
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
                .flatMapLatest { days ->
                    _uiState.update { it.copy(days = days) }
                    if (days.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        combine(days.map { day ->
                            workoutRepository.getPlannedExercisesForDay(day.id)
                                .map { exercises -> day.id to exercises.size }
                        }) { pairs -> pairs.toMap() }
                    }
                }
                .collect { counts -> _uiState.update { it.copy(exerciseCounts = counts) } }
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

    fun deleteDay(day: WorkoutDay) {
        viewModelScope.launch { workoutRepository.deleteDay(day) }
    }

    fun prepareRoutineForSharing() {
        val plan = _uiState.value.activePlan ?: return
        val days = _uiState.value.days.sortedBy { it.dayOfWeek.ordinal }
        
        viewModelScope.launch {
            val sb = StringBuilder()
            sb.append("Weekly Routine: ${plan.name}\n")
            sb.append("Generated via TrackMe\n\n")
            
            for (day in days) {
                sb.append("${day.dayOfWeek.name}: ${day.name}\n")
                val planned = workoutRepository.getPlannedExercisesForDay(day.id).first()
                if (planned.isEmpty()) {
                    sb.append("- Rest Day\n")
                } else {
                    val exerciseById = exerciseRepository.getByIds(planned.map { it.exerciseId })
                    for (pe in planned.sortedBy { it.orderIndex }) {
                        val ex = exerciseById[pe.exerciseId]
                        val name = ex?.name ?: "Unknown Exercise"
                        sb.append("- $name: ${pe.targetSets} sets")
                        pe.targetReps?.let { sb.append(" x $it reps") }
                        pe.targetWeightKg?.let { sb.append(" @ ${it}kg") }
                        pe.targetDurationSeconds?.let { sb.append(", ${it}s") }
                        pe.targetDistanceKm?.let { sb.append(", ${it}km") }
                        sb.append("\n")
                    }
                }
                sb.append("\n")
            }
            
            _uiState.update { it.copy(routineTextToShare = sb.toString()) }
        }
    }

    fun onRoutineShared() {
        _uiState.update { it.copy(routineTextToShare = null) }
    }
}
