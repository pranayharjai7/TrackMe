package com.trackme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.GetTodayWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HomeUiState(
    val todayWorkoutDay: WorkoutDay? = null,
    val recentPRs: List<PersonalRecord> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTodayWorkout: GetTodayWorkoutUseCase,
    private val workoutRepository: WorkoutRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val userId get() = runCatching {
        supabase.auth.currentSessionOrNull()?.user?.id
    }.getOrNull() ?: ""

    val uiState: StateFlow<HomeUiState> = combine(
        getTodayWorkout(userId),
        workoutRepository.getPersonalRecords(userId),
    ) { today, prs ->
        HomeUiState(
            todayWorkoutDay = today,
            recentPRs = prs.take(3),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
