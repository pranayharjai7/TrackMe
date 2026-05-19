package com.trackme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.repository.HealthRepository
import com.trackme.domain.usecase.GetHealthSnapshotsUseCase
import com.trackme.domain.usecase.GetTodayWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.trackme.utils.MILLIS_PER_DAY
import com.trackme.utils.millisDaysAgo
import com.trackme.utils.startOfLocalDayMillis
import javax.inject.Inject

enum class HomeDashboardState {
    REST_RECOVERY,
    PRE_WORKOUT,
    ACTIVE_SESSION,
    TRIUMPH
}

data class HealthInsight(
    val title: String,
    val description: String,
    val score: Int
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val dashboardState: HomeDashboardState = HomeDashboardState.REST_RECOVERY,
    val healthInsight: HealthInsight? = null,
    val todayWorkoutDay: WorkoutDay? = null,
    val recentPRs: List<PersonalRecord> = emptyList(),
    val weekStrip: List<WorkoutDay?> = emptyList(),
    val streakDays: Int = 0,
    val latestSnapshot: HealthSnapshot? = null,
    val activeSessionDayId: String? = null,
    val isTodaySessionFinished: Boolean = false,
    val displayName: String = "",
)

/**
 * ViewModel responsible for the home dashboard.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Combine Supabase auth state with workout, progress, and health data.
 * - Derive dashboard mode without letting the Compose screen perform business logic.
 * - Expose only UI-ready state to HomeScreen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTodayWorkout: GetTodayWorkoutUseCase,
    private val workoutRepository: WorkoutRepository,
    private val getHealthSnapshots: GetHealthSnapshotsUseCase,
    private val healthRepository: HealthRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = supabase.auth.sessionStatus
        .onStart {
            supabase.auth.awaitInitialization()
            emit(supabase.auth.sessionStatus.value)
        }
        .distinctUntilChanged()
        .flatMapLatest { status ->
            val session = (status as? SessionStatus.Authenticated)?.session
                ?: return@flatMapLatest flowOf(HomeUiState(isLoading = status is SessionStatus.LoadingFromStorage))

            val uid = session.user?.id.orEmpty()
            if (uid.isEmpty()) return@flatMapLatest flowOf(HomeUiState(isLoading = false))

            viewModelScope.launch {
                runCatching { healthRepository.syncFromHealthConnect(uid) }
            }

            val thirtyDaysAgo = millisDaysAgo(30)
            val displayName = session.user
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
                val todayMidnight = startOfLocalDayMillis(System.currentTimeMillis())
                val completedDayIdsToday = sessions
                    .filter { startOfLocalDayMillis(it.date) == todayMidnight && it.durationMinutes > 0 }
                    .map { it.dayId }
                    .toSet()
                val validDayIds = weekStrip.filterNotNull().map { it.id }.toSet()
                val activeSession = sessions.firstOrNull {
                    startOfLocalDayMillis(it.date) == todayMidnight
                        && it.durationMinutes == 0
                        && it.dayId !in completedDayIdsToday
                        && it.dayId in validDayIds
                }
                val isTodaySessionFinished = today != null && sessions.any {
                    startOfLocalDayMillis(it.date) == todayMidnight && it.dayId == today.id && it.durationMinutes > 0
                }
                
                val dashboardState = when {
                    activeSession != null -> HomeDashboardState.ACTIVE_SESSION
                    isTodaySessionFinished -> HomeDashboardState.TRIUMPH
                    today != null -> HomeDashboardState.PRE_WORKOUT
                    else -> HomeDashboardState.REST_RECOVERY
                }
                
                val latestSnapshot = snapshots.maxByOrNull { it.date }
                val healthInsight = latestSnapshot?.toHealthInsight(dashboardState)

                HomeUiState(
                    isLoading = false,
                    dashboardState = dashboardState,
                    healthInsight = healthInsight,
                    todayWorkoutDay = today,
                    recentPRs = prs.take(3),
                    weekStrip = weekStrip,
                    streakDays = calculateStreak(
                        sessions.filter { it.durationMinutes > 0 }
                            .map { startOfLocalDayMillis(it.date) }.distinct(),
                        System.currentTimeMillis(),
                    ),
                    latestSnapshot = latestSnapshot,
                    activeSessionDayId = activeSession?.dayId,
                    isTodaySessionFinished = isTodaySessionFinished,
                    displayName = displayName,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun restartFinishedWorkout(dayId: String) {
        val status = supabase.auth.sessionStatus.value
        val session = (status as? SessionStatus.Authenticated)?.session ?: return
        val uid = session.user?.id.orEmpty()
        if (uid.isEmpty()) return

        viewModelScope.launch {
            val todayMidnight = startOfLocalDayMillis(System.currentTimeMillis())
            val sessions = workoutRepository.getSessionsSince(uid, todayMidnight).first()
            val finishedSession = sessions.firstOrNull {
                startOfLocalDayMillis(it.date) == todayMidnight && it.dayId == dayId && it.durationMinutes > 0
            }
            if (finishedSession != null) {
                workoutRepository.finishSession(finishedSession.id, 0)
            }
        }
    }
}

/**
 * Calculates consecutive local-day workout completions ending today.
 *
 * Inputs:
 * - sortedMidnights: any order of local midnight timestamps for completed sessions.
 * - nowMs: the current time, injectable for deterministic tests.
 */
internal fun calculateStreak(sortedMidnights: List<Long>, nowMs: Long): Int {
    if (sortedMidnights.isEmpty()) return 0
    val sorted = sortedMidnights.sortedDescending()
    val todayMid = startOfLocalDayMillis(nowMs)
    var streak = 0
    var expected = todayMid
    for (day in sorted) {
        if (day == expected) {
            streak++
            expected -= MILLIS_PER_DAY
        } else if (day < expected) break
    }
    return streak
}

private fun HealthSnapshot.toHealthInsight(dashboardState: HomeDashboardState): HealthInsight =
    if (dashboardState == HomeDashboardState.REST_RECOVERY) {
        val steps = steps ?: 0L
        HealthInsight(
            title = "Recovery & Readiness",
            description = "You've taken ${"%,d".format(steps)} steps today. Keep active but prioritize rest.",
            score = (steps / 100).toInt().coerceIn(0, 100),
        )
    } else {
        val calories = activeCaloriesBurned ?: 0f
        HealthInsight(
            title = "Active Energy",
            description = "You've burned ${calories.toInt()} kcal today. Fuel your body for the workout!",
            score = (calories / 5).toInt().coerceIn(0, 100),
        )
    }
