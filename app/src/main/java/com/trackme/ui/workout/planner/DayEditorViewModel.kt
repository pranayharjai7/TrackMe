package com.trackme.ui.workout.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DayEditorUiState(
    val plannedExercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val estimatedDurationMinutes: Int = 0,
    val isLoading: Boolean = true,
    val editingExercise: Pair<PlannedExercise, Exercise?>? = null,
    val inputStyle: String = "TAP_EXPAND",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayEditorViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val supabase: SupabaseClient,
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])
    private val userId get() = supabase.auth.currentSessionOrNull()?.user?.id ?: ""

    private val _uiState = MutableStateFlow(DayEditorUiState())
    val uiState: StateFlow<DayEditorUiState> = _uiState.asStateFlow()

    private var reorderJob: Job? = null

    init {
        viewModelScope.launch {
            workoutRepository.getPlannedExercisesForDay(dayId)
                .flatMapLatest { planned ->
                    flow {
                        val exerciseById = exerciseRepository.getByIds(planned.map { it.exerciseId })
                        val withDetails = planned.map { pe ->
                            pe to exerciseById[pe.exerciseId]
                        }
                        emit(withDetails)
                    }
                }
                .collect { withDetails ->
                    val duration = calculateEstimatedWorkoutDuration(withDetails.map { it.first })
                    _uiState.update { it.copy(plannedExercises = withDetails, estimatedDurationMinutes = duration, isLoading = false) }
                }
        }
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update { it.copy(inputStyle = prefs[com.trackme.ui.onboarding.PREF_INPUT_STYLE] ?: "TAP_EXPAND") }
            }
        }
    }

    fun removeExercise(pe: PlannedExercise) {
        viewModelScope.launch { workoutRepository.removePlannedExercise(pe) }
    }

    fun reorderExercises(from: Int, to: Int) {
        val current = _uiState.value.plannedExercises.toMutableList()
        if (from < 0 || to < 0 || from >= current.size || to >= current.size) return
        val moved = current.removeAt(from)
        current.add(to, moved)
        
        val reordered = current.mapIndexed { index, (pe, exercise) ->
            pe.copy(orderIndex = index) to exercise
        }
        _uiState.update { it.copy(plannedExercises = reordered) }
        
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(500)
            workoutRepository.reorderExercises(reordered.map { it.first })
        }
    }

    fun addExercise(exerciseId: String) {
        viewModelScope.launch {
            val nextIndex = _uiState.value.plannedExercises.size
            addExerciseToDay(dayId, userId, exerciseId, nextIndex)
        }
    }

    fun startEditExercise(pe: PlannedExercise) {
        val pair = _uiState.value.plannedExercises.find { it.first.id == pe.id }
        _uiState.update { it.copy(editingExercise = pair) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editingExercise = null) }
    }

    fun saveEditedParams(
        pe: PlannedExercise,
        targetSets: Int,
        targetReps: Int?,
        targetWeightKg: Float?,
        targetDurationSeconds: Int?,
        targetDistanceKm: Float?,
        targetSpeedKmh: Float?,
        targetIncline: Float?,
    ) {
        dismissEdit()
        viewModelScope.launch {
            workoutRepository.addPlannedExercise(
                pe.copy(
                    targetSets = targetSets,
                    targetReps = targetReps,
                    targetWeightKg = targetWeightKg,
                    targetDurationSeconds = targetDurationSeconds,
                    targetDistanceKm = targetDistanceKm,
                    targetSpeedKmh = targetSpeedKmh,
                    targetIncline = targetIncline,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }
}

internal fun calculateEstimatedWorkoutDuration(exercises: List<PlannedExercise>): Int {
    if (exercises.isEmpty()) return 0
    var totalSeconds = 0
    for (exercise in exercises) {
        val sets = exercise.targetSets
        val restSeconds = 90 // average rest per set
        
        val timePerSet = when {
            exercise.targetDurationSeconds != null -> exercise.targetDurationSeconds
            exercise.targetReps != null -> exercise.targetReps * 4 // roughly 4 seconds per rep
            else -> 60 // fallback
        }
        
        totalSeconds += sets * (timePerSet + restSeconds)
    }
    return totalSeconds / 60
}
