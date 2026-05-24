package com.trackme.ui.workout.session.notification

/**
 * Decides which notification UI blocks are visible so layouts stay short and never clip.
 */
data class NotificationDisplayPlan(
    val collapsedShowProgress: Boolean,
    val collapsedShowAction: Boolean,
    val collapsedInlineRestTimer: Boolean,
    val expandedShowProgress: Boolean,
    val expandedShowRestBlock: Boolean,
    val expandedShowQuickStepper: Boolean,
    val expandedShowContextLine: Boolean,
    val expandedShowPrimaryCta: Boolean,
    val systemActionPause: Boolean,
    val systemActionEnd: Boolean,
    val systemActionRestPlus15: Boolean,
) {
    companion object {
        fun from(state: WorkoutNotificationState): NotificationDisplayPlan = when (state) {
            WorkoutNotificationState.RESTING -> NotificationDisplayPlan(
                collapsedShowProgress = true,
                collapsedShowAction = true,
                collapsedInlineRestTimer = true,
                expandedShowProgress = false,
                expandedShowRestBlock = true,
                expandedShowQuickStepper = false,
                expandedShowContextLine = true,
                expandedShowPrimaryCta = true,
                systemActionPause = true,
                systemActionEnd = true,
                systemActionRestPlus15 = true,
            )
            WorkoutNotificationState.ACTIVE_SET -> NotificationDisplayPlan(
                collapsedShowProgress = true,
                collapsedShowAction = true,
                collapsedInlineRestTimer = false,
                expandedShowProgress = true,
                expandedShowRestBlock = false,
                expandedShowQuickStepper = true,
                expandedShowContextLine = true,
                expandedShowPrimaryCta = true,
                systemActionPause = true,
                systemActionEnd = true,
                systemActionRestPlus15 = false,
            )
            WorkoutNotificationState.WORKOUT_PAUSED -> NotificationDisplayPlan(
                collapsedShowProgress = false,
                collapsedShowAction = true,
                collapsedInlineRestTimer = false,
                expandedShowProgress = false,
                expandedShowRestBlock = false,
                expandedShowQuickStepper = false,
                expandedShowContextLine = true,
                expandedShowPrimaryCta = true,
                systemActionPause = false,
                systemActionEnd = true,
                systemActionRestPlus15 = false,
            )
            WorkoutNotificationState.SESSION_IDLE,
            WorkoutNotificationState.EXERCISE_COMPLETED,
            -> NotificationDisplayPlan(
                collapsedShowProgress = true,
                collapsedShowAction = true,
                collapsedInlineRestTimer = false,
                expandedShowProgress = true,
                expandedShowRestBlock = false,
                expandedShowQuickStepper = false,
                expandedShowContextLine = true,
                expandedShowPrimaryCta = true,
                systemActionPause = true,
                systemActionEnd = true,
                systemActionRestPlus15 = false,
            )
            WorkoutNotificationState.WORKOUT_COMPLETED -> NotificationDisplayPlan(
                collapsedShowProgress = false,
                collapsedShowAction = false,
                collapsedInlineRestTimer = false,
                expandedShowProgress = false,
                expandedShowRestBlock = false,
                expandedShowQuickStepper = false,
                expandedShowContextLine = true,
                expandedShowPrimaryCta = false,
                systemActionPause = false,
                systemActionEnd = false,
                systemActionRestPlus15 = false,
            )
        }
    }
}
