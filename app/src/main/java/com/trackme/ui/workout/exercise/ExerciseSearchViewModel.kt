package com.trackme.ui.workout.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.SearchExercisesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseSearchUiState(
    val results: List<Exercise> = emptyList(),
    val isAdding: Boolean = false,
)

@HiltViewModel
class ExerciseSearchViewModel @Inject constructor(
    private val searchExercises: SearchExercisesUseCase,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val workoutRepository: WorkoutRepository,
    private val supabase: SupabaseClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dayId: String = savedStateHandle["dayId"] ?: ""
    private val _query = MutableStateFlow("")
    private val _added = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val addedEvent: SharedFlow<Unit> = _added

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<ExerciseSearchUiState> = _query
        .debounce(300)
        .flatMapLatest { query -> searchExercises(query) }
        .map { results -> ExerciseSearchUiState(results = results) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseSearchUiState())

    fun onQueryChange(query: String) { _query.value = query }

    fun addExercise(exerciseId: String) {
        if (dayId.isEmpty()) return
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        viewModelScope.launch {
            val currentCount = workoutRepository.getPlannedExercisesForDay(dayId).firstOrNull()?.size ?: 0
            addExerciseToDay(dayId, uid, exerciseId, currentCount)
            _added.emit(Unit)
        }
    }
}
