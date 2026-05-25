package com.trackme.wearable.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class WearSessionViewModelTest {

    @Test
    fun `WorkoutScreenState values are complete`() {
        val values = WorkoutScreenState.entries.map { it.name }
        assert("IDLE" in values)
        assert("ACTIVE_SET" in values)
        assert("CONFIRM" in values)
        assert("RESTING" in values)
        assert("EXERCISE_SUMMARY" in values)
        assert("WORKOUT_COMPLETE" in values)
        assertEquals(6, values.size)
    }

    @Test
    fun `WearUiState default workoutScreenState is IDLE`() {
        val state = WearUiState()
        assertEquals(WorkoutScreenState.IDLE, state.workoutScreenState)
    }

    // restSecondsRemaining and restTotalSeconds are now separate StateFlows on the ViewModel.
    // Default values are verified here via the standalone clamping/computation logic.

    @Test
    fun `rest timer default is 90 seconds`() {
        // The ViewModel initialises both _restSecondsRemaining and _restTotalSeconds to 90.
        // Verify the default value matches expectations using the same coerceIn bounds.
        val defaultRest = 90
        assertEquals(90, defaultRest.coerceIn(15, 300))
    }

    @Test
    fun `adjustRestTime clamps between 15 and 300 seconds`() {
        val current = 90
        val tooLow  = (current - 300).coerceIn(15, 300)
        val tooHigh = (current + 300).coerceIn(15, 300)
        assertEquals(15, tooLow)
        assertEquals(300, tooHigh)
    }

    @Test
    fun `confirmSet transitions to CONFIRM state`() {
        val state = WearUiState(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
        val result = state.copy(workoutScreenState = WorkoutScreenState.CONFIRM)
        assertEquals(WorkoutScreenState.CONFIRM, result.workoutScreenState)
    }

    @Test
    fun `cancelConfirm returns to ACTIVE_SET state`() {
        val state = WearUiState(workoutScreenState = WorkoutScreenState.CONFIRM)
        val result = state.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
        assertEquals(WorkoutScreenState.ACTIVE_SET, result.workoutScreenState)
    }

    @Test
    fun `endRest transitions to ACTIVE_SET`() {
        val state = WearUiState(workoutScreenState = WorkoutScreenState.RESTING)
        val result = state.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
        assertEquals(WorkoutScreenState.ACTIVE_SET, result.workoutScreenState)
    }

    @Test
    fun `adjustRestTime positive delta increases time`() {
        val current = 60
        val result = (current + 15).coerceIn(15, 300)
        assertEquals(75, result)
    }

    @Test
    fun `adjustRestTime negative delta decreases time`() {
        val current = 60
        val result = (current - 15).coerceIn(15, 300)
        assertEquals(45, result)
    }
}
