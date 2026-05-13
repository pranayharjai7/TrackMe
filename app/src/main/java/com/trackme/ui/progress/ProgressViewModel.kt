package com.trackme.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.SessionSet
import com.trackme.domain.usecase.GetHealthSnapshotsUseCase
import com.trackme.domain.usecase.GetMuscleVolumeUseCase
import com.trackme.domain.usecase.GetPersonalRecordsUseCase
import com.trackme.domain.usecase.MuscleVolume
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.utils.millisDaysAgo
import com.trackme.utils.startOfLocalDayMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class ProgressState {
    MOMENTUM, MAINTENANCE, RECOVERY, UNCHARTED
}

data class ProgressInsight(val title: String, val description: String)

data class ProgressUiState(
    val dashboardState: ProgressState = ProgressState.UNCHARTED,
    val primaryInsight: ProgressInsight? = null,
    val personalRecords: List<PersonalRecord> = emptyList(),
    val weightHistory: List<HealthSnapshot> = emptyList(),
    val latestSnapshot: HealthSnapshot? = null,
    val sessionVolumes: Map<Long, Int> = emptyMap(),
    val muscleVolume: List<MuscleVolume> = emptyList(),
    val exerciseOptions: List<String> = emptyList(),
    val selectedExerciseId: String? = null,
    val isLoading: Boolean = true,
)

/**
 * ViewModel responsible for progress analytics.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Combine personal records, workout volume, muscle volume, and health snapshots.
 * - Derive progress dashboard state before data reaches the Compose UI.
 * - Expose selected-exercise strength history as a separate stream for charts.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getPersonalRecords: GetPersonalRecordsUseCase,
    private val getHealthSnapshots: GetHealthSnapshotsUseCase,
    private val getMuscleVolume: GetMuscleVolumeUseCase,
    private val workoutRepository: WorkoutRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _userId = MutableStateFlow(supabase.auth.currentSessionOrNull()?.user?.id ?: "")
    private val _selectedExerciseId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState> = _userId
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            val thirtyDaysAgo = millisDaysAgo(30)
            val fourWeeksAgo = millisDaysAgo(28)
            combine(
                getPersonalRecords(uid),
                getHealthSnapshots(uid, 30),
                workoutRepository.getSetsSince(uid, thirtyDaysAgo),
                getMuscleVolume(uid, fourWeeksAgo),
                _selectedExerciseId,
            ) { prs, snapshots, sets, muscleVol, selectedId ->
                val volumeByDay = sets.groupBy { startOfLocalDayMillis(it.updatedAt) }
                    .mapValues { (_, daySets) -> daySets.size }
                
                val now = System.currentTimeMillis()
                val fourteenDaysAgo = millisDaysAgo(14, now)
                val recentSets = sets.filter { it.updatedAt >= fourteenDaysAgo }
                val olderSets = sets.filter { it.updatedAt < fourteenDaysAgo }
                val state = deriveProgressState(sets.size, recentSets.size, olderSets.size)
                val insight = state.toInsight(recentSets.size, olderSets.size)

                val options = prs.map { it.exerciseId }.distinct().sorted()
                val effectiveSelectedId = selectedId ?: prs.maxByOrNull { it.maxWeightKg }?.exerciseId
                ProgressUiState(
                    dashboardState = state,
                    primaryInsight = insight,
                    personalRecords = prs,
                    weightHistory = snapshots,
                    latestSnapshot = snapshots.maxByOrNull { it.date },
                    sessionVolumes = volumeByDay,
                    muscleVolume = muscleVol,
                    exerciseOptions = options,
                    selectedExerciseId = effectiveSelectedId,
                    isLoading = false,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val strengthHistory: StateFlow<List<SessionSet>> = combine(
        _userId.filter { it.isNotEmpty() },
        _selectedExerciseId,
    ) { uid, selId -> uid to selId }
        .flatMapLatest { (uid, selId) ->
            if (selId != null) {
                workoutRepository.getHistoryForExercise(uid, selId)
            } else {
                uiState.mapNotNull { it.selectedExerciseId }
                    .distinctUntilChanged()
                    .flatMapLatest { exerciseId -> workoutRepository.getHistoryForExercise(uid, exerciseId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectExercise(exerciseId: String) {
        _selectedExerciseId.value = exerciseId
    }
}

/**
 * Converts raw set counts into a UI dashboard state.
 *
 * Inputs:
 * - totalSets: all completed sets in the 30-day analysis window.
 * - recentSetCount: completed sets in the most recent 14 days.
 * - olderSetCount: completed sets in the previous part of the window.
 */
private fun deriveProgressState(
    totalSets: Int,
    recentSetCount: Int,
    olderSetCount: Int,
): ProgressState = when {
    totalSets < 10 -> ProgressState.UNCHARTED
    recentSetCount > olderSetCount * 1.15 -> ProgressState.MOMENTUM
    recentSetCount < olderSetCount * 0.75 -> ProgressState.RECOVERY
    else -> ProgressState.MAINTENANCE
}

private fun ProgressState.toInsight(recentSetCount: Int, olderSetCount: Int): ProgressInsight = when (this) {
    ProgressState.MOMENTUM -> ProgressInsight(
        "Momentum Building",
        "Your training volume is up ${(recentSetCount * 100f / olderSetCount.coerceAtLeast(1).toFloat()).toInt() - 100}% compared to the previous two weeks. Keep riding this wave!",
    )
    ProgressState.MAINTENANCE -> ProgressInsight(
        "Steady Consistency",
        "You're maintaining a solid baseline. Consistent effort is the key to long-term gains.",
    )
    ProgressState.RECOVERY -> ProgressInsight(
        "Recovery Phase",
        "Your volume has decreased recently. If you're resting, enjoy it. If not, it's time to get back on track.",
    )
    ProgressState.UNCHARTED -> ProgressInsight(
        "Just Beginning",
        "Log more workouts to unlock deep insights into your progress trajectory.",
    )
}
