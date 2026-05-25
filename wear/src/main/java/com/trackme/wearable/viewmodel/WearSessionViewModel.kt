package com.trackme.wearable.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.wearable.TrackMeWearApplication
import com.trackme.wearable.health.WearHealthSnapshot
import com.trackme.wearable.workout.HorizontalPage
import com.trackme.wearable.workout.WearWorkoutStateMachine
import com.trackme.wearable.workout.WorkoutAppPhase
import com.trackme.wearable.workout.WorkoutFlowState
import com.trackme.wearable.workout.WorkoutOverlay
import com.trackme.wearable.workout.defaultLoggerField
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionStatePayload
import com.trackme.wearbridge.SetLogPayload
import com.trackme.wearable.phone.PhoneConnectionState
import com.trackme.wearbridge.WatchActionType
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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

data class WearUiState(
    val session: SessionStatePayload? = null,
    val offline: Boolean = false,
    val queuedCount: Int = 0,
    val loggerInput: LoggerInputState = LoggerInputState(),
    val health: WearHealthSnapshot = WearHealthSnapshot(),
    val lastCommandAccepted: Boolean? = null,
    val workoutFlow: WorkoutFlowState = WorkoutFlowState(),
)

class WearSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = (application as TrackMeWearApplication).runtime
    private val workoutStateSync = runtime.workoutStateSync
    private val healthMetricsSender = runtime.healthMetricsSender
    private val phoneConnectionManager = runtime.phoneConnectionManager

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    private var started = false
    private var lastCompletedSets = 0
    private var restWarningFired = false

    init {
        combine(
            workoutStateSync.sessionState,
            phoneConnectionManager.connectionState,
            workoutStateSync.queuedCount,
            healthMetricsSender.snapshot,
        ) { session, connection, queuedCount, health ->
            val previous = _uiState.value
            var flow = previous.workoutFlow

            if (session?.sessionId.isNullOrBlank()) {
                flow = flow.copy(
                    appPhase = WorkoutAppPhase.WatchFace,
                    overlay = WorkoutOverlay.None,
                    restAdjustSeconds = 0,
                )
            } else if (session.isCompleted) {
                flow = flow.copy(
                    appPhase = WorkoutAppPhase.WorkoutActive,
                    overlay = WorkoutOverlay.None,
                )
            } else if (session.restActive) {
                flow = flow.copy(overlay = WorkoutOverlay.None)
            }

            if (
                session != null &&
                flow.overlay != WorkoutOverlay.ExerciseSummary &&
                WearWorkoutStateMachine.shouldAutoShowExerciseSummary(session, lastCompletedSets)
            ) {
                val exerciseId = session.exercises.getOrNull(session.exerciseIndex)?.exerciseId
                flow = flow.copy(
                    overlay = WorkoutOverlay.ExerciseSummary,
                    exerciseSummaryExerciseId = exerciseId,
                )
                scheduleExerciseSummaryDismiss()
            }
            lastCompletedSets = session?.exercises?.getOrNull(session.exerciseIndex ?: 0)?.completedSets ?: 0

            previous.copy(
                session = session,
                offline = connection !is PhoneConnectionState.Connected,
                queuedCount = queuedCount,
                health = health,
                workoutFlow = flow,
            )
        }.onEach { next ->
            _uiState.value = next
            monitorRestWarnings(next)
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

    fun navigateToHub() {
        _uiState.update {
            it.copy(workoutFlow = it.workoutFlow.copy(appPhase = WorkoutAppPhase.WorkoutHub))
        }
    }

    fun engageWorkout() {
        val session = _uiState.value.session
        prepareLogger()
        _uiState.update {
            it.copy(
                workoutFlow = it.workoutFlow.copy(
                    appPhase = WorkoutAppPhase.WorkoutActive,
                    horizontalPage = HorizontalPage.ActiveSet,
                    overlay = WorkoutOverlay.None,
                ),
            )
        }
        if (session?.sessionId?.isNotBlank() == true) {
            startSet()
        } else {
            requestSnapshot()
        }
    }

    fun dismissToWatchFace() {
        _uiState.update {
            it.copy(
                workoutFlow = WorkoutFlowState(appPhase = WorkoutAppPhase.WatchFace),
            )
        }
    }

    fun openLogConfirm() {
        _uiState.update {
            it.copy(workoutFlow = it.workoutFlow.copy(overlay = WorkoutOverlay.LogConfirm))
        }
    }

    fun cancelLogConfirm() {
        _uiState.update {
            it.copy(workoutFlow = it.workoutFlow.copy(overlay = WorkoutOverlay.None))
        }
    }

    fun confirmLog() {
        val session = _uiState.value.session ?: return
        val input = _uiState.value.loggerInput
        detectPersonalRecord(session, input.weightKg)
        submitLog()
        _uiState.update {
            it.copy(workoutFlow = it.workoutFlow.copy(overlay = WorkoutOverlay.None))
        }
    }

    fun dismissExerciseSummary() {
        _uiState.update {
            it.copy(
                workoutFlow = it.workoutFlow.copy(
                    overlay = WorkoutOverlay.None,
                    exerciseSummaryExerciseId = null,
                    pendingPrDeltaKg = null,
                ),
            )
        }
    }

    fun prepareLogger() {
        val session = _uiState.value.session ?: return
        _uiState.value = _uiState.value.copy(
            loggerInput = LoggerInputState(
                activeField = defaultLoggerField(session.loggingType),
                weightKg = session.targetWeight ?: 0f,
                reps = session.targetReps ?: 8,
                durationSeconds = session.targetDurationSeconds ?: 45,
                distanceKm = session.targetDistanceKm ?: 0f,
            ),
        )
    }

    fun toggleActiveField() {
        val session = _uiState.value.session ?: return
        val profile = session.loggingType
        val input = _uiState.value.loggerInput
        val next = when (profile) {
            LoggingTypePayload.WEIGHTED_REPS ->
                if (input.activeField == LoggerField.WEIGHT) LoggerField.REPS else LoggerField.WEIGHT
            LoggingTypePayload.BODYWEIGHT_REPS -> LoggerField.REPS
            LoggingTypePayload.TIMED -> LoggerField.DURATION
            LoggingTypePayload.CARDIO ->
                if (input.activeField == LoggerField.DURATION) LoggerField.DISTANCE else LoggerField.DURATION
        }
        _uiState.value = _uiState.value.copy(loggerInput = input.copy(activeField = next))
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

    fun adjustRestSeconds(deltaSeconds: Int) {
        _uiState.update {
            it.copy(
                workoutFlow = it.workoutFlow.copy(
                    restAdjustSeconds = it.workoutFlow.restAdjustSeconds + deltaSeconds,
                ),
            )
        }
    }

    fun clearRestAdjustment() {
        _uiState.update { it.copy(workoutFlow = it.workoutFlow.copy(restAdjustSeconds = 0)) }
    }

    fun setHorizontalPage(page: HorizontalPage) {
        _uiState.update { it.copy(workoutFlow = it.workoutFlow.copy(horizontalPage = page)) }
    }

    fun jumpToActiveSetPage() {
        setHorizontalPage(HorizontalPage.ActiveSet)
        cancelLogConfirm()
    }

    fun cycleHubWorkout(direction: Int) {
        val count = 5
        _uiState.update {
            val next = (it.workoutFlow.hubWorkoutIndex + direction).mod(count).let { idx ->
                if (idx < 0) idx + count else idx
            }
            it.copy(workoutFlow = it.workoutFlow.copy(hubWorkoutIndex = next))
        }
    }

    fun scrollWorkoutSummary(direction: Int) {
        val session = _uiState.value.session ?: return
        val max = session.exercises.size.coerceAtLeast(1)
        _uiState.update {
            val next = (it.workoutFlow.summaryDetailIndex + direction).coerceIn(0, max - 1)
            it.copy(workoutFlow = it.workoutFlow.copy(summaryDetailIndex = next))
        }
    }

    fun scrollMuscleGroups(direction: Int) {
        _uiState.update {
            it.copy(
                workoutFlow = it.workoutFlow.copy(
                    muscleScrollOffset = (it.workoutFlow.muscleScrollOffset + direction).coerceAtLeast(0),
                ),
            )
        }
    }

    fun adjustMediaVolume(direction: Int) {
        _uiState.update {
            it.copy(
                workoutFlow = it.workoutFlow.copy(
                    mediaVolume = (it.workoutFlow.mediaVolume + direction).coerceIn(0, 10),
                ),
            )
        }
    }

    fun requestUndoLastSet() {
        _uiState.update {
            it.copy(workoutFlow = it.workoutFlow.copy(undoConfirmVisible = true))
        }
    }

    fun confirmUndo() {
        val session = _uiState.value.session
        val last = session?.setHistory?.lastOrNull()
        sendAction(
            actionType = WatchActionType.REQUEST_SYNC,
            exerciseId = last?.exerciseId,
            setNumber = last?.setNumber,
        )
        _uiState.update {
            it.copy(workoutFlow = it.workoutFlow.copy(undoConfirmVisible = false))
        }
    }

    fun dismissUndo() {
        _uiState.update { it.copy(workoutFlow = it.workoutFlow.copy(undoConfirmVisible = false)) }
    }

    fun showWorkoutControls() {
        _uiState.update { it.copy(workoutFlow = it.workoutFlow.copy(workoutControlsVisible = true)) }
    }

    fun hideWorkoutControls() {
        _uiState.update { it.copy(workoutFlow = it.workoutFlow.copy(workoutControlsVisible = false)) }
    }

    fun startSet() {
        val session = _uiState.value.session
        sendAction(
            actionType = WatchActionType.START_SET,
            exerciseId = session?.exercises?.getOrNull(session.exerciseIndex)?.exerciseId,
            setNumber = session?.setIndex,
        )
    }

    fun submitLog() {
        val session = _uiState.value.session ?: return
        val input = _uiState.value.loggerInput
        val log = when (session.loggingType) {
            LoggingTypePayload.WEIGHTED_REPS -> SetLogPayload(weightKg = input.weightKg, reps = input.reps)
            LoggingTypePayload.BODYWEIGHT_REPS -> SetLogPayload(reps = input.reps)
            LoggingTypePayload.TIMED -> SetLogPayload(durationSeconds = input.durationSeconds)
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
        prepareLogger()
    }

    fun skipRest() {
        sendAction(
            actionType = WatchActionType.SKIP_REST,
            exerciseId = _uiState.value.session?.exercises?.getOrNull(_uiState.value.session?.exerciseIndex ?: 0)?.exerciseId,
            setNumber = _uiState.value.session?.setIndex,
        )
        clearRestAdjustment()
    }

    fun finishWorkout() {
        flushHealthMetrics()
        sendAction(actionType = WatchActionType.END_WORKOUT)
    }

    fun flushHealthMetrics() {
        viewModelScope.launch { healthMetricsSender.flushNow() }
    }

    private fun detectPersonalRecord(session: SessionStatePayload, weightKg: Float) {
        val exerciseId = session.exercises.getOrNull(session.exerciseIndex)?.exerciseId ?: return
        val best = session.setHistory
            .filter { it.exerciseId == exerciseId }
            .maxOfOrNull { it.weightKg } ?: 0f
        if (weightKg > best) {
            _uiState.update {
                it.copy(workoutFlow = it.workoutFlow.copy(pendingPrDeltaKg = weightKg - best))
            }
        }
    }

    private fun scheduleExerciseSummaryDismiss() {
        viewModelScope.launch {
            delay(2_000)
            if (_uiState.value.workoutFlow.overlay == WorkoutOverlay.ExerciseSummary) {
                dismissExerciseSummary()
            }
        }
    }

    private fun monitorRestWarnings(state: WearUiState) {
        val rest = (state.session?.restRemaining ?: 0) + state.workoutFlow.restAdjustSeconds
        if (state.session?.restActive == true && rest <= 10 && rest > 0 && !restWarningFired) {
            restWarningFired = true
        }
        if (state.session?.restActive != true) {
            restWarningFired = false
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
}
