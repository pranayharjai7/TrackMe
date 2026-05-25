package com.trackme.wearable.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.wearable.TrackMeWearApplication
import com.trackme.wearable.health.HealthConnectManager
import com.trackme.wearable.health.WearHealthSnapshot
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionStatePayload
import com.trackme.wearbridge.SetLogPayload
import com.trackme.wearable.phone.PhoneConnectionState
import com.trackme.wearbridge.WatchActionType
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class LoggerInputState(
    val activeField: LoggerField = LoggerField.REPS,
    val weightKg: Float = 0f,
    val reps: Int = 8,
    val durationSeconds: Int = 45,
    val distanceKm: Float = 0f,
    val speedKmh: Float = 0f,
    val inclinePercent: Float = 0f,
)

enum class LoggerField {
    WEIGHT,
    REPS,
    DURATION,
    DISTANCE,
    SPEED,
    INCLINE,
}

enum class WorkoutScreenState {
    IDLE,
    ACTIVE_SET,
    CONFIRM,
    RESTING,
    EXERCISE_SUMMARY,
    WORKOUT_COMPLETE
}

data class WearUiState(
    val session: SessionStatePayload? = null,
    val offline: Boolean = false,
    val queuedCount: Int = 0,
    val loggerInput: LoggerInputState = LoggerInputState(),
    val health: WearHealthSnapshot = WearHealthSnapshot(),
    val lastCommandAccepted: Boolean? = null,
    val workoutScreenState: WorkoutScreenState = WorkoutScreenState.IDLE,
    val restSecondsRemaining: Int = 90,
    val restTotalSeconds: Int = 90,
    val lastExerciseVolume: Float = 0f,
    val lastExerciseName: String = "",
    val isPr: Boolean = false,
    val prDeltaKg: Float = 0f,
    val isAmbient: Boolean = false,
)

class WearSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = (application as TrackMeWearApplication).runtime
    private val workoutStateSync = runtime.workoutStateSync
    private val healthMetricsSender = runtime.healthMetricsSender
    private val phoneConnectionManager = runtime.phoneConnectionManager
    private val healthConnectManager = HealthConnectManager(application.applicationContext)

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    private var started = false
    private var sessionStartEpochMs: Long = 0L

    init {
        combine(
            workoutStateSync.sessionState,
            phoneConnectionManager.connectionState,
            workoutStateSync.queuedCount,
            healthMetricsSender.snapshot,
        ) { session, connection, queuedCount, health ->
            _uiState.value.copy(
                session = session,
                offline = connection !is PhoneConnectionState.Connected,
                queuedCount = queuedCount,
                health = health,
            )
        }.onEach { next ->
            // Track session start time when a new session begins
            if (next.session != null && _uiState.value.session == null) {
                sessionStartEpochMs = System.currentTimeMillis()
            }
            // Crash recovery: if a session is active but we're in IDLE (e.g. after process restart),
            // auto-transition to ACTIVE_SET so the user can continue logging immediately.
            val state = if (next.session != null && _uiState.value.workoutScreenState == WorkoutScreenState.IDLE) {
                next.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
            } else {
                next
            }
            _uiState.value = state
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            while (true) {
                delay(30_000)
                flushHealthMetrics()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        runtime.start()
    }

    fun requestSnapshot() {
        viewModelScope.launch {
            workoutStateSync.sendAction(actionType = WatchActionType.REQUEST_SYNC)
        }
    }

    fun startSet() {
        val session = _uiState.value.session
        sendAction(
            actionType = WatchActionType.START_SET,
            exerciseId = session?.exercises?.getOrNull(session.exerciseIndex)?.exerciseId,
            setNumber = session?.setIndex,
        )
    }

    fun prepareLogger() {
        val session = _uiState.value.session ?: return
        _uiState.value = _uiState.value.copy(
            loggerInput = LoggerInputState(
                activeField = defaultField(session.loggingType),
                weightKg = session.targetWeight ?: 0f,
                reps = session.targetReps ?: 8,
                durationSeconds = session.targetDurationSeconds ?: 45,
                distanceKm = session.targetDistanceKm ?: 0f,
            )
        )
    }

    fun selectField(field: LoggerField) {
        _uiState.value = _uiState.value.copy(loggerInput = _uiState.value.loggerInput.copy(activeField = field))
    }

    fun adjustActiveField(deltaSteps: Int) {
        val input = _uiState.value.loggerInput
        val next = when (input.activeField) {
            LoggerField.WEIGHT -> input.copy(weightKg = (input.weightKg + deltaSteps * 2.5f).coerceAtLeast(0f))
            LoggerField.REPS -> input.copy(reps = (input.reps + deltaSteps).coerceAtLeast(0))
            LoggerField.DURATION -> input.copy(durationSeconds = (input.durationSeconds + deltaSteps * 5).coerceAtLeast(0))
            LoggerField.DISTANCE -> input.copy(distanceKm = (input.distanceKm + deltaSteps * 0.05f).coerceAtLeast(0f))
            LoggerField.SPEED -> input.copy(speedKmh = (input.speedKmh + deltaSteps * 0.5f).coerceAtLeast(0f))
            LoggerField.INCLINE -> input.copy(inclinePercent = (input.inclinePercent + deltaSteps * 0.5f).coerceAtLeast(0f))
        }
        _uiState.value = _uiState.value.copy(loggerInput = next)
    }

    /** Called when user taps the Active Set screen — shows the confirm overlay. */
    fun confirmSet() {
        _uiState.value = _uiState.value.copy(workoutScreenState = WorkoutScreenState.CONFIRM)
    }

    /** Called when user swipes down on the confirm overlay — cancels without logging. */
    fun cancelConfirm() {
        _uiState.value = _uiState.value.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
    }

    /** Called when user taps the confirm overlay — logs the set and starts rest. */
    fun confirmAndLog() {
        submitLog()
        val restSeconds = _uiState.value.session?.restRemaining ?: 90
        _uiState.value = _uiState.value.copy(
            workoutScreenState = WorkoutScreenState.RESTING,
            restSecondsRemaining = restSeconds,
            restTotalSeconds = restSeconds
        )
        startRestCountdown()
    }

    /** Adjusts the rest timer duration. deltaSeconds is a raw delta (e.g. +15 or -15). */
    fun adjustRestTime(deltaSeconds: Int) {
        val newTime = (_uiState.value.restSecondsRemaining + deltaSeconds).coerceIn(15, 300)
        _uiState.value = _uiState.value.copy(restSecondsRemaining = newTime)
    }

    /** Called when rest ends (timer or tap). Advances to next set. */
    fun endRest() {
        restCountdownJob?.cancel()
        val session = _uiState.value.session
        val currentExercise = session?.exercises?.getOrNull(session.exerciseIndex)
        val allSetsComplete = currentExercise != null &&
            currentExercise.completedSets >= currentExercise.targetSets
        if (allSetsComplete) {
            showExerciseSummary()
        } else {
            _uiState.value = _uiState.value.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
        }
    }

    /** Long-press hardware button — resets watch state. Undo logic lives on the phone. */
    fun undoLastSet() {
        // WatchActionType has no UNDO action; local state reset only.
        // Phone will resync the authoritative state shortly after.
        _uiState.value = _uiState.value.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
    }

    /** Sets ambient (always-on) mode. */
    fun setAmbientMode(ambient: Boolean) {
        _uiState.value = _uiState.value.copy(isAmbient = ambient)
    }

    fun submitLog() {
        val session = _uiState.value.session ?: return
        val input = _uiState.value.loggerInput
        val log = when (session.loggingType) {
            LoggingTypePayload.WEIGHTED_REPS -> SetLogPayload(
                weightKg = input.weightKg,
                reps = input.reps,
            )
            LoggingTypePayload.BODYWEIGHT_REPS -> SetLogPayload(
                reps = input.reps,
            )
            LoggingTypePayload.TIMED -> SetLogPayload(
                durationSeconds = input.durationSeconds,
            )
            LoggingTypePayload.CARDIO -> SetLogPayload(
                durationSeconds = input.durationSeconds,
                distanceKm = input.distanceKm,
                speedKmh = input.speedKmh,
                inclinePercent = input.inclinePercent,
            )
        }
        sendAction(
            actionType = WatchActionType.COMPLETE_SET,
            exerciseId = session.exercises.getOrNull(session.exerciseIndex)?.exerciseId,
            setNumber = session.setIndex,
            log = log,
        )
    }

    fun skipRest() {
        sendAction(
            actionType = WatchActionType.SKIP_REST,
            exerciseId = _uiState.value.session?.exercises?.getOrNull(_uiState.value.session?.exerciseIndex ?: 0)?.exerciseId,
            setNumber = _uiState.value.session?.setIndex,
        )
    }

    fun nextExercise() {
        sendAction(actionType = WatchActionType.SKIP_EXERCISE)
    }

    fun previousExercise() {
        sendAction(actionType = WatchActionType.PREVIOUS_EXERCISE)
    }

    fun switchToExercise(index: Int) {
        val session = _uiState.value.session
        sendAction(
            actionType = WatchActionType.SWITCH_EXERCISE,
            exerciseId = session?.exercises?.getOrNull(index)?.exerciseId,
            setNumber = session?.setIndex,
        )
    }

    fun showExerciseSummary() {
        val session = _uiState.value.session ?: return
        val currentExercise = session.exercises.getOrNull(session.exerciseIndex)
        _uiState.value = _uiState.value.copy(
            workoutScreenState = WorkoutScreenState.EXERCISE_SUMMARY,
            lastExerciseName = session.exerciseName,
            lastExerciseVolume = currentExercise?.completedSets?.toFloat()?.times(session.targetWeight ?: 0f) ?: 0f,
            isPr = false,      // TODO: wire from phone when PR detection is added
            prDeltaKg = 0f,
        )
    }

    fun finishWorkout() {
        flushHealthMetrics()
        sendAction(actionType = WatchActionType.END_WORKOUT)
        _uiState.value = _uiState.value.copy(workoutScreenState = WorkoutScreenState.WORKOUT_COMPLETE)
        writeToHealthConnect()
    }

    private fun writeToHealthConnect() {
        val session = _uiState.value.session ?: return
        val health = _uiState.value.health
        val endMs = System.currentTimeMillis()
        val startMs = if (sessionStartEpochMs > 0L) sessionStartEpochMs else endMs - (health.durationSeconds * 1_000L)
        viewModelScope.launch {
            healthConnectManager.writeWorkoutSession(
                startEpochMs = startMs,
                endEpochMs = endMs,
                sessionTitle = session.exerciseName,
                heartRateSamples = emptyList(), // no buffered samples in this version
                totalCaloriesKcal = health.activeCalories,
            )
        }
    }

    fun flushHealthMetrics() {
        viewModelScope.launch { healthMetricsSender.flushNow() }
    }

    private var restCountdownJob: Job? = null

    private fun startRestCountdown() {
        restCountdownJob?.cancel()
        restCountdownJob = viewModelScope.launch {
            while (_uiState.value.restSecondsRemaining > 0) {
                delay(1_000)
                _uiState.value = _uiState.value.copy(
                    restSecondsRemaining = (_uiState.value.restSecondsRemaining - 1).coerceAtLeast(0)
                )
            }
            endRest()
        }
    }

    private fun sendAction(
        actionType: WatchActionType,
        exerciseId: String? = null,
        setNumber: Int? = null,
        log: SetLogPayload? = null,
    ) {
        viewModelScope.launch {
            workoutStateSync.sendAction(
                actionType = actionType,
                exerciseId = exerciseId,
                setNumber = setNumber,
                log = log,
            )
            _uiState.value = _uiState.value.copy(lastCommandAccepted = true)
        }
    }

    private fun defaultField(loggingType: LoggingTypePayload): LoggerField =
        when (loggingType) {
            LoggingTypePayload.WEIGHTED_REPS -> LoggerField.WEIGHT
            LoggingTypePayload.BODYWEIGHT_REPS -> LoggerField.REPS
            LoggingTypePayload.TIMED -> LoggerField.DURATION
            LoggingTypePayload.CARDIO -> LoggerField.DURATION
        }
}
