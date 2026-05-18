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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
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

/**
 * Representation of the screen's UI State.
 * Added restingExerciseId to track exactly which exercise is resting.
 * executionStates is now fully derived and kept read-only for the UI.
 */
data class ActiveSessionUiState(
    val sessionId: String = "",
    val exercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val loggedSets: List<SessionSet> = emptyList(),
    val loggedSetsByExercise: Map<String, List<SessionSet>> = emptyMap(),
    val executionStates: Map<String, ExerciseExecutionState> = emptyMap(),
    val activeExerciseId: String? = null,
    val restingExerciseId: String? = null, // Tracks the currently resting exercise
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
 * State Machine & Completion Logic:
 * The execution state of each exercise card is dynamically derived as a function of current data:
 * UI State = f(completedSets, targetSets, activeExerciseId, restingExerciseId)
 *
 * Rules:
 * 1. COMPLETED: If completedSets >= targetSets. Takes absolute precedence.
 * 2. ACTIVE_SET: If exerciseId == activeExerciseId.
 * 3. RESTING: If exerciseId == restingExerciseId.
 * 4. IDLE: Default state.
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
        // Collect user preference for logger input style
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                updateUiState { it.copy(inputStyle = prefs[PREF_INPUT_STYLE] ?: DEFAULT_INPUT_STYLE) }
            }
        }
        
        // Start or resume the today's active session, then observe exercises and logged sets flows.
        viewModelScope.launch {
            val session = try {
                workoutRepository.getInProgressSessionForDay(userId, dayId, startOfTodayMillis())
                    ?: startSession(userId, dayId)
            } catch (e: Exception) {
                return@launch
            }
            sessionStartTime = session.date
            updateUiState { it.copy(sessionId = session.id) }

            // Restore persistent active exercise / rest timer state
            try {
                val prefs = dataStore.data.first()
                val savedActive = prefs[stringPreferencesKey("${dayId}_active_exercise_id")]
                val savedResting = prefs[stringPreferencesKey("${dayId}_resting_exercise_id")]
                val savedRestEndTime = prefs[longPreferencesKey("${dayId}_rest_timer_end_time")]

                if (savedActive != null) {
                    updateUiState { it.copy(activeExerciseId = savedActive) }
                }

                if (savedResting != null && savedRestEndTime != null) {
                    val now = System.currentTimeMillis()
                    val remainingSeconds = ((savedRestEndTime - now) / 1000).toInt()
                    if (remainingSeconds > 0) {
                        updateUiState { it.copy(restingExerciseId = savedResting) }
                        runRestTimer(savedResting, remainingSeconds)
                    } else {
                        // Rest timer expired while away, clean up preference keys
                        launch {
                            dataStore.edit { editPrefs ->
                                editPrefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                                editPrefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore failure in reading datastore
            }

            // Observe planned exercises. Any update here (such as changing target set counts)
            // will publish to exercises list, triggering an automatic recomposition and execution state derivation.
            launch {
                observePlannedExercisesWithDetails(dayId).collect { withDetails ->
                    updateUiState { state ->
                        state.copy(exercises = withDetails)
                    }
                }
            }

            // Observe logged sets. Adding/removing/modifying sets emits here, immediately updating completed set counts
            // and automatically recalculating the exercise execution state.
            launch {
                workoutRepository.getSessionSets(session.id).collect { sets ->
                    updateUiState { state ->
                        state.copy(
                            loggedSets = sets,
                            loggedSetsByExercise = sets.groupBy { it.exerciseId }
                        )
                    }
                }
            }
        }
    }

    /**
     * Centralized state update helper to ensure consistency.
     * Ensures all state transitions recompute derived execution states dynamically
     * and clears invalid active/resting state flags when an exercise is marked completed.
     */
    private fun updateUiState(updateBlock: (ActiveSessionUiState) -> ActiveSessionUiState) {
        _uiState.update { currentState ->
            val nextState = updateBlock(currentState)
            
            // Derive execution states based on target sets and logged sets
            val derivedExecutionStates = deriveExecutionStates(
                exercises = nextState.exercises,
                loggedSetsByExercise = nextState.loggedSetsByExercise,
                activeExerciseId = nextState.activeExerciseId,
                restingExerciseId = nextState.restingExerciseId
            )
            
            // Edge-case cleanup: If an exercise has reached its target sets (COMPLETED),
            // it can no longer be in an ACTIVE_SET or RESTING state.
            var finalActiveExerciseId = nextState.activeExerciseId
            var finalRestingExerciseId = nextState.restingExerciseId
            
            derivedExecutionStates.forEach { (exerciseId, execState) ->
                if (execState == ExerciseExecutionState.COMPLETED) {
                    if (finalActiveExerciseId == exerciseId) {
                        finalActiveExerciseId = null
                    }
                    if (finalRestingExerciseId == exerciseId) {
                        finalRestingExerciseId = null
                    }
                }
            }
            
            // Re-derive if the flags were updated during verification/cleanup
            val finalExecutionStates = if (finalActiveExerciseId != nextState.activeExerciseId || finalRestingExerciseId != nextState.restingExerciseId) {
                deriveExecutionStates(
                    exercises = nextState.exercises,
                    loggedSetsByExercise = nextState.loggedSetsByExercise,
                    activeExerciseId = finalActiveExerciseId,
                    restingExerciseId = finalRestingExerciseId
                )
            } else {
                derivedExecutionStates
            }
            
            nextState.copy(
                activeExerciseId = finalActiveExerciseId,
                restingExerciseId = finalRestingExerciseId,
                executionStates = finalExecutionStates
            )
        }
    }

    /**
     * Determines the execution state of all exercises deterministically.
     */
    private fun deriveExecutionStates(
        exercises: List<Pair<PlannedExercise, Exercise?>>,
        loggedSetsByExercise: Map<String, List<SessionSet>>,
        activeExerciseId: String?,
        restingExerciseId: String?,
    ): Map<String, ExerciseExecutionState> {
        return exercises.associate { (planned, _) ->
            val exerciseId = planned.exerciseId
            val completedSets = loggedSetsByExercise[exerciseId]?.size ?: 0
            val targetSets = planned.targetSets
            val state = when {
                completedSets >= targetSets -> ExerciseExecutionState.COMPLETED
                exerciseId == activeExerciseId -> ExerciseExecutionState.ACTIVE_SET
                exerciseId == restingExerciseId -> ExerciseExecutionState.RESTING
                else -> ExerciseExecutionState.IDLE
            }
            exerciseId to state
        }
    }

    /**
     * Activates an exercise to perform a set. Cancels any rest period for that exercise.
     */
    fun startExercise(exerciseId: String) {
        if (_uiState.value.activeExerciseId == exerciseId) return

        updateUiState {
            it.copy(
                activeExerciseId = exerciseId,
                restingExerciseId = if (it.restingExerciseId == exerciseId) null else it.restingExerciseId
            )
        }

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("${dayId}_active_exercise_id")] = exerciseId
                val currentResting = _uiState.value.restingExerciseId
                if (currentResting != null) {
                    prefs[stringPreferencesKey("${dayId}_resting_exercise_id")] = currentResting
                } else {
                    prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                }
            }
        }
    }

    /**
     * Cancels active set logging for an exercise, returning it to IDLE.
     */
    fun cancelActiveSet(exerciseId: String) {
        updateUiState {
            val newState = if (it.activeExerciseId == exerciseId) null else it.activeExerciseId
            it.copy(activeExerciseId = newState)
        }

        viewModelScope.launch {
            dataStore.edit { prefs ->
                val currentActive = _uiState.value.activeExerciseId
                if (currentActive != null) {
                    prefs[stringPreferencesKey("${dayId}_active_exercise_id")] = currentActive
                } else {
                    prefs.remove(stringPreferencesKey("${dayId}_active_exercise_id"))
                }
            }
        }
    }

    /**
     * Logs the completed set. Transitions the state machine:
     * - If completedSets >= targetSets: transition to COMPLETED.
     * - Else: transition to RESTING and trigger the rest timer countdown.
     */
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
            updateUiState { state ->
                state.copy(
                    activeExerciseId = if (state.activeExerciseId == exerciseId) null else state.activeExerciseId,
                    restingExerciseId = if (isDone) null else exerciseId
                )
            }

            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_active_exercise_id"))
                if (isDone) {
                    prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                    prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
                } else {
                    prefs[stringPreferencesKey("${dayId}_resting_exercise_id")] = exerciseId
                    val restEndTime = System.currentTimeMillis() + _uiState.value.restSeconds * 1000L
                    prefs[longPreferencesKey("${dayId}_rest_timer_end_time")] = restEndTime
                }
            }

            if (!isDone) {
                startRestTimer(exerciseId)
            }
        }
    }

    /**
     * Skips the rest timer for the given exercise, immediately transitioning it to IDLE.
     */
    fun skipRest(exerciseId: String) {
        stopRestTimer()
        updateUiState {
            it.copy(
                restingExerciseId = if (it.restingExerciseId == exerciseId) null else it.restingExerciseId
            )
        }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
        }
    }

    /**
     * Logs a set manually (used in edit/logging sub-components).
     */
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
            startRestTimer(exerciseId)
        }
    }

    /**
     * Edits a previously logged set's parameters.
     */
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
        }
    }

    /**
     * Optimistically deletes a logged set from the UI State and deletes it in the database.
     * The dynamic derivation ensures that if completedSets drops below targetSets,
     * the exercise state heals instantly (e.g. transitioning from COMPLETED back to IDLE).
     */
    fun deleteSet(set: SessionSet) {
        viewModelScope.launch {
            updateUiState { state ->
                val loggedForEx = state.loggedSetsByExercise[set.exerciseId].orEmpty()
                val updatedSetsForEx = loggedForEx.filter { it.id != set.id }
                val updatedGrouped = state.loggedSetsByExercise + (set.exerciseId to updatedSetsForEx)
                val updatedLoggedSets = state.loggedSets.filter { it.id != set.id }
                state.copy(
                    loggedSets = updatedLoggedSets,
                    loggedSetsByExercise = updatedGrouped
                )
            }
            
            deleteSetUseCase(set)
        }
    }

    /**
     * Runs the global rest countdown and cleans up its state.
     */
    private fun runRestTimer(exerciseId: String?, seconds: Int) {
        restTimerJob?.cancel()
        updateUiState { it.copy(restTimerRunning = true, restSecondsRemaining = seconds) }
        restTimerJob = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                updateUiState { it.copy(restSecondsRemaining = remaining) }
                if (remaining > 0) delay(1_000)
            }
            updateUiState { state ->
                state.copy(
                    restTimerRunning = false,
                    restingExerciseId = if (exerciseId != null && state.restingExerciseId == exerciseId) null else state.restingExerciseId
                )
            }
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
        }
    }

    /**
     * Starts the global rest countdown. If an exerciseId is specified,
     * it stays in the RESTING state until the countdown is zero or skipped.
     */
    private fun startRestTimer(exerciseId: String? = null) {
        runRestTimer(exerciseId, _uiState.value.restSeconds)
    }

    /**
     * Stops the global rest timer and clears any resting exercise status.
     */
    fun stopRestTimer() {
        restTimerJob?.cancel()
        updateUiState {
            it.copy(
                restTimerRunning = false,
                restingExerciseId = null
            )
        }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
        }
    }

    fun setRestDuration(seconds: Int) = updateUiState { it.copy(restSeconds = seconds) }

    fun finishSession(onDone: () -> Unit) {
        viewModelScope.launch {
            updateUiState { it.copy(isFinishing = true) }
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_active_exercise_id"))
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
            finishSessionUseCase(_uiState.value.sessionId, sessionStartTime)
            onDone()
        }
    }

    /**
     * Updates target set counts. Triggering this database update will cause
     * observePlannedExercises flow to emit, immediately triggering updateUiState
     * and re-evaluating the derived state logic.
     */
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
