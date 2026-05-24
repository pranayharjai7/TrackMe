package com.trackme.ui.workout.session.notification

import com.trackme.ui.workout.session.WorkoutSessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches notification actions to [WorkoutSessionManager] without duplicating domain logic.
 */
@Singleton
class WorkoutNotificationActionHandler @Inject constructor(
    private val sessionManager: WorkoutSessionManager,
) {
    private var lastAction: String? = null
    private var lastActionAtMs: Long = 0L

    fun handle(action: String): Boolean {
        val now = System.currentTimeMillis()
        if (action == lastAction && now - lastActionAtMs < DUPLICATE_WINDOW_MS) {
            WorkoutNotificationLogger.d("Ignored duplicate action: $action")
            return false
        }
        lastAction = action
        lastActionAtMs = now

        val state = sessionManager.uiState.value
        if (state.sessionId.isEmpty() && action != WorkoutNotificationActions.ACTION_STOP_SERVICE) {
            WorkoutNotificationLogger.w("Ignoring action $action — no active session")
            return false
        }

        val activeExId = state.activeExerciseId
        val restingExId = state.restingExerciseId

        WorkoutNotificationLogger.i("Dispatch action: $action")

        when (action) {
            WorkoutNotificationActions.ACTION_START_SET -> {
                val target = activeExId ?: state.exercises.firstOrNull { (planned, _) ->
                    (state.loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
                }?.first?.exerciseId
                target?.let { sessionManager.startExercise(it) }
            }
            WorkoutNotificationActions.ACTION_COMPLETE_SET -> {
                activeExId?.let { exId ->
                    sessionManager.completeSet(
                        exerciseId = exId,
                        weightKg = state.quickWeight,
                        reps = state.quickReps,
                    )
                }
            }
            WorkoutNotificationActions.ACTION_SKIP_REST -> {
                restingExId?.let { sessionManager.skipRest(it) }
            }
            WorkoutNotificationActions.ACTION_PAUSE_WORKOUT -> sessionManager.pauseWorkout()
            WorkoutNotificationActions.ACTION_RESUME_WORKOUT -> sessionManager.resumeWorkout()
            WorkoutNotificationActions.ACTION_FINISH_WORKOUT -> sessionManager.finishSession {}
            WorkoutNotificationActions.ACTION_NEXT_EXERCISE -> {
                val id = activeExId ?: restingExId ?: return true
                sessionManager.skipExercise(id)
            }
            WorkoutNotificationActions.ACTION_PREV_EXERCISE -> {
                val id = activeExId ?: restingExId ?: return true
                sessionManager.previousExercise(id)
            }
            WorkoutNotificationActions.ACTION_ADD_SET -> {
                val exId = activeExId ?: restingExId ?: return true
                val planned = sessionManager.plannedFor(exId) ?: return true
                sessionManager.updateTargetSets(exId, planned.targetSets + 1)
            }
            WorkoutNotificationActions.ACTION_REMOVE_SET -> {
                val exId = activeExId ?: restingExId ?: return true
                val logged = sessionManager.setsForExercise(exId)
                if (logged.isNotEmpty()) {
                    sessionManager.deleteSet(logged.last())
                } else {
                    val planned = sessionManager.plannedFor(exId) ?: return true
                    if (planned.targetSets > 1) {
                        sessionManager.updateTargetSets(exId, planned.targetSets - 1)
                    }
                }
            }
            WorkoutNotificationActions.ACTION_WEIGHT_INC -> sessionManager.adjustQuickWeight(2.5f)
            WorkoutNotificationActions.ACTION_WEIGHT_DEC -> sessionManager.adjustQuickWeight(-2.5f)
            WorkoutNotificationActions.ACTION_REPS_INC -> sessionManager.adjustQuickReps(1)
            WorkoutNotificationActions.ACTION_REPS_DEC -> sessionManager.adjustQuickReps(-1)
            WorkoutNotificationActions.ACTION_SAVE_SET -> sessionManager.saveQuickSet()
            WorkoutNotificationActions.ACTION_REST_ADD_15 -> sessionManager.adjustRestTime(15)
            WorkoutNotificationActions.ACTION_REST_ADD_30 -> sessionManager.adjustRestTime(30)
            else -> {
                WorkoutNotificationLogger.w("Unknown action: $action")
                return false
            }
        }
        return true
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 350L
    }
}
