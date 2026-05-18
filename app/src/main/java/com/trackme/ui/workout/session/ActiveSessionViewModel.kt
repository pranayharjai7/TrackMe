package com.trackme.ui.workout.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.DeleteSetUseCase
import com.trackme.domain.usecase.FinishSessionUseCase
import com.trackme.domain.usecase.LogSetUseCase
import com.trackme.domain.usecase.ObservePlannedExercisesWithDetailsUseCase
import com.trackme.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.trackme.ui.onboarding.DEFAULT_INPUT_STYLE
import com.trackme.ui.onboarding.PREF_INPUT_STYLE
import com.trackme.utils.startOfTodayMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class ExerciseExecutionState {
    IDLE,
    ACTIVE_SET,
    RESTING,
    COMPLETED
}

data class ActiveSessionUiState(
    val sessionId: String = "",
    val exercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val loggedSets: List<SessionSet> = emptyList(),
    val loggedSetsByExercise: Map<String, List<SessionSet>> = emptyMap(),
    val executionStates: Map<String, ExerciseExecutionState> = emptyMap(),
    val activeExerciseId: String? = null,
    val restTimerRunning: Boolean = false,
    val restSeconds: Int = 90,
    val restSecondsRemaining: Int = 90,
    val isFinishing: Boolean = false,
    val inputStyle: String = DEFAULT_INPUT_STYLE,
)

