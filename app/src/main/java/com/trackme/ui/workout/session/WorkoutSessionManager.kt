package com.trackme.ui.workout.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.loggingType
import com.trackme.domain.model.LoggingType
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.DeleteSetUseCase
import com.trackme.domain.usecase.FinishSessionUseCase
import com.trackme.domain.usecase.LogSetUseCase
import com.trackme.domain.usecase.ObservePlannedExercisesWithDetailsUseCase
import com.trackme.domain.usecase.StartSessionUseCase
import com.trackme.ui.onboarding.DEFAULT_INPUT_STYLE
import com.trackme.ui.onboarding.PREF_INPUT_STYLE
import com.trackme.utils.startOfTodayMillis
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centrally manages the active workout session state to synchronize
 * both the ActiveSessionScreen (UI) and the WorkoutSessionService (Notification).
 *
 * Architecture Layer: State Manager (Core/Data)
 * Pattern: Thread-safe Repository Coordinator / Singleton State Machine
 */
@Singleton
class WorkoutSessionManager @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val startSession: StartSessionUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val deleteSetUseCase: DeleteSetUseCase,
    private val finishSessionUseCase: FinishSessionUseCase,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val observePlannedExercisesWithDetails: ObservePlannedExercisesWithDetailsUseCase,
    private val supabase: SupabaseClient,
    private val dataStore: DataStore<Preferences>
) {
    // Thread context for safety and isolation
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    private var dayId: String = ""
    private var sessionStartTime = System.currentTimeMillis()
    private var restTimerJob: Job? = null
    private var dataCollectionJob: Job? = null
    
    private val userId get() = runCatching { supabase.auth.currentSessionOrNull()?.user?.id }.getOrNull() ?: ""

    /**
     * Initializes or resumes the active workout session.
     * Prevents re-initialization if already managing the same day.
     */
    fun startOrResumeSession(userId: String, targetDayId: String, sessionDateMillis: Long) {
        if (dayId == targetDayId && _uiState.value.sessionId.isNotEmpty()) {
            // Already running/loaded correct session, do not interrupt
            return
        }

        this.dayId = targetDayId
        dataCollectionJob?.cancel()

        dataCollectionJob = managerScope.launch {
            val resolvedUserId = userId.ifBlank { this@WorkoutSessionManager.userId }
            val localTodayStart = com.trackme.utils.startOfLocalDayMillis(sessionDateMillis)
            val isHistorical = com.trackme.utils.startOfLocalDayMillis(sessionDateMillis) < startOfTodayMillis()
            
            val session = try {
                workoutRepository.getLatestSessionForDay(resolvedUserId, dayId, localTodayStart)
                    ?: startSession(resolvedUserId, dayId, sessionDateMillis)
            } catch (e: Exception) {
                return@launch
            }
            sessionStartTime = session.date
            val isCompleted = session.durationMinutes > 0 && !isHistorical
            
            updateUiState { 
                it.copy(
                    sessionId = session.id,
                    dayId = dayId,
                    sessionDateMillis = session.date,
                    isCompleted = isCompleted, 
                    isHistoricalSession = isHistorical 
                ) 
            }

            // Observe logger input style
            launch {
                dataStore.data.collect { prefs ->
                    updateUiState { it.copy(inputStyle = prefs[PREF_INPUT_STYLE] ?: DEFAULT_INPUT_STYLE) }
                }
            }

            // Restore active and resting states
            try {
                val prefs = dataStore.data.first()
                val savedActive = prefs[stringPreferencesKey("${dayId}_active_exercise_id")]
                val savedResting = prefs[stringPreferencesKey("${dayId}_resting_exercise_id")]
                val savedRestEndTime = prefs[longPreferencesKey("${dayId}_rest_timer_end_time")]

                if (savedActive != null) {
                    updateUiState { it.copy(activeExerciseId = savedActive) }
                    prefillQuickLogging(savedActive)
                }

                if (savedResting != null && savedRestEndTime != null) {
                    val now = System.currentTimeMillis()
                    val remainingSeconds = ((savedRestEndTime - now) / 1000).toInt()
                    if (remainingSeconds > 0) {
                        updateUiState { it.copy(restingExerciseId = savedResting) }
                        runRestTimer(savedResting, remainingSeconds)
                    } else {
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

            // Observe planned exercises flow
            launch {
                observePlannedExercisesWithDetails(dayId).collect { withDetails ->
                    updateUiState { state -> state.copy(exercises = withDetails) }
                    // Update quick logging defaults if empty and active exercise set
                    val activeId = _uiState.value.activeExerciseId
                    if (activeId != null && _uiState.value.quickWeight == 0f && _uiState.value.quickReps == 0) {
                        prefillQuickLogging(activeId)
                    }
                }
            }

            // Observe logged sets flow
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
     * Helper to keep quick logging pre-fills consistent with targets or historical sets.
     */
    private fun prefillQuickLogging(exerciseId: String) {
        val planned = plannedFor(exerciseId)
        val lastSet = _uiState.value.loggedSetsByExercise[exerciseId]?.lastOrNull()
        val defaultWeight = lastSet?.weightKg ?: planned?.targetWeightKg ?: 0f
        val defaultReps = lastSet?.reps ?: planned?.targetReps ?: 0
        updateUiState { 
            it.copy(
                quickWeight = defaultWeight,
                quickReps = defaultReps
            ) 
        }
    }

    /**
     * Centralized state update helper to ensure consistency.
     */
    private fun updateUiState(updateBlock: (ActiveSessionUiState) -> ActiveSessionUiState) {
        _uiState.update { currentState ->
            val nextState = updateBlock(currentState)
            
            // Derive execution states based on target sets and logged sets
            val derivedExecutionStates = deriveExecutionStates(
                exercises = nextState.exercises,
                loggedSetsByExercise = nextState.loggedSetsByExercise,
                activeExerciseId = nextState.activeExerciseId,
                restingExerciseId = nextState.restingExerciseId,
                isCompleted = nextState.isCompleted
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
                    restingExerciseId = finalRestingExerciseId,
                    isCompleted = nextState.isCompleted
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

    private fun deriveExecutionStates(
        exercises: List<Pair<PlannedExercise, Exercise?>>,
        loggedSetsByExercise: Map<String, List<SessionSet>>,
        activeExerciseId: String?,
        restingExerciseId: String?,
        isCompleted: Boolean,
    ): Map<String, ExerciseExecutionState> {
        return exercises.associate { (planned, _) ->
            val exerciseId = planned.exerciseId
            val completedSets = loggedSetsByExercise[exerciseId]?.size ?: 0
            val targetSets = planned.targetSets
            val state = when {
                isCompleted -> ExerciseExecutionState.COMPLETED
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
        prefillQuickLogging(exerciseId)

        managerScope.launch {
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

        managerScope.launch {
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
     * Logs the completed set. Transitions the state machine.
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

        managerScope.launch {
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
        managerScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
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
        managerScope.launch {
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
        managerScope.launch {
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

    fun deleteSet(set: SessionSet) {
        managerScope.launch {
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

    private fun runRestTimer(exerciseId: String?, seconds: Int) {
        restTimerJob?.cancel()
        updateUiState { it.copy(restTimerRunning = true, restSecondsRemaining = seconds) }
        restTimerJob = managerScope.launch {
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

    private fun startRestTimer(exerciseId: String? = null) {
        if (_uiState.value.isCompleted || _uiState.value.isHistoricalSession) {
            updateUiState { it.copy(restingExerciseId = null) }
            return
        }
        runRestTimer(exerciseId, _uiState.value.restSeconds)
    }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        updateUiState {
            it.copy(
                restTimerRunning = false,
                restingExerciseId = null
            )
        }
        managerScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
        }
    }

    fun setRestDuration(seconds: Int) = updateUiState { it.copy(restSeconds = seconds) }

    fun finishSession(onDone: () -> Unit) {
        managerScope.launch {
            updateUiState { it.copy(isFinishing = true, isCompleted = true) }
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${dayId}_active_exercise_id"))
                prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
            }
            finishSessionUseCase(_uiState.value.sessionId, sessionStartTime)
            
            // Clean up state
            dayId = ""
            dataCollectionJob?.cancel()
            restTimerJob?.cancel()
            _uiState.update { ActiveSessionUiState() }
            
            onDone()
        }
    }

    fun updateTargetSets(exerciseId: String, sets: Int) {
        managerScope.launch {
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
        managerScope.launch {
            val nextIndex = _uiState.value.exercises.size
            addExerciseToDay(dayId, userId, exerciseId, nextIndex)
        }
    }

    // ==========================================
    // ADDITIONAL LIVE WORKOUT NOTIFICATION CONTROLS
    // ==========================================

    /**
     * Pauses the entire workout session, halting timer operations.
     */
    fun pauseWorkout() {
        restTimerJob?.cancel()
        updateUiState { it.copy(isPaused = true, restTimerRunning = false) }
    }

    /**
     * Resumes the paused workout, restoring the rest timer if active.
     */
    fun resumeWorkout() {
        updateUiState { it.copy(isPaused = false) }
        val restExId = _uiState.value.restingExerciseId
        if (restExId != null && _uiState.value.restSecondsRemaining > 0) {
            runRestTimer(restExId, _uiState.value.restSecondsRemaining)
        }
    }

    /**
     * Navigates workout to the next exercise. Skips the current active exercise.
     */
    fun skipExercise(currentExerciseId: String) {
        val orderedPlanned = _uiState.value.exercises.map { it.first }
        val currentIndex = orderedPlanned.indexOfFirst { it.exerciseId == currentExerciseId }
        
        if (currentIndex != -1 && currentIndex < orderedPlanned.size - 1) {
            val nextExerciseId = orderedPlanned[currentIndex + 1].exerciseId
            startExercise(nextExerciseId)
        } else {
            // No next exercise in plan, return to IDLE active exercise
            updateUiState { it.copy(activeExerciseId = null) }
        }
    }

    /**
     * Navigates workout to the previous exercise.
     */
    fun previousExercise(currentExerciseId: String) {
        val orderedPlanned = _uiState.value.exercises.map { it.first }
        val currentIndex = orderedPlanned.indexOfFirst { it.exerciseId == currentExerciseId }
        
        if (currentIndex > 0) {
            val prevExerciseId = orderedPlanned[currentIndex - 1].exerciseId
            startExercise(prevExerciseId)
        }
    }

    /**
     * Increments/decrements quick entry weight value (in-notification log buffer).
     */
    fun adjustQuickWeight(delta: Float) {
        updateUiState { 
            val newWeight = maxOf(0f, it.quickWeight + delta)
            it.copy(quickWeight = newWeight) 
        }
    }

    /**
     * Increments/decrements quick entry reps value (in-notification log buffer).
     */
    fun adjustQuickReps(delta: Int) {
        updateUiState { 
            val newReps = maxOf(0, it.quickReps + delta)
            it.copy(quickReps = newReps) 
        }
    }

    /**
     * Saves the current set using quick log panel weight and reps values.
     */
    fun saveQuickSet() {
        val activeExId = _uiState.value.activeExerciseId ?: return
        val weight = _uiState.value.quickWeight
        val reps = _uiState.value.quickReps
        
        completeSet(
            exerciseId = activeExId,
            weightKg = weight,
            reps = reps
        )
    }

    /**
     * Increments/decrements the current rest duration dynamically.
     */
    fun adjustRestTime(secondsToAdd: Int) {
        if (!_uiState.value.restTimerRunning) return
        val newRemaining = maxOf(0, _uiState.value.restSecondsRemaining + secondsToAdd)
        val restExId = _uiState.value.restingExerciseId
        runRestTimer(restExId, newRemaining)
        managerScope.launch {
            dataStore.edit { prefs ->
                if (restExId != null && newRemaining > 0) {
                    prefs[stringPreferencesKey("${dayId}_resting_exercise_id")] = restExId
                    prefs[longPreferencesKey("${dayId}_rest_timer_end_time")] =
                        System.currentTimeMillis() + newRemaining * 1000L
                } else {
                    prefs.remove(stringPreferencesKey("${dayId}_resting_exercise_id"))
                    prefs.remove(longPreferencesKey("${dayId}_rest_timer_end_time"))
                }
            }
        }
    }
}
