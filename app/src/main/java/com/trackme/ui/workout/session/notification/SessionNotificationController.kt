package com.trackme.ui.workout.session.notification

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.WorkoutSessionManager
import com.trackme.ui.workout.session.WorkoutSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Observes session state and drives efficient notification updates for [WorkoutSessionService].
 */
class SessionNotificationController(
    private val context: Context,
    private val sessionManager: WorkoutSessionManager,
    private val notificationManager: NotificationManager,
    private val renderer: WorkoutNotificationRenderer,
    private val onStopService: () -> Unit,
    private val onStartForeground: (android.app.Notification) -> Unit,
) {
    private var wasResting = false
    private var elapsedTickerJob: Job? = null
    private var lastPostedModel: WorkoutNotificationModel? = null
    private var lastUiState: ActiveSessionUiState? = null

    fun start(scope: CoroutineScope) {
        WorkoutNotificationLogger.i("SessionNotificationController started")

        scope.launch {
            sessionManager.uiState
                .map { WorkoutNotificationStateMapper.map(it) }
                .distinctUntilChanged()
                .collect { model ->
                    onModelUpdated(model)
                }
        }

        scope.launch {
            sessionManager.uiState.collect { state ->
                lastUiState = state
                handleRestCompletion(state)
                manageElapsedTicker(state, scope)
            }
        }
    }

    private fun onModelUpdated(model: WorkoutNotificationModel) {
        if (model.sessionId.isEmpty()) {
            WorkoutNotificationLogger.i("Session ended — stopping service")
            notificationManager.cancel(WorkoutSessionService.ALERT_NOTIFICATION_ID)
            renderer.reset()
            onStopService()
            return
        }

        if (model.notificationState == WorkoutNotificationState.WORKOUT_COMPLETED) {
            postNotification(model, throttle = false)
            return
        }

        val throttle = shouldThrottle(model)
        postNotification(model, throttle)
    }

    private fun postNotification(model: WorkoutNotificationModel, throttle: Boolean) {
        if (throttle && model == lastPostedModel) return

        val notification = renderer.buildNotification(
            model = model,
            channelId = WorkoutSessionService.ONGOING_CHANNEL_ID,
        )
        onStartForeground(notification)
        notificationManager.notify(WorkoutSessionService.ONGOING_NOTIFICATION_ID, notification)
        lastPostedModel = model

        WorkoutNotificationLogger.d("Notification updated: ${model.notificationState}")
    }

    private fun shouldThrottle(model: WorkoutNotificationModel): Boolean {
        val previous = lastPostedModel ?: return false
        if (model.notificationState == WorkoutNotificationState.RESTING) return false
        if (model.notificationState == WorkoutNotificationState.ACTIVE_SET) return false
        if (model.notificationState == WorkoutNotificationState.WORKOUT_PAUSED) {
            return previous.elapsedFormatted == model.elapsedFormatted
        }
        return previous == model
    }

    private fun handleRestCompletion(state: ActiveSessionUiState) {
        val isResting = state.restingExerciseId != null
        if (!isResting && wasResting && state.restSecondsRemaining == 0 && !state.isPaused) {
            triggerRestFinishedFeedback()
        }
        wasResting = isResting
    }

    private fun triggerRestFinishedFeedback() {
        WorkoutNotificationLogger.i("Rest timer completed — alerting user")
        val alert = renderer.buildRestFinishedAlert(
            sessionManager.uiState.value.sessionId,
        )
        notificationManager.notify(WorkoutSessionService.ALERT_NOTIFICATION_ID, alert)
        vibrateRestComplete()
        playRestCompleteSound()
    }

    fun cancelRestAlert() {
        notificationManager.cancel(WorkoutSessionService.ALERT_NOTIFICATION_ID)
    }

    private fun manageElapsedTicker(state: ActiveSessionUiState, scope: CoroutineScope) {
        val shouldTick = state.sessionId.isNotEmpty() &&
            !state.isCompleted &&
            !state.isPaused &&
            state.restingExerciseId == null

        if (!shouldTick) {
            elapsedTickerJob?.cancel()
            elapsedTickerJob = null
            return
        }

        if (elapsedTickerJob?.isActive == true) return

        elapsedTickerJob = scope.launch {
            while (isActive) {
                val ui = lastUiState ?: break
                if (ui.sessionId.isEmpty() || ui.isPaused || ui.restTimerRunning) break
                val model = WorkoutNotificationStateMapper.map(ui)
                postNotification(model, throttle = true)
                delay(ELAPSED_TICK_MS)
            }
        }
    }

    private fun vibrateRestComplete() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 180), -1),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 120, 60, 180), -1)
        }
    }

    private fun playRestCompleteSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }.onFailure {
            WorkoutNotificationLogger.w("Unable to play rest-complete sound", it)
        }
    }

    companion object {
        private const val ELAPSED_TICK_MS = 5_000L
    }
}
