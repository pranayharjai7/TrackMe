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
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
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
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val fourWeeksAgo = System.currentTimeMillis() - 28L * 24 * 60 * 60 * 1000
            combine(
                getPersonalRecords(uid),
                getHealthSnapshots(uid, 30),
                workoutRepository.getSetsSince(uid, thirtyDaysAgo),
                getMuscleVolume(uid, fourWeeksAgo),
                _selectedExerciseId,
            ) { prs, snapshots, sets, muscleVol, selectedId ->
                val volumeByDay = sets.groupBy { normalizeToDay(it.updatedAt) }
                    .mapValues { (_, daySets) -> daySets.size }
                
                // Calculate Progress State
                val now = System.currentTimeMillis()
                val fourteenDaysAgo = now - 14L * 24 * 60 * 60 * 1000
                val recentSets = sets.filter { it.updatedAt >= fourteenDaysAgo }
                val olderSets = sets.filter { it.updatedAt < fourteenDaysAgo }
                
                val state = when {
                    sets.size < 10 -> ProgressState.UNCHARTED
                    recentSets.size > olderSets.size * 1.15 -> ProgressState.MOMENTUM
                    recentSets.size < olderSets.size * 0.75 -> ProgressState.RECOVERY
                    else -> ProgressState.MAINTENANCE
                }

                val insight = when (state) {
                    ProgressState.MOMENTUM -> ProgressInsight("Momentum Building", "Your training volume is up ${(recentSets.size * 100f / olderSets.size.coerceAtLeast(1).toFloat()).toInt() - 100}% compared to the previous two weeks. Keep riding this wave!")
                    ProgressState.MAINTENANCE -> ProgressInsight("Steady Consistency", "You're maintaining a solid baseline. Consistent effort is the key to long-term gains.")
                    ProgressState.RECOVERY -> ProgressInsight("Recovery Phase", "Your volume has decreased recently. If you're resting, enjoy it. If not, it's time to get back on track.")
                    ProgressState.UNCHARTED -> ProgressInsight("Just Beginning", "Log more workouts to unlock deep insights into your progress trajectory.")
                }

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

private fun normalizeToDay(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
