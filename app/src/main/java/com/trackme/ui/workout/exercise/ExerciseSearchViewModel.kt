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
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseSearchUiState(
    val results: List<Exercise> = emptyList(),
    val isAdding: Boolean = false,
    val pendingExercise: Exercise? = null,
    val isLoadingMore: Boolean = false,
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
    private val _limit = MutableStateFlow(30)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _pendingExercise = MutableStateFlow<Exercise?>(null)
    private val _added = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val addedEvent: SharedFlow<Unit> = _added

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<ExerciseSearchUiState> = combine(
        combine(_query.debounce(300), _limit) { query, limit -> query to limit }
            .flatMapLatest { (query, limit) ->
                searchExercises(query, limit).onEach { _isLoadingMore.value = false }
            },
        _pendingExercise,
        _isLoadingMore,
    ) { results, pending, loading ->
        ExerciseSearchUiState(results = results, pendingExercise = pending, isLoadingMore = loading)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseSearchUiState())

    fun onQueryChange(query: String) {
        _isLoadingMore.value = true
        _query.value = query
        _limit.value = 30
    }

    fun loadNextPage() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        _limit.value = _limit.value + 30
    }

    fun onExerciseSelectedForAdd(exercise: Exercise) {
        _pendingExercise.value = exercise
    }

    fun dismissPending() {
        _pendingExercise.value = null
    }

    fun confirmAdd(
        targetSets: Int,
        targetReps: Int?,
        targetWeightKg: Float?,
        targetDurationSeconds: Int?,
        targetDistanceKm: Float?,
        targetSpeedKmh: Float?,
        targetIncline: Float?,
    ) {
        val exercise = _pendingExercise.value ?: return
        if (dayId.isEmpty()) return
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        _pendingExercise.value = null
        viewModelScope.launch {
            val currentCount = workoutRepository.getPlannedExercisesForDay(dayId).firstOrNull()?.size ?: 0
            addExerciseToDay(
                dayId, uid, exercise.id, currentCount,
                targetSets, targetReps, targetWeightKg,
                targetDurationSeconds, targetDistanceKm, targetSpeedKmh, targetIncline,
            )
            _added.emit(Unit)
        }
    }
}
