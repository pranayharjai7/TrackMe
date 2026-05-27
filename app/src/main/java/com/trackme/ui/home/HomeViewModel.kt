package com.trackme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.repository.HealthRepository
import com.trackme.domain.usecase.GetHealthSnapshotsUseCase
import com.trackme.domain.usecase.GetWorkoutForDateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.trackme.utils.MILLIS_PER_DAY
import com.trackme.utils.millisDaysAgo
import com.trackme.utils.startOfLocalDayMillis
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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
    val selectedDate: LocalDate = LocalDate.now(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val completedDays: Set<LocalDate> = emptySet(),
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
    private val getWorkoutForDate: GetWorkoutForDateUseCase,
    private val workoutRepository: WorkoutRepository,
    private val getHealthSnapshots: GetHealthSnapshotsUseCase,
    private val healthRepository: HealthRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _visibleMonth = MutableStateFlow(YearMonth.now())

    init {
        // Perform an initial broad sync when the user is first authenticated
        viewModelScope.launch {
            supabase.auth.sessionStatus
                .mapNotNull { (it as? SessionStatus.Authenticated)?.session?.user?.id }
                .distinctUntilChanged()
                .collect { uid ->
                    runCatching { healthRepository.syncFromHealthConnect(uid) }
                }
        }

        // Perform a targeted sync whenever the selected date changes
        viewModelScope.launch {
            _selectedDate.collect { date ->
                val uid = (supabase.auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.id
                if (!uid.isNullOrEmpty()) {
                    runCatching { healthRepository.syncFromHealthConnectForDate(uid, date) }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        supabase.auth.sessionStatus
            .onStart {
                supabase.auth.awaitInitialization()
                emit(supabase.auth.sessionStatus.value)
            }
            .distinctUntilChanged(),
        _selectedDate,
        _visibleMonth
    ) { status, selectedDate, visibleMonth ->
        Triple(status, selectedDate, visibleMonth)
    }.flatMapLatest { (status, selectedDate, visibleMonth) ->
        val session = (status as? SessionStatus.Authenticated)?.session
            ?: return@flatMapLatest flowOf(HomeUiState(isLoading = status is SessionStatus.Initializing))

        val uid = session.user?.id.orEmpty()
        if (uid.isEmpty()) return@flatMapLatest flowOf(HomeUiState(isLoading = false))

        val thirtyDaysAgo = millisDaysAgo(30)
        val oneYearAgo = millisDaysAgo(365)
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
            getWorkoutForDate(uid, selectedDate),
            workoutRepository.getPersonalRecords(uid),
            workoutRepository.getSessionsSince(uid, oneYearAgo),
            getHealthSnapshots(uid, 30),
            combine(weekStripFlow, workoutRepository.getSetsSince(uid, oneYearAgo)) { ws, sets -> ws to sets }
        ) { today, prs, sessions, snapshots, (weekStrip, sets) ->
            val selectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val selectedDateMidnight = startOfLocalDayMillis(selectedDateMillis)
            val todayMidnight = startOfLocalDayMillis(System.currentTimeMillis())
            val isHistorical = selectedDateMidnight < todayMidnight
            
            // For completed day IDs today, if historical, consider ANY session with sets as completed
            val completedDayIdsToday = sessions
                .filter { session -> 
                    startOfLocalDayMillis(session.date) == selectedDateMidnight && 
                    (session.durationMinutes > 0 || (isHistorical && sets.any { it.sessionId == session.id }))
                }
                .map { it.dayId }
                .toSet()
                
            val validDayIds = weekStrip.filterNotNull().map { it.id }.toSet()
            
            val activeSession = if (isHistorical) null else sessions.firstOrNull {
                startOfLocalDayMillis(it.date) == selectedDateMidnight
                    && it.durationMinutes == 0
                    && it.dayId !in completedDayIdsToday
                    && it.dayId in validDayIds
            }
            
            val isTodaySessionFinished = if (isHistorical) {
                sessions.any { session ->
                    startOfLocalDayMillis(session.date) == selectedDateMidnight && 
                    session.dayId == today?.id && 
                    sets.any { it.sessionId == session.id }
                }
            } else {
                today != null && sessions.any {
                    startOfLocalDayMillis(it.date) == selectedDateMidnight && it.dayId == today.id && it.durationMinutes > 0
                }
            }
            
            val dashboardState = when {
                activeSession != null -> HomeDashboardState.ACTIVE_SESSION
                isTodaySessionFinished -> HomeDashboardState.TRIUMPH
                today != null -> HomeDashboardState.PRE_WORKOUT
                else -> HomeDashboardState.REST_RECOVERY
            }
            
            // Calculate today's volume and lifting calories
            val todaySets = sets.filter { it.updatedAt >= selectedDateMidnight && it.updatedAt < selectedDateMidnight + MILLIS_PER_DAY }
            val todayVolumeKg = todaySets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
            val liftingCalories = todayVolumeKg * 0.04f

            val latestSnapshot = snapshots.maxByOrNull { it.date }
            val healthInsight = latestSnapshot?.toHealthInsight(dashboardState, liftingCalories)

            val completedDays = sessions.filter { session ->
                session.durationMinutes > 0 || (startOfLocalDayMillis(session.date) < todayMidnight && sets.any { it.sessionId == session.id })
            }.map { 
                Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSet()

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
                selectedDate = selectedDate,
                visibleMonth = visibleMonth,
                completedDays = completedDays,
            )
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _selectedDate.value = date
    }

    fun changeMonth(monthOffset: Long) {
        _visibleMonth.value = _visibleMonth.value.plusMonths(monthOffset)
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _visibleMonth.value = YearMonth.from(today)
    }

    fun restartFinishedWorkout(dayId: String) {
        val status = supabase.auth.sessionStatus.value
        val session = (status as? SessionStatus.Authenticated)?.session ?: return
        val uid = session.user?.id.orEmpty()
        if (uid.isEmpty()) return

        viewModelScope.launch {
            val selectedDateMillis = _selectedDate.value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val selectedDateMidnight = startOfLocalDayMillis(selectedDateMillis)
            val sessions = workoutRepository.getSessionsSince(uid, millisDaysAgo(365)).first()
            val finishedSession = sessions.firstOrNull {
                startOfLocalDayMillis(it.date) == selectedDateMidnight && it.dayId == dayId && it.durationMinutes > 0
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

private fun HealthSnapshot.toHealthInsight(dashboardState: HomeDashboardState, liftingCalories: Float): HealthInsight =
    if (dashboardState == HomeDashboardState.REST_RECOVERY) {
        val steps = steps ?: 0L
        HealthInsight(
            title = "Recovery & Readiness",
            description = "You've taken ${"%,d".format(steps)} steps today. Keep active but prioritize rest.",
            score = (steps / 100).toInt().coerceIn(0, 100),
        )
    } else {
        val activeBurned = activeCaloriesBurned
        val stepsVal = steps ?: 0L
        val weight = weightKg ?: 75f
        val stepsCalories = stepsVal * weight * 0.0005f
        
        val baseActiveCalories = if (activeBurned != null && activeBurned > 0f) {
            activeBurned
        } else {
            stepsCalories
        }
        
        val calories = baseActiveCalories + liftingCalories
        HealthInsight(
            title = "Active Energy",
            description = "You've burned ${calories.toInt()} kcal today. Fuel your body for the workout!",
            score = (calories / 5).toInt().coerceIn(0, 100),
        )
    }
