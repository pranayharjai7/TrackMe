package com.trackme.ui.workout.session.notification

import com.trackme.R
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.ExerciseExecutionState
import kotlin.math.roundToInt

/**
 * Maps [ActiveSessionUiState] into a notification-specific presentation model.
 */
object WorkoutNotificationStateMapper {

    fun map(
        state: ActiveSessionUiState,
        nowMillis: Long = System.currentTimeMillis(),
    ): WorkoutNotificationModel {
        val notificationState = resolveNotificationState(state)
        val focusExerciseId = state.activeExerciseId
            ?: state.restingExerciseId
            ?: findNextIncompleteExerciseId(state)

        val focusPair = state.exercises.find { it.first.exerciseId == focusExerciseId }
        val focusPlanned = focusPair?.first
        val focusExercise = focusPair?.second

        val loggedForFocus = focusExerciseId?.let { state.loggedSetsByExercise[it].orEmpty() }.orEmpty()
        val currentSetNumber = loggedForFocus.size + 1
        val targetSets = focusPlanned?.targetSets ?: 0
        val targetReps = focusPlanned?.targetReps ?: 0
        val targetWeight = focusPlanned?.targetWeightKg ?: 0f

        val previousSet = loggedForFocus.lastOrNull()
        val previousSetSummary = previousSet?.let {
            "${formatWeight(it.weightKg)} × ${it.reps}"
        } ?: "—"

        val totalTargetSets = state.exercises.sumOf { it.first.targetSets }
        val completedSetsTotal = state.loggedSets.size
        val workoutProgressPercent = if (totalTargetSets > 0) {
            ((completedSetsTotal * 100f) / totalTargetSets).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val setProgressPercent = if (targetSets > 0) {
            ((loggedForFocus.size * 100f) / targetSets).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val totalVolumeKg = state.loggedSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()

        val elapsedFormatted = formatElapsed(
            startMillis = state.sessionStartTimeMillis.takeIf { it > 0 } ?: nowMillis,
            nowMillis = nowMillis,
            isPaused = state.isPaused,
        )

        val restTotal = state.restSeconds.coerceAtLeast(1)
        val restRemaining = state.restSecondsRemaining.coerceAtLeast(0)
        val restProgressPercent = if (notificationState == WorkoutNotificationState.RESTING) {
            (((restTotal - restRemaining).toFloat() / restTotal) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val nextExerciseName = findNextExerciseName(state, focusExerciseId)
        val currentExerciseName = focusExercise?.name ?: "Ready"
        val muscleGroupLabel = focusExercise?.primaryMuscles?.firstOrNull()?.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase() else c.toString()
        } ?: ""

        val metaLine = buildMetaLine(
            notificationState = notificationState,
            setNumber = currentSetNumber,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeight = targetWeight,
            quickReps = state.quickReps,
            quickWeight = state.quickWeight,
        )
        val subStatusLine = buildSubStatusLine(state, notificationState, nextExerciseName, restRemaining)

        val (statusIcon, exerciseIcon) = iconResources(notificationState, state.isRestUrgent(restRemaining))

        val controls = collapsedControls(state, notificationState)
        val (primaryLabel, primaryAction) = primaryAction(state, notificationState)
        val displayPlan = NotificationDisplayPlan.from(notificationState)
        val contextLine = buildContextLine(
            notificationState = notificationState,
            nextExercise = nextExerciseName,
            targetReps = targetReps,
            targetWeight = targetWeight,
            quickReps = state.quickReps,
            quickWeight = state.quickWeight,
            previousSetSummary = previousSetSummary,
        )

        return WorkoutNotificationModel(
            notificationState = notificationState,
            sessionId = state.sessionId,
            workoutTitle = currentExerciseName,
            statusLine = metaLine,
            subStatusLine = subStatusLine,
            currentExerciseName = currentExerciseName,
            nextExerciseName = nextExerciseName,
            muscleGroupLabel = muscleGroupLabel,
            currentSetNumber = currentSetNumber.coerceAtLeast(1),
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeightKg = targetWeight,
            previousSetSummary = previousSetSummary,
            completedSetsTotal = completedSetsTotal,
            totalTargetSets = totalTargetSets,
            workoutProgressPercent = workoutProgressPercent,
            setProgressPercent = setProgressPercent,
            totalVolumeKg = totalVolumeKg,
            elapsedFormatted = elapsedFormatted,
            restSecondsRemaining = restRemaining,
            restSecondsTotal = restTotal,
            restProgressPercent = restProgressPercent,
            isRestUrgent = state.isRestUrgent(restRemaining),
            quickWeight = state.quickWeight,
            quickReps = state.quickReps,
            statusIconRes = statusIcon,
            exerciseIconRes = exerciseIcon,
            showCollapsedStart = controls.showStart,
            showCollapsedComplete = controls.showComplete,
            showCollapsedSkipRest = controls.showSkipRest,
            showRestPanel = notificationState == WorkoutNotificationState.RESTING,
            showQuickLogPanel = notificationState == WorkoutNotificationState.ACTIVE_SET ||
                notificationState == WorkoutNotificationState.SESSION_IDLE,
            pauseButtonLabel = if (state.isPaused) "Resume" else "Pause",
            primaryActionLabel = primaryLabel,
            primaryAction = primaryAction,
            displayPlan = displayPlan,
            contextLine = contextLine,
        )
    }

    private fun buildContextLine(
        notificationState: WorkoutNotificationState,
        nextExercise: String,
        targetReps: Int,
        targetWeight: Float,
        quickReps: Int,
        quickWeight: Float,
        previousSetSummary: String,
    ): String = when (notificationState) {
        WorkoutNotificationState.RESTING -> "Next · $nextExercise"
        WorkoutNotificationState.WORKOUT_PAUSED -> "Tap resume to continue"
        WorkoutNotificationState.WORKOUT_COMPLETED -> "Great work today"
        WorkoutNotificationState.EXERCISE_COMPLETED -> "Up next · $nextExercise"
        WorkoutNotificationState.SESSION_IDLE -> "Up next · $nextExercise"
        WorkoutNotificationState.ACTIVE_SET -> {
            val reps = if (quickReps > 0) quickReps else targetReps
            val weight = if (quickWeight > 0f) quickWeight else targetWeight
            if (previousSetSummary != "—") {
                "${formatWeight(weight)} × $reps · prev $previousSetSummary"
            } else {
                "${formatWeight(weight)} × $reps"
            }
        }
    }

    fun resolveNotificationState(state: ActiveSessionUiState): WorkoutNotificationState {
        if (state.sessionId.isEmpty()) return WorkoutNotificationState.SESSION_IDLE
        if (state.isCompleted) return WorkoutNotificationState.WORKOUT_COMPLETED
        if (state.isPaused) return WorkoutNotificationState.WORKOUT_PAUSED
        if (state.restingExerciseId != null) return WorkoutNotificationState.RESTING
        if (state.activeExerciseId != null) return WorkoutNotificationState.ACTIVE_SET

        val hasIncomplete = state.exercises.any { (planned, _) ->
            (state.loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
        }
        val hasCompleted = state.exercises.any { (planned, _) ->
            state.executionStates[planned.exerciseId] == ExerciseExecutionState.COMPLETED
        }
        if (hasCompleted && hasIncomplete) return WorkoutNotificationState.EXERCISE_COMPLETED

        return WorkoutNotificationState.SESSION_IDLE
    }

    private fun ActiveSessionUiState.isRestUrgent(restRemaining: Int): Boolean =
        restingExerciseId != null && restRemaining in 0..10

    private fun findNextIncompleteExerciseId(state: ActiveSessionUiState): String? =
        state.exercises.firstOrNull { (planned, _) ->
            (state.loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
        }?.first?.exerciseId

    private fun findNextExerciseName(state: ActiveSessionUiState, currentId: String?): String {
        val ordered = state.exercises
        val index = ordered.indexOfFirst { it.first.exerciseId == currentId }
        val next = if (index != -1 && index < ordered.size - 1) ordered[index + 1] else null
        return next?.second?.name ?: "Finish strong"
    }

    private fun buildMetaLine(
        notificationState: WorkoutNotificationState,
        setNumber: Int,
        targetSets: Int,
        targetReps: Int,
        targetWeight: Float,
        quickReps: Int,
        quickWeight: Float,
    ): String = when (notificationState) {
        WorkoutNotificationState.WORKOUT_PAUSED -> "Paused"
        WorkoutNotificationState.WORKOUT_COMPLETED -> "Session complete"
        WorkoutNotificationState.RESTING -> "Rest before next set"
        WorkoutNotificationState.EXERCISE_COMPLETED -> "Exercise done"
        WorkoutNotificationState.SESSION_IDLE -> "Tap to start"
        WorkoutNotificationState.ACTIVE_SET -> {
            val reps = if (quickReps > 0) quickReps else targetReps
            val weight = if (quickWeight > 0f) quickWeight else targetWeight
            "Set $setNumber of $targetSets · ${formatWeight(weight)} × $reps"
        }
    }

    private fun buildSubStatusLine(
        state: ActiveSessionUiState,
        notificationState: WorkoutNotificationState,
        nextExercise: String,
        restRemaining: Int,
    ): String = when (notificationState) {
        WorkoutNotificationState.RESTING -> "Next · $nextExercise"
        WorkoutNotificationState.ACTIVE_SET -> "${state.loggedSets.size} sets logged this session"
        WorkoutNotificationState.EXERCISE_COMPLETED -> "Up next · $nextExercise"
        WorkoutNotificationState.WORKOUT_PAUSED -> "Resume when ready"
        WorkoutNotificationState.WORKOUT_COMPLETED -> "Nice work today"
        WorkoutNotificationState.SESSION_IDLE -> "Up next · $nextExercise"
    }

    fun formatRestTime(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        return if (m > 0) String.format("%d:%02d", m, s) else "${s}s"
    }

    private data class CollapsedControls(
        val showStart: Boolean,
        val showComplete: Boolean,
        val showSkipRest: Boolean,
    )

    private fun collapsedControls(
        state: ActiveSessionUiState,
        notificationState: WorkoutNotificationState,
    ): CollapsedControls = when (notificationState) {
        WorkoutNotificationState.RESTING -> CollapsedControls(false, false, true)
        WorkoutNotificationState.ACTIVE_SET -> CollapsedControls(false, true, false)
        WorkoutNotificationState.SESSION_IDLE,
        WorkoutNotificationState.EXERCISE_COMPLETED,
        -> CollapsedControls(true, false, false)
        else -> CollapsedControls(false, false, false)
    }

    private fun primaryAction(
        state: ActiveSessionUiState,
        notificationState: WorkoutNotificationState,
    ): Pair<String, String> = when (notificationState) {
        WorkoutNotificationState.RESTING -> "Start next set" to WorkoutNotificationActions.ACTION_START_SET
        WorkoutNotificationState.ACTIVE_SET -> "Complete set" to WorkoutNotificationActions.ACTION_COMPLETE_SET
        WorkoutNotificationState.WORKOUT_PAUSED -> "Resume workout" to WorkoutNotificationActions.ACTION_RESUME_WORKOUT
        else -> "Start set" to WorkoutNotificationActions.ACTION_START_SET
    }

    private fun iconResources(
        notificationState: WorkoutNotificationState,
        urgent: Boolean,
    ): Pair<Int, Int> = when (notificationState) {
        WorkoutNotificationState.RESTING -> {
            if (urgent) R.drawable.ic_notification_rest_urgent to R.drawable.ic_notification_exercise
            else R.drawable.ic_notification_rest to R.drawable.ic_notification_exercise
        }
        WorkoutNotificationState.ACTIVE_SET -> R.drawable.ic_notification_active to R.drawable.ic_notification_exercise
        WorkoutNotificationState.WORKOUT_PAUSED -> R.drawable.ic_notification_paused to R.drawable.ic_notification_exercise
        WorkoutNotificationState.WORKOUT_COMPLETED -> R.drawable.ic_notification_complete to R.drawable.ic_notification_exercise
        else -> R.drawable.ic_notification_workout to R.drawable.ic_notification_exercise
    }

    fun formatElapsed(startMillis: Long, nowMillis: Long, isPaused: Boolean): String {
        val elapsedSec = ((nowMillis - startMillis) / 1000).coerceAtLeast(0)
        val hours = elapsedSec / 3600
        val minutes = (elapsedSec % 3600) / 60
        val seconds = elapsedSec % 60
        val base = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
        return if (isPaused) "$base · Paused" else base
    }

    fun formatWeight(kg: Float): String =
        if (kg % 1f == 0f) "${kg.toInt()} kg" else String.format("%.1f kg", kg)

    fun formatVolume(kg: Float): String =
        if (kg >= 1000f) String.format("%.1f t", kg / 1000f)
        else if (kg % 1f == 0f) "${kg.toInt()} kg"
        else String.format("%.0f kg", kg)
}
