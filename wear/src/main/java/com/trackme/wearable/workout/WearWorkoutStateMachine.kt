package com.trackme.wearable.workout

import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.SessionStatePayload

/**
 * Layer 1 — maps phone session sync + local overlays into a single derived UI phase.
 */
object WearWorkoutStateMachine {
    fun derive(state: WearUiState, flow: WorkoutFlowState): DerivedWorkoutUi {
        val session = state.session
        val hasSession = session?.sessionId?.isNotBlank() == true

        val appPhase = when {
            session?.isCompleted == true -> WorkoutAppPhase.WorkoutActive
            !hasSession -> WorkoutAppPhase.WatchFace
            else -> flow.appPhase
        }

        val screenPhase = when {
            session?.isCompleted == true -> WorkoutScreenPhase.WorkoutSummary
            flow.overlay == WorkoutOverlay.LogConfirm -> WorkoutScreenPhase.LogConfirm
            flow.overlay == WorkoutOverlay.ExerciseSummary -> WorkoutScreenPhase.ExerciseSummary
            session?.restActive == true -> WorkoutScreenPhase.RestTimer
            appPhase == WorkoutAppPhase.WorkoutActive -> WorkoutScreenPhase.SetReady
            else -> WorkoutScreenPhase.SetReady
        }

        val tint = when (screenPhase) {
            WorkoutScreenPhase.LogConfirm -> 0xFF071310
            WorkoutScreenPhase.RestTimer -> 0xFF00050F
            WorkoutScreenPhase.ExerciseSummary -> 0xFF000000
            WorkoutScreenPhase.WorkoutSummary -> 0xFF000000
            else -> 0xFF000000
        }

        return DerivedWorkoutUi(
            appPhase = appPhase,
            screenPhase = screenPhase,
            overlay = flow.overlay,
            horizontalPage = flow.horizontalPage,
            accentBackgroundTint = tint,
            showOfflineChip = state.offline,
            showHrUnavailableChip = state.health.heartRateBpm == null && hasSession,
        )
    }

    fun shouldAutoShowExerciseSummary(
        session: SessionStatePayload?,
        previousCompleted: Int,
    ): Boolean {
        val current = session ?: return false
        val exercise = current.exercises.getOrNull(current.exerciseIndex) ?: return false
        return exercise.completedSets >= exercise.targetSets &&
            exercise.completedSets > previousCompleted
    }
}
