package com.trackme.wearable.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

enum class WorkoutHapticEvent {
    SetLogged,
    RestStarted,
    RestWarning10s,
    RestComplete,
    PersonalRecord,
    LogCancelled,
    BezelNotch,
    WorkoutStarted,
    WorkoutComplete,
}

class HapticFeedbackManager(private val haptics: HapticFeedback) {
    fun play(event: WorkoutHapticEvent) {
        when (event) {
            WorkoutHapticEvent.SetLogged -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            WorkoutHapticEvent.RestStarted ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            WorkoutHapticEvent.RestWarning10s -> repeat(3) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            WorkoutHapticEvent.RestComplete -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
            WorkoutHapticEvent.PersonalRecord -> {
                repeat(4) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
            WorkoutHapticEvent.LogCancelled ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            WorkoutHapticEvent.BezelNotch ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            WorkoutHapticEvent.WorkoutStarted ->
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            WorkoutHapticEvent.WorkoutComplete -> {
                repeat(3) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }
    }
}

@Composable
fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    return remember(haptics) { HapticFeedbackManager(haptics) }
}
