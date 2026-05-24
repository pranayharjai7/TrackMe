package com.trackme.ui.workout.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.model.WorkoutPlan
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.BuildRoutineShareTextUseCase
import com.trackme.domain.usecase.GetActivePlanUseCase
import com.trackme.domain.usecase.SaveWorkoutDayUseCase
import com.trackme.domain.usecase.SaveWorkoutPlanUseCase
import com.trackme.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyPlannerUiState(
    val activePlan: WorkoutPlan? = null,
    val days: List<WorkoutDay> = emptyList(),
    val exerciseCounts: Map<String, Int> = emptyMap(),
    val isCreatingPlan: Boolean = false,
    val showNewPlanDialog: Boolean = false,
    val newPlanName: String = "",
    val routineTextToShare: String? = null,
)

/**
 * ViewModel responsible for the weekly planner screen.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Observe the active workout plan and its days.
 * - Expose per-day exercise counts for the planner UI.
 * - Delegate plan/day creation and routine-share formatting to domain use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeeklyPlannerViewModel @Inject constructor(
    private val getActivePlan: GetActivePlanUseCase,
    private val workoutRepository: WorkoutRepository,
    private val savePlan: SaveWorkoutPlanUseCase,
    private val saveDay: SaveWorkoutDayUseCase,
    private val buildRoutineShareText: BuildRoutineShareTextUseCase,
    private val syncManager: SyncManager,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private var userId: String = ""

    private val _uiState = MutableStateFlow(WeeklyPlannerUiState())
    val uiState: StateFlow<WeeklyPlannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            supabase.auth.sessionStatus
                .onStart {
                    supabase.auth.awaitInitialization()
                    emit(supabase.auth.sessionStatus.value)
                }
                .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.id.orEmpty() }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    userId = uid
                    if (uid.isBlank()) {
                        flowOf(PlannerObservation(null, emptyList(), emptyMap()))
                    } else {
                        viewModelScope.launch { runCatching { syncManager.runInitialSync() } }
                        getActivePlan(uid)
                            .flatMapLatest { plan ->
                                if (plan == null) {
                                    flowOf(PlannerObservation(null, emptyList(), emptyMap()))
                                } else {
                                    workoutRepository.getDaysForPlan(plan.id)
                                        .flatMapLatest { days ->
                                            if (days.isEmpty()) {
                                                flowOf(PlannerObservation(plan, emptyList(), emptyMap()))
                                            } else {
                                                combine(days.map { day ->
                                                    workoutRepository.getPlannedExercisesForDay(day.id)
                                                        .map { exercises -> day.id to exercises.size }
                                                }) { pairs ->
                                                    PlannerObservation(plan, days, pairs.toMap())
                                                }
                                            }
                                        }
                                }
                            }
                    }
                }
                .collect { observation ->
                    _uiState.update {
                        it.copy(
                            activePlan = observation.activePlan,
                            days = observation.days,
                            exerciseCounts = observation.exerciseCounts,
                        )
                    }
                }
        }
    }

    fun showNewPlanDialog() = _uiState.update { it.copy(showNewPlanDialog = true) }
    fun dismissNewPlanDialog() = _uiState.update { it.copy(showNewPlanDialog = false, newPlanName = "") }
    fun onNewPlanNameChange(name: String) = _uiState.update { it.copy(newPlanName = name) }

    fun createPlan() {
        val name = _uiState.value.newPlanName.trim().ifEmpty { return }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPlan = true) }
            savePlan(userId, name)
            _uiState.update { it.copy(isCreatingPlan = false, showNewPlanDialog = false, newPlanName = "") }
        }
    }

    fun addDay(dayOfWeek: DayOfWeek, name: String) {
        val planId = _uiState.value.activePlan?.id ?: return
        viewModelScope.launch { saveDay(planId, userId, dayOfWeek, name) }
    }

    fun deleteDay(day: WorkoutDay) {
        viewModelScope.launch { workoutRepository.deleteDay(day) }
    }

    fun prepareRoutineForSharing() {
        val plan = _uiState.value.activePlan ?: return
        val days = _uiState.value.days
        
        viewModelScope.launch {
            _uiState.update { it.copy(routineTextToShare = buildRoutineShareText(plan, days)) }
        }
    }

    fun onRoutineShared() {
        _uiState.update { it.copy(routineTextToShare = null) }
    }
}

private data class PlannerObservation(
    val activePlan: WorkoutPlan?,
    val days: List<WorkoutDay>,
    val exerciseCounts: Map<String, Int>,
)
