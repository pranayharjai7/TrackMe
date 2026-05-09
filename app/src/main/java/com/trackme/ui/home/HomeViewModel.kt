package com.trackme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.GetHealthSnapshotsUseCase
import com.trackme.domain.usecase.GetTodayWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val todayWorkoutDay: WorkoutDay? = null,
    val recentPRs: List<PersonalRecord> = emptyList(),
    val weekStrip: List<WorkoutDay?> = emptyList(),
    val streakDays: Int = 0,
    val latestSnapshot: HealthSnapshot? = null,
    val activeSessionDayId: String? = null,
    val isTodaySessionFinished: Boolean = false,
    val displayName: String = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTodayWorkout: GetTodayWorkoutUseCase,
    private val workoutRepository: WorkoutRepository,
    private val getHealthSnapshots: GetHealthSnapshotsUseCase,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _userId = MutableStateFlow(
        supabase.auth.currentSessionOrNull()?.user?.id ?: ""
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = _userId
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val displayName = supabase.auth.currentSessionOrNull()?.user
                ?.userMetadata?.get("full_name")?.toString()?.trim('"') ?: ""

            val weekStripFlow: Flow<List<WorkoutDay?>> = workoutRepository.getActivePlan(uid)
                .flatMapLatest { plan ->
                    if (plan == null) flowOf(List(7) { null })
                    else workoutRepository.getDaysForPlan(plan.id)
                        .map { days ->
                            val byDow = days.associateBy { it.dayOfWeek.ordinal }
                            List(7) { i -> byDow[i] }
                        }
                }

            combine(
                getTodayWorkout(uid),
                workoutRepository.getPersonalRecords(uid),
                workoutRepository.getSessionsSince(uid, thirtyDaysAgo),
                getHealthSnapshots(uid, 30),
                weekStripFlow,
            ) { today, prs, sessions, snapshots, weekStrip ->
                val todayMidnight = normalizeToMidnight(System.currentTimeMillis())
                val completedDayIdsToday = sessions
                    .filter { normalizeToMidnight(it.date) == todayMidnight && it.durationMinutes > 0 }
                    .map { it.dayId }
                    .toSet()
                val activeSession = sessions.firstOrNull {
                    normalizeToMidnight(it.date) == todayMidnight
                        && it.durationMinutes == 0
                        && it.dayId !in completedDayIdsToday
                }
                val isTodaySessionFinished = today != null && sessions.any {
                    normalizeToMidnight(it.date) == todayMidnight && it.dayId == today.id && it.durationMinutes > 0
                }
                HomeUiState(
                    todayWorkoutDay = today,
                    recentPRs = prs.take(3),
                    weekStrip = weekStrip,
                    streakDays = calculateStreak(
                        sessions.filter { it.durationMinutes > 0 }
                            .map { normalizeToMidnight(it.date) }.distinct(),
                        System.currentTimeMillis(),
                    ),
                    latestSnapshot = snapshots.maxByOrNull { it.date },
                    activeSessionDayId = activeSession?.dayId,
                    isTodaySessionFinished = isTodaySessionFinished,
                    displayName = displayName,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}

internal fun calculateStreak(sortedMidnights: List<Long>, nowMs: Long): Int {
    if (sortedMidnights.isEmpty()) return 0
    val sorted = sortedMidnights.sortedDescending()
    val todayMid = normalizeToMidnight(nowMs)
    var streak = 0
    var expected = todayMid
    for (day in sorted) {
        if (day == expected) {
            streak++
            expected -= 86_400_000L
        } else if (day < expected) break
    }
    return streak
}

internal fun normalizeToMidnight(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

