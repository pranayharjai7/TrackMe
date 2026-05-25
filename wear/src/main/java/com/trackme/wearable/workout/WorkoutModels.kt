package com.trackme.wearable.workout

import androidx.compose.runtime.Immutable
import com.trackme.wearable.viewmodel.LoggerField
import com.trackme.wearable.viewmodel.LoggerInputState
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionStatePayload

enum class WorkoutAppPhase {
    WatchFace,
    WorkoutHub,
    WorkoutActive,
}

enum class WorkoutScreenPhase {
    SetReady,
    LogConfirm,
    RestTimer,
    ExerciseSummary,
    WorkoutSummary,
}

enum class WorkoutOverlay {
    None,
    LogConfirm,
    ExerciseSummary,
    WorkoutControls,
    UndoConfirm,
}

enum class HorizontalPage(val index: Int) {
    HrZones(0),
    ActiveSet(1),
    MuscleMap(2),
    Media(3),
    ;

    companion object {
        fun fromIndex(index: Int): HorizontalPage =
            entries.firstOrNull { it.index == index } ?: ActiveSet
    }
}

enum class ExerciseLayoutProfile {
    Strength,
    Bodyweight,
    Timed,
    Cardio,
    Interval,
    Distance,
}

@Immutable
data class WorkoutFlowState(
    val appPhase: WorkoutAppPhase = WorkoutAppPhase.WatchFace,
    val overlay: WorkoutOverlay = WorkoutOverlay.None,
    val horizontalPage: HorizontalPage = HorizontalPage.ActiveSet,
    val hubWorkoutIndex: Int = 0,
    val restAdjustSeconds: Int = 0,
    val exerciseSummaryExerciseId: String? = null,
    val pendingPrDeltaKg: Float? = null,
    val summaryDetailIndex: Int = 0,
    val mediaVolume: Int = 5,
    val muscleScrollOffset: Int = 0,
    val workoutControlsVisible: Boolean = false,
    val undoConfirmVisible: Boolean = false,
)

@Immutable
data class DerivedWorkoutUi(
    val appPhase: WorkoutAppPhase,
    val screenPhase: WorkoutScreenPhase,
    val overlay: WorkoutOverlay,
    val horizontalPage: HorizontalPage,
    val accentBackgroundTint: Long,
    val showOfflineChip: Boolean,
    val showHrUnavailableChip: Boolean,
)

@Immutable
data class ReadinessUi(
    val score: Int,
    val label: String,
    val ringProgress: Float,
)

@Immutable
data class HubWorkoutItem(
    val name: String,
    val exerciseCount: Int,
)

fun LoggingTypePayload.toLayoutProfile(): ExerciseLayoutProfile =
    when (this) {
        LoggingTypePayload.WEIGHTED_REPS -> ExerciseLayoutProfile.Strength
        LoggingTypePayload.BODYWEIGHT_REPS -> ExerciseLayoutProfile.Bodyweight
        LoggingTypePayload.TIMED -> ExerciseLayoutProfile.Timed
        LoggingTypePayload.CARDIO -> ExerciseLayoutProfile.Cardio
    }

fun LoggerInputState.primaryDisplayValue(profile: ExerciseLayoutProfile, activeField: LoggerField): String =
    when (profile) {
        ExerciseLayoutProfile.Strength,
        ExerciseLayoutProfile.Bodyweight,
        -> when (activeField) {
            LoggerField.WEIGHT -> formatWeight(weightKg)
            else -> reps.toString()
        }
        ExerciseLayoutProfile.Timed -> formatDuration(durationSeconds)
        ExerciseLayoutProfile.Cardio,
        ExerciseLayoutProfile.Distance,
        -> when (activeField) {
            LoggerField.DISTANCE -> formatDistance(distanceKm)
            LoggerField.DURATION -> formatDuration(durationSeconds)
            else -> formatPace(durationSeconds, distanceKm)
        }
        ExerciseLayoutProfile.Interval -> formatDuration(durationSeconds)
    }

