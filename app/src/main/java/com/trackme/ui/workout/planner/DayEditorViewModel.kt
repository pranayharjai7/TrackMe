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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DayEditorUiState(
    val plannedExercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
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
            workoutRepository.getPlannedExercisesForDay(dayId)
                .flatMapLatest { planned ->
                    flow {
                        val withDetails = coroutineScope {
                            planned.map { pe ->
                                async { pe to exerciseRepository.getById(pe.exerciseId) }
                            }.awaitAll()
                        }
                        emit(withDetails)
                    }
                }
                .collect { withDetails ->
                    _uiState.update { it.copy(plannedExercises = withDetails, isLoading = false) }
                }
        }
    }

    fun removeExercise(pe: PlannedExercise) {
        viewModelScope.launch { workoutRepository.removePlannedExercise(pe) }
    }

    fun addExercise(exerciseId: String) {
        viewModelScope.launch {
            val nextIndex = _uiState.value.plannedExercises.size
            addExerciseToDay(dayId, userId, exerciseId, nextIndex)
        }
    }
}
