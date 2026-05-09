package com.trackme.ui.workout.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.GetExerciseByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    val history: List<SessionSet> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val getExerciseById: GetExerciseByIdUseCase,
    private val workoutRepository: WorkoutRepository,
    private val supabase: SupabaseClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val exercise = getExerciseById(exerciseId)
            _uiState.update { it.copy(exercise = exercise, isLoading = false) }
        }
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: ""
        if (userId.isNotEmpty()) {
            viewModelScope.launch {
                workoutRepository.getHistoryForExercise(userId, exerciseId)
                    .collect { sets -> _uiState.update { it.copy(history = sets) } }
            }
        }
    }
}
