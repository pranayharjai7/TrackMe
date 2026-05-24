package com.trackme.ui.workout.session.notification

/**
 * High-level notification presentation states for the live workout experience.
 */
enum class WorkoutNotificationState {
    SESSION_IDLE,
    ACTIVE_SET,
    RESTING,
    EXERCISE_COMPLETED,
    WORKOUT_PAUSED,
    WORKOUT_COMPLETED,
}