fun LoggerInputState.secondaryLine(profile: ExerciseLayoutProfile, activeField: LoggerField): String =
    when (profile) {
        ExerciseLayoutProfile.Strength -> "× ${reps} reps"
        ExerciseLayoutProfile.Bodyweight -> "× ${reps} reps"
        ExerciseLayoutProfile.Timed -> "hold"
        ExerciseLayoutProfile.Cardio,
        ExerciseLayoutProfile.Distance,
        -> when (activeField) {
            LoggerField.DURATION -> formatDistance(distanceKm)
            LoggerField.DISTANCE -> formatDuration(durationSeconds)
            else -> formatPace(durationSeconds, distanceKm)
        }
        ExerciseLayoutProfile.Interval -> "interval"
    }

fun toggleLoggerField(profile: ExerciseLayoutProfile, current: LoggerField): LoggerField =
    when (profile) {
        ExerciseLayoutProfile.Strength -> if (current == LoggerField.WEIGHT) LoggerField.REPS else LoggerField.WEIGHT
        ExerciseLayoutProfile.Bodyweight -> LoggerField.REPS
        ExerciseLayoutProfile.Timed -> LoggerField.DURATION
        ExerciseLayoutProfile.Cardio,
        ExerciseLayoutProfile.Distance,
        -> when (current) {
            LoggerField.DURATION -> LoggerField.DISTANCE
            LoggerField.DISTANCE -> LoggerField.DURATION
            else -> LoggerField.DURATION
        }
        ExerciseLayoutProfile.Interval -> LoggerField.DURATION
    }

fun defaultLoggerField(loggingType: LoggingTypePayload): LoggerField =
    when (loggingType.toLayoutProfile()) {
        ExerciseLayoutProfile.Strength -> LoggerField.WEIGHT
        ExerciseLayoutProfile.Bodyweight -> LoggerField.REPS
        ExerciseLayoutProfile.Timed -> LoggerField.DURATION
        ExerciseLayoutProfile.Cardio,
        ExerciseLayoutProfile.Distance,
        ExerciseLayoutProfile.Interval,
        -> LoggerField.DURATION
    }

fun computeReadiness(state: WearUiState): ReadinessUi {
    val hr = state.health.heartRateBpm?.toInt()
    val score = when {
        hr == null -> 78
        hr <= 55 -> 92
        hr <= 62 -> 85
        hr <= 70 -> 78
        hr <= 80 -> 65
        else -> 52
    }
    val label = when {
        score >= 85 -> "good"
        score >= 70 -> "fair"
        else -> "recover"
    }
    return ReadinessUi(score = score, label = label, ringProgress = score / 100f)
}

fun hubWorkouts(state: WearUiState): List<HubWorkoutItem> {
    val session = state.session
    val primary = HubWorkoutItem(
        name = session?.exercises?.firstOrNull()?.exerciseName?.let { "Today's session" }
            ?: "Push Day",
        exerciseCount = session?.exercises?.size ?: 6,
    )
    val recents = listOf(
        HubWorkoutItem("Push A", 6),
        HubWorkoutItem("Pull B", 5),
        HubWorkoutItem("Legs", 7),
        HubWorkoutItem("Upper", 4),
    )
    return (listOf(primary) + recents).distinctBy { it.name }.take(5)
}

fun setProgressWithinExercise(session: SessionStatePayload): Float {
    val exercise = session.exercises.getOrNull(session.exerciseIndex) ?: return 0f
    val total = exercise.targetSets.coerceAtLeast(1)
    return (exercise.completedSets.coerceAtMost(total).toFloat() / total).coerceIn(0f, 1f)
}

fun formatWeight(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else String.format(java.util.Locale.US, "%.1f", kg)

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

fun formatDistance(km: Float): String =
    if (km % 1f == 0f) "${km.toInt()} km" else String.format(java.util.Locale.US, "%.1f km", km)

fun formatPace(durationSeconds: Int, distanceKm: Float): String {
    if (distanceKm <= 0f) return "—"
    val paceSec = (durationSeconds / distanceKm).toInt().coerceAtLeast(0)
    return "${paceSec / 60}:${(paceSec % 60).toString().padStart(2, '0')} /km"
}

fun hrZone(bpm: Int): Pair<Int, String> =
    when {
        bpm < 100 -> 1 to "ZONE 1"
        bpm < 120 -> 2 to "ZONE 2"
        bpm < 140 -> 3 to "ZONE 3"
        bpm < 160 -> 4 to "ZONE 4"
        else -> 5 to "ZONE 5"
    }
