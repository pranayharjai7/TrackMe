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

    @Test
    fun `WearUiState restSecondsRemaining defaults to 90`() {
        val state = WearUiState()
        assertEquals(90, state.restSecondsRemaining)
    }

    @Test
    fun `WearUiState restTotalSeconds defaults to 90`() {
        val state = WearUiState()
        assertEquals(90, state.restTotalSeconds)
    }

    @Test
    fun `adjustRestTime clamps between 15 and 300 seconds`() {
        val state = WearUiState(restSecondsRemaining = 90)
        val tooLow  = state.copy(restSecondsRemaining = (state.restSecondsRemaining - 300).coerceIn(15, 300))
        val tooHigh = state.copy(restSecondsRemaining = (state.restSecondsRemaining + 300).coerceIn(15, 300))
        assertEquals(15, tooLow.restSecondsRemaining)
        assertEquals(300, tooHigh.restSecondsRemaining)
    }
}
