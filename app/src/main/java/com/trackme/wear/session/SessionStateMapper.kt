package com.trackme.wear.session

import com.trackme.domain.model.Exercise
import com.trackme.domain.model.LoggingType
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.loggingType
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.ExerciseExecutionState
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionExercisePayload
import com.trackme.wearbridge.SessionStatePayload
import com.trackme.wearbridge.SetHistoryPayload
import kotlin.math.roundToInt

fun ActiveSessionUiState.toWearPayload(now: Long = System.currentTimeMillis()): SessionStatePayload {
    val orderedExercises = exercises.sortedBy { it.first.orderIndex }
    val activeOrRestingExerciseId = activeExerciseId ?: restingExerciseId
    val currentIndex = orderedExercises.indexOfFirst { it.first.exerciseId == activeOrRestingExerciseId }
        .takeIf { it >= 0 }
        ?: orderedExercises.indexOfFirst { (planned, _) ->
            (loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
        }.takeIf { it >= 0 }
        ?: 0

    val current = orderedExercises.getOrNull(currentIndex)
    val next = orderedExercises.drop(currentIndex + 1).firstOrNull { (planned, _) ->
        (loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
    }

    val totalSets = orderedExercises.sumOf { it.first.targetSets }
    val completedSets = loggedSets.count { it.completed }
    val progress = if (totalSets > 0) {
        ((completedSets.toFloat() / totalSets.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        0
    }
    val totalVolume = loggedSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()

    val currentPlanned = current?.first
    val currentExercise = current?.second
    val currentExerciseId = currentPlanned?.exerciseId.orEmpty()
    val currentCompletedSets = loggedSetsByExercise[currentExerciseId]?.size ?: 0
    val loggingType = currentExercise?.loggingType()?.toWearLoggingType() ?: LoggingTypePayload.WEIGHTED_REPS

    return SessionStatePayload(
        sessionId = sessionId,
        dayId = dayId.takeIf { it.isNotBlank() },
        sessionDateMillis = sessionDateMillis,
        exerciseIndex = currentIndex.coerceAtLeast(0),
        setIndex = currentCompletedSets + 1,
        completedSets = completedSets,
        totalSets = totalSets,
        restRemaining = if (restTimerRunning) restSecondsRemaining else 0,
        exerciseName = currentExercise?.name.orEmpty(),
        nextExerciseName = next?.second?.name,
        muscle = currentExercise?.primaryMuscles?.joinToString(", ").orEmpty(),
        equipment = currentExercise?.equipment.orEmpty(),
        instructionSummary = currentExercise?.instructions?.firstOrNull().orEmpty(),
        loggingType = loggingType,
        targetReps = currentPlanned?.targetReps,
        targetWeight = currentPlanned?.targetWeightKg,
        targetDurationSeconds = currentPlanned?.targetDurationSeconds,
        targetDistanceKm = currentPlanned?.targetDistanceKm,
        sessionProgressPercent = progress,
        totalVolumeKg = totalVolume,
        restActive = restTimerRunning || restingExerciseId != null,
        isPaused = isPaused,
        isCompleted = isCompleted,
        updatedAt = now,
        exercises = orderedExercises.map { (planned, exercise) -> planned.toWearExercise(exercise, this) },
        setHistory = loggedSets.sortedWith(compareBy({ it.exerciseId }, { it.setNumber })).map { set ->
            SetHistoryPayload(
                setId = set.id,
                exerciseId = set.exerciseId,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                durationSeconds = set.durationSeconds,
                distanceKm = set.distanceKm,
                speedKmh = set.speedKmh,
                inclinePercent = set.inclinePercent,
                completedAt = set.updatedAt,
            )
        },
    )
}

private fun PlannedExercise.toWearExercise(exercise: Exercise?, state: ActiveSessionUiState): SessionExercisePayload {
    val completedSets = state.loggedSetsByExercise[exerciseId]?.size ?: 0
    val executionState = state.executionStates[exerciseId] ?: ExerciseExecutionState.IDLE
    return SessionExercisePayload(
        exerciseId = exerciseId,
        plannedExerciseId = id,
        orderIndex = orderIndex,
        exerciseName = exercise?.name.orEmpty(),
        muscle = exercise?.primaryMuscles?.joinToString(", ").orEmpty(),
        equipment = exercise?.equipment.orEmpty(),
        instructionSummary = exercise?.instructions?.firstOrNull().orEmpty(),
        loggingType = exercise?.loggingType()?.toWearLoggingType() ?: LoggingTypePayload.WEIGHTED_REPS,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeight = targetWeightKg,
        targetDurationSeconds = targetDurationSeconds,
        targetDistanceKm = targetDistanceKm,
        targetSpeedKmh = targetSpeedKmh,
        targetIncline = targetIncline,
        completedSets = completedSets,
        isActive = executionState == ExerciseExecutionState.ACTIVE_SET,
        isResting = executionState == ExerciseExecutionState.RESTING,
        isCompleted = executionState == ExerciseExecutionState.COMPLETED,
    )
}

private fun LoggingType.toWearLoggingType(): LoggingTypePayload =
    when (this) {
        LoggingType.WEIGHTED_REPS -> LoggingTypePayload.WEIGHTED_REPS
        LoggingType.BODYWEIGHT_REPS -> LoggingTypePayload.BODYWEIGHT_REPS
        LoggingType.TIMED -> LoggingTypePayload.TIMED
        LoggingType.CARDIO -> LoggingTypePayload.CARDIO
    }