/**
 * ViewModel responsible for a live workout session.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Start or resume today's in-progress session for the selected workout day.
 * - Expose planned exercises, exercise metadata, logged sets, and rest timer state.
 * - Delegate persistence and personal-record logic to domain use cases/repositories.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val startSession: StartSessionUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val deleteSetUseCase: DeleteSetUseCase,
    private val finishSessionUseCase: FinishSessionUseCase,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val observePlannedExercisesWithDetails: ObservePlannedExercisesWithDetailsUseCase,
    private val supabase: SupabaseClient,
    private val dataStore: DataStore<Preferences>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])
    private val userId get() = runCatching { supabase.auth.currentSessionOrNull()?.user?.id }.getOrNull() ?: ""

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    private var sessionStartTime = System.currentTimeMillis()
    private var restTimerJob: Job? = null

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update { it.copy(inputStyle = prefs[PREF_INPUT_STYLE] ?: DEFAULT_INPUT_STYLE) }
            }
        }
        
        viewModelScope.launch {
            val session = try {
                workoutRepository.getInProgressSessionForDay(userId, dayId, startOfTodayMillis())
                    ?: startSession(userId, dayId)
            } catch (e: Exception) {
                return@launch
            }
            sessionStartTime = session.date
            _uiState.update { it.copy(sessionId = session.id) }

            launch {
                observePlannedExercisesWithDetails(dayId).collect { withDetails ->
                    _uiState.update { state ->
                        val newExecutionStates = state.executionStates.toMutableMap()
                        withDetails.forEach { (planned, _) ->
                            if (!newExecutionStates.containsKey(planned.exerciseId)) {
                                val sets = state.loggedSetsByExercise[planned.exerciseId].orEmpty()
                                newExecutionStates[planned.exerciseId] = if (sets.size >= planned.targetSets) {
                                    ExerciseExecutionState.COMPLETED
                                } else {
                                    ExerciseExecutionState.IDLE
                                }
                            }
                        }
                        state.copy(exercises = withDetails, executionStates = newExecutionStates)
                    }
                }
            }
            launch {
                workoutRepository.getSessionSets(session.id).collect { sets ->
                    _uiState.update { state ->
                        val grouped = sets.groupBy { it.exerciseId }
                        val newExecutionStates = state.executionStates.toMutableMap()
                        state.exercises.forEach { (planned, _) ->
                            val loggedForEx = grouped[planned.exerciseId].orEmpty()
                            // If we are IDLE but should be COMPLETED, update it.
                            // But don't overwrite ACTIVE_SET or RESTING states unless target reached.
                            if (loggedForEx.size >= planned.targetSets) {
                                newExecutionStates[planned.exerciseId] = ExerciseExecutionState.COMPLETED
                            } else if (newExecutionStates[planned.exerciseId] == ExerciseExecutionState.COMPLETED) {
                                newExecutionStates[planned.exerciseId] = ExerciseExecutionState.IDLE
                            }
                        }
                        state.copy(
                            loggedSets = sets,
                            loggedSetsByExercise = grouped,
                            executionStates = newExecutionStates
                        )
                    }
                }
            }
        }
    }

    fun startExercise(exerciseId: String) {
        val currentState = _uiState.value.executionStates[exerciseId]
        if (currentState == ExerciseExecutionState.ACTIVE_SET) return

        _uiState.update { 
            it.copy(
                activeExerciseId = exerciseId,
                executionStates = it.executionStates + (exerciseId to ExerciseExecutionState.ACTIVE_SET)
            )
        }
    }

    fun cancelActiveSet(exerciseId: String) {
        _uiState.update { 
            val newState = if (it.activeExerciseId == exerciseId) null else it.activeExerciseId
            it.copy(
                activeExerciseId = newState,
                executionStates = it.executionStates + (exerciseId to ExerciseExecutionState.IDLE)
            )
        }
    }

    fun completeSet(
        exerciseId: String,
        weightKg: Float,
        reps: Int,
        durationSeconds: Int? = null,
        distanceKm: Float? = null,
        speedKmh: Float? = null,
        inclinePercent: Float? = null,
    ) {
        val planned = plannedFor(exerciseId) ?: return
        val loggedSets = _uiState.value.loggedSetsByExercise[exerciseId].orEmpty()
        val nextSetNumber = loggedSets.size + 1

        viewModelScope.launch {
            logSetUseCase(
                sessionId = _uiState.value.sessionId,
                userId = userId,
                exerciseId = exerciseId,
                setNumber = nextSetNumber,
                weightKg = weightKg,
                reps = reps,
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                speedKmh = speedKmh,
                inclinePercent = inclinePercent,
            )

            val isDone = nextSetNumber >= planned.targetSets
            _uiState.update { state ->
                val nextExecutionState = if (isDone) ExerciseExecutionState.COMPLETED else ExerciseExecutionState.RESTING
                state.copy(
                    executionStates = state.executionStates + (exerciseId to nextExecutionState),
                    activeExerciseId = if (state.activeExerciseId == exerciseId) null else state.activeExerciseId
                )
            }

            if (!isDone) {
                startRestTimer(exerciseId)
            }
        }
    }

    fun skipRest(exerciseId: String) {
        stopRestTimer()
        _uiState.update { 
            it.copy(executionStates = it.executionStates + (exerciseId to ExerciseExecutionState.IDLE))
        }
    }

    fun logSet(
        exerciseId: String,
        setNumber: Int,
        weightKg: Float,
        reps: Int,
        durationSeconds: Int? = null,
        distanceKm: Float? = null,
        speedKmh: Float? = null,
        inclinePercent: Float? = null,
    ) {
        // Logging delegates to LogSetUseCase so PR updates and persistence stay in domain/data layers.
        viewModelScope.launch {
            logSetUseCase(
                sessionId = _uiState.value.sessionId,
                userId = userId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = weightKg,
                reps = reps,
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                speedKmh = speedKmh,
                inclinePercent = inclinePercent,
            )
            startRestTimer()
        }
    }

    fun editSet(
        setId: String,
        exerciseId: String,
        setNumber: Int,
        weightKg: Float,
        reps: Int,
        durationSeconds: Int? = null,
        distanceKm: Float? = null,
        speedKmh: Float? = null,
        inclinePercent: Float? = null,
    ) {
        viewModelScope.launch {
            logSetUseCase(
                sessionId = _uiState.value.sessionId,
                userId = userId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = weightKg,
                reps = reps,
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                speedKmh = speedKmh,
                inclinePercent = inclinePercent,
                setId = setId,
            )
            // No state transition or rest timer needed for edit
        }
    }

    fun deleteSet(set: SessionSet) {
        viewModelScope.launch {
            deleteSetUseCase(set)
            
            // Re-evaluate execution state immediately to update UI before sync finishes
            val planned = plannedFor(set.exerciseId)
            val loggedSets = _uiState.value.loggedSetsByExercise[set.exerciseId].orEmpty()
            
            if (planned != null) {
                // Determine what the size will be after deletion
                val newCount = maxOf(0, loggedSets.size - 1)
                if (newCount < planned.targetSets) {
                    _uiState.update { state ->
                        val currentExState = state.executionStates[set.exerciseId]
                        // If it was COMPLETED, we need to move it back to IDLE
                        if (currentExState == ExerciseExecutionState.COMPLETED) {
                            state.copy(executionStates = state.executionStates + (set.exerciseId to ExerciseExecutionState.IDLE))
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    private fun startRestTimer(exerciseId: String? = null) {
        // A single Job owns the timer so repeated set logs reset the countdown cleanly.
        restTimerJob?.cancel()
        val seconds = _uiState.value.restSeconds
        _uiState.update { it.copy(restTimerRunning = true, restSecondsRemaining = seconds) }
        restTimerJob = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _uiState.update { it.copy(restSecondsRemaining = remaining) }
                if (remaining > 0) delay(1_000)
            }
            _uiState.update { state ->
                val nextStates = if (exerciseId != null && state.executionStates[exerciseId] == ExerciseExecutionState.RESTING) {
                    state.executionStates + (exerciseId to ExerciseExecutionState.IDLE)
                } else state.executionStates
                state.copy(restTimerRunning = false, executionStates = nextStates)
            }
        }
    }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        _uiState.update { it.copy(restTimerRunning = false) }
    }

    fun setRestDuration(seconds: Int) = _uiState.update { it.copy(restSeconds = seconds) }

    fun finishSession(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFinishing = true) }
            finishSessionUseCase(_uiState.value.sessionId, sessionStartTime)
            onDone()
        }
    }

    fun updateTargetSets(exerciseId: String, sets: Int) {
        viewModelScope.launch {
            val planned = _uiState.value.exercises.find { it.first.exerciseId == exerciseId }?.first
            if (planned != null) {
                val updated = planned.copy(targetSets = sets)
                workoutRepository.updatePlannedExercise(updated)
            }
        }
    }

    fun setsForExercise(exerciseId: String): List<SessionSet> =
        _uiState.value.loggedSetsByExercise[exerciseId].orEmpty()

    fun plannedFor(exerciseId: String): PlannedExercise? =
        _uiState.value.exercises.find { it.first.exerciseId == exerciseId }?.first

    fun addExercise(exerciseId: String) {
        viewModelScope.launch {
            val nextIndex = _uiState.value.exercises.size
            addExerciseToDay(dayId, userId, exerciseId, nextIndex)
        }
    }
}
