package com.trackme.ui.workout.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.ObservePlannedExercisesWithDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import com.trackme.ui.onboarding.DEFAULT_INPUT_STYLE
import com.trackme.ui.onboarding.PREF_INPUT_STYLE
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DayEditorUiState(
    val plannedExercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val estimatedDurationMinutes: Int = 0,
    val isLoading: Boolean = true,
    val editingExercise: Pair<PlannedExercise, Exercise?>? = null,
    val inputStyle: String = DEFAULT_INPUT_STYLE,
)

/**
 * ViewModel responsible for editing one workout day.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Observe planned exercises with exercise metadata for display.
 * - Handle add/remove/reorder/edit commands from the Compose screen.
 * - Keep optimistic reorder state local while persistence is debounced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayEditorViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val observePlannedExercisesWithDetails: ObservePlannedExercisesWithDetailsUseCase,
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
            observePlannedExercisesWithDetails(dayId)
                .collect { withDetails ->
                    val duration = calculateEstimatedWorkoutDuration(withDetails.map { it.first })
                    _uiState.update { it.copy(plannedExercises = withDetails, estimatedDurationMinutes = duration, isLoading = false) }
                }
        }
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update { it.copy(inputStyle = prefs[PREF_INPUT_STYLE] ?: DEFAULT_INPUT_STYLE) }
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

/**
 * Estimates total workout length in minutes from planned exercise targets.
 *
 * Inputs:
 * - exercises: planned exercises already filtered to the current workout day.
 *
 * Output:
 * - Integer minute estimate used only for planner display.
 */
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
