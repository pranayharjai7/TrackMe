package com.trackme.ui.workout.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.usecase.SearchExercisesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ExerciseSearchUiState(
    val query: String = "",
    val results: List<Exercise> = emptyList(),
)

@HiltViewModel
class ExerciseSearchViewModel @Inject constructor(
    private val searchExercises: SearchExercisesUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<ExerciseSearchUiState> = _query
        .debounce(300)
        .flatMapLatest { query -> searchExercises(query) }
        .map { results -> ExerciseSearchUiState(query = _query.value, results = results) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseSearchUiState())

    fun onQueryChange(query: String) { _query.value = query }
}
