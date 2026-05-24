package com.trackme.ui.workout.session

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.ui.onboarding.DEFAULT_INPUT_STYLE
import com.trackme.ui.workout.session.notification.WorkoutNotificationActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class ExerciseExecutionState {
    IDLE,
    ACTIVE_SET,
    RESTING,
    COMPLETED
}

/**
 * Representation of the screen's UI State.
 * Includes paused state and in-notification quick logger values.
 */
data class ActiveSessionUiState(
    val sessionId: String = "",
    val dayId: String = "",
    val sessionDateMillis: Long? = null,
    val exercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val loggedSets: List<SessionSet> = emptyList(),
    val loggedSetsByExercise: Map<String, List<SessionSet>> = emptyMap(),
    val executionStates: Map<String, ExerciseExecutionState> = emptyMap(),
    val activeExerciseId: String? = null,
    val restingExerciseId: String? = null,
    val restTimerRunning: Boolean = false,
    val restSeconds: Int = 90,
    val restSecondsRemaining: Int = 90,
    val isFinishing: Boolean = false,
    val inputStyle: String = DEFAULT_INPUT_STYLE,
    val isCompleted: Boolean = false,
    val isHistoricalSession: Boolean = false,
    val isPaused: Boolean = false,
    val quickWeight: Float = 0f,
    val quickReps: Int = 0,
    val sessionStartTimeMillis: Long = 0L,
)

/**
 * ViewModel responsible for a live workout session.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Delegate Pattern:
 * Delegates all workout logic to [WorkoutSessionManager] to ensure the screen and the foreground
 * notification service stay perfectly synchronized at all times.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val sessionManager: WorkoutSessionManager,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])
    private val dateMillisStr: String? = savedStateHandle["dateMillis"]
    private val sessionDateMillis = dateMillisStr?.toLongOrNull() ?: System.currentTimeMillis()
    
    // Bind UI state directly to the central manager's state
    val uiState: StateFlow<ActiveSessionUiState> = sessionManager.uiState

    init {
        // Trigger initialization or session resumption in the central state coordinator
        sessionManager.startOrResumeSession("", dayId, sessionDateMillis)

        // Start Ongoing Workout Foreground Service
        val intent = Intent(context, WorkoutSessionService::class.java).apply {
            action = WorkoutNotificationActions.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startExercise(exerciseId: String) = sessionManager.startExercise(exerciseId)
    
    fun cancelActiveSet(exerciseId: String) = sessionManager.cancelActiveSet(exerciseId)
    
    fun completeSet(
        exerciseId: String,
        weightKg: Float,
        reps: Int,
        durationSeconds: Int? = null,
        distanceKm: Float? = null,
        speedKmh: Float? = null,
        inclinePercent: Float? = null,
    ) = sessionManager.completeSet(
        exerciseId = exerciseId,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        speedKmh = speedKmh,
        inclinePercent = inclinePercent
    )

    fun skipRest(exerciseId: String) = sessionManager.skipRest(exerciseId)

    fun logSet(
        exerciseId: String,
        setNumber: Int,
        weightKg: Float,
        reps: Int,
        durationSeconds: Int? = null,
        distanceKm: Float? = null,
        speedKmh: Float? = null,
        inclinePercent: Float? = null,
    ) = sessionManager.logSet(
        exerciseId = exerciseId,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        speedKmh = speedKmh,
        inclinePercent = inclinePercent
    )

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
    ) = sessionManager.editSet(
        setId = setId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        speedKmh = speedKmh,
        inclinePercent = inclinePercent
    )

    fun deleteSet(set: SessionSet) = sessionManager.deleteSet(set)

    fun stopRestTimer() = sessionManager.stopRestTimer()

    fun setRestDuration(seconds: Int) = sessionManager.setRestDuration(seconds)

    fun finishSession(onDone: () -> Unit) = sessionManager.finishSession(onDone)

    fun updateTargetSets(exerciseId: String, sets: Int) = sessionManager.updateTargetSets(exerciseId, sets)

    fun setsForExercise(exerciseId: String): List<SessionSet> = sessionManager.setsForExercise(exerciseId)

    fun plannedFor(exerciseId: String): PlannedExercise? = sessionManager.plannedFor(exerciseId)

    fun addExercise(exerciseId: String) = sessionManager.addExercise(exerciseId)

    // Additional Notification and Real-Time Controls
    fun pauseWorkout() = sessionManager.pauseWorkout()
    
    fun resumeWorkout() = sessionManager.resumeWorkout()
    
    fun skipExercise(exerciseId: String) = sessionManager.skipExercise(exerciseId)
    
    fun previousExercise(exerciseId: String) = sessionManager.previousExercise(exerciseId)
    
    fun adjustQuickWeight(delta: Float) = sessionManager.adjustQuickWeight(delta)
    
    fun adjustQuickReps(delta: Int) = sessionManager.adjustQuickReps(delta)
    
    fun saveQuickSet() = sessionManager.saveQuickSet()
    
    fun adjustRestTime(seconds: Int) = sessionManager.adjustRestTime(seconds)
}
