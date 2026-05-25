package com.trackme.wearable.workout

import com.trackme.wearable.interaction.BezelInputController
import com.trackme.wearable.interaction.HapticFeedbackManager
import com.trackme.wearable.interaction.WorkoutHapticEvent
import com.trackme.wearable.viewmodel.LoggerField
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.SessionStatePayload

/**
 * Layer 3 — routes bezel, tap, and hardware events to ViewModel + haptics.
 */
class WorkoutInteractionEngine(
    private val viewModel: WearSessionViewModel,
    private val haptics: HapticFeedbackManager,
    val bezel: BezelInputController = BezelInputController(),
) {
    fun onActiveSetTap(state: WearUiState, derived: DerivedWorkoutUi) {
        if (derived.screenPhase != WorkoutScreenPhase.SetReady) return
        if (derived.horizontalPage != HorizontalPage.ActiveSet) return
        viewModel.openLogConfirm()
    }

    fun onActiveSetDoubleTap(state: WearUiState) {
        viewModel.toggleActiveField()
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onConfirmLog() {
        viewModel.confirmLog()
        haptics.play(WorkoutHapticEvent.SetLogged)
        haptics.play(WorkoutHapticEvent.RestStarted)
    }

    fun onCancelLog() {
        viewModel.cancelLogConfirm()
        haptics.play(WorkoutHapticEvent.LogCancelled)
    }

    fun onEndRestEarly() {
        viewModel.skipRest()
        viewModel.clearRestAdjustment()
        haptics.play(WorkoutHapticEvent.RestComplete)
    }

    fun onStartWorkout() {
        viewModel.engageWorkout()
        haptics.play(WorkoutHapticEvent.WorkoutStarted)
    }

    fun onOpenHub() {
        viewModel.navigateToHub()
    }

    fun onDismissWorkoutSummary() {
        viewModel.dismissToWatchFace()
        haptics.play(WorkoutHapticEvent.WorkoutComplete)
    }

    fun onAdvanceExerciseSummary() {
        viewModel.dismissExerciseSummary()
    }

    fun onBezelActiveSet(state: WearUiState, direction: Int, steps: Int) {
        viewModel.adjustActiveField(direction * steps)
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onBezelRest(state: WearUiState, direction: Int, steps: Int) {
        viewModel.adjustRestSeconds(direction * steps * 15)
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onBezelHub(direction: Int) {
        viewModel.cycleHubWorkout(direction)
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onBezelSummary(direction: Int) {
        viewModel.scrollWorkoutSummary(direction)
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onBezelMuscle(direction: Int) {
        viewModel.scrollMuscleGroups(direction)
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onBezelMedia(direction: Int) {
        viewModel.adjustMediaVolume(direction)
        haptics.play(WorkoutHapticEvent.BezelNotch)
    }

    fun onHardwareButtonTap() {
        viewModel.jumpToActiveSetPage()
    }

    fun onHardwareButtonLongPress() {
        viewModel.requestUndoLastSet()
    }

    fun handleRotaryScroll(
        state: WearUiState,
        derived: DerivedWorkoutUi,
        deltaPx: Float,
    ): Boolean = when (derived.appPhase) {
        WorkoutAppPhase.WatchFace -> false
        WorkoutAppPhase.WorkoutHub -> bezel.onScroll(deltaPx) { dir, _ ->
            onBezelHub(dir)
        }
        WorkoutAppPhase.WorkoutActive -> when (derived.screenPhase) {
            WorkoutScreenPhase.SetReady -> when (derived.horizontalPage) {
                HorizontalPage.Media -> bezel.onScroll(deltaPx) { dir, steps -> onBezelMedia(dir * steps) }
                HorizontalPage.MuscleMap -> bezel.onScroll(deltaPx) { dir, _ -> onBezelMuscle(dir) }
                HorizontalPage.HrZones -> false
                HorizontalPage.ActiveSet -> bezel.onScroll(deltaPx) { dir, steps ->
                    onBezelActiveSet(state, dir, steps)
                }
            }
            WorkoutScreenPhase.RestTimer -> bezel.onScroll(deltaPx) { dir, steps ->
                onBezelRest(state, dir, steps)
            }
            WorkoutScreenPhase.WorkoutSummary -> bezel.onScroll(deltaPx) { dir, _ ->
                onBezelSummary(dir)
            }
            else -> false
        }
    }

    fun restSecondsDisplayed(session: SessionStatePayload?, adjust: Int): Int =
        ((session?.restRemaining ?: 0) + adjust).coerceAtLeast(0)
}
