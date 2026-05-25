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
        val state = WearUiState(workoutScreenState = WorkoutScreenState.RESTING, restSecondsRemaining = 45)
        val result = state.copy(workoutScreenState = WorkoutScreenState.ACTIVE_SET)
        assertEquals(WorkoutScreenState.ACTIVE_SET, result.workoutScreenState)
    }

    @Test
    fun `adjustRestTime positive delta increases time`() {
        val state = WearUiState(restSecondsRemaining = 60)
        val result = state.copy(restSecondsRemaining = (state.restSecondsRemaining + 15).coerceIn(15, 300))
        assertEquals(75, result.restSecondsRemaining)
    }

    @Test
    fun `adjustRestTime negative delta decreases time`() {
        val state = WearUiState(restSecondsRemaining = 60)
        val result = state.copy(restSecondsRemaining = (state.restSecondsRemaining - 15).coerceIn(15, 300))
        assertEquals(45, result.restSecondsRemaining)
    }
}
