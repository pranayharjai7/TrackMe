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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DayEditorUiState(
    val plannedExercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DayEditorViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val supabase: SupabaseClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])
    private val userId get() = supabase.auth.currentSessionOrNull()?.user?.id ?: ""

    private val _uiState = MutableStateFlow(DayEditorUiState())
    val uiState: StateFlow<DayEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepository.getPlannedExercisesForDay(dayId).collect { planned ->
                val withDetails = planned.map { pe ->
                    pe to exerciseRepository.getById(pe.exerciseId)
                }
                _uiState.update { it.copy(plannedExercises = withDetails, isLoading = false) }
            }
        }
    }

    fun removeExercise(pe: PlannedExercise) {
        viewModelScope.launch { workoutRepository.removePlannedExercise(pe) }
    }

    fun addExercise(exerciseId: String) {
        val nextIndex = _uiState.value.plannedExercises.size
        viewModelScope.launch { addExerciseToDay(dayId, userId, exerciseId, nextIndex) }
    }
}
