package com.trackme.ui.workout.session.notification

import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.ExerciseExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutNotificationStateMapperTest {

    private val exerciseId = "ex-1"
    private val exercise = Exercise(
        id = exerciseId,
        name = "Bench Press",
        category = "strength",
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = "barbell",
        instructions = emptyList(),
        gifUrl = "",
        youtubeQuery = "",
    )
    private val planned = PlannedExercise(
        id = "pe-1",
        dayId = "day-1",
        userId = "user-1",
        exerciseId = exerciseId,
        orderIndex = 0,
        updatedAt = 0L,
        targetSets = 3,
        targetReps = 10,
        targetWeightKg = 60f,
    )

    @Test
    fun `maps resting state`() {
        val state = ActiveSessionUiState(
            sessionId = "session-1",
            exercises = listOf(planned to exercise),
            restingExerciseId = exerciseId,
            restSecondsRemaining = 45,
            restSeconds = 90,
            sessionStartTimeMillis = 1_000L,
        )

        val model = WorkoutNotificationStateMapper.map(state, nowMillis = 61_000L)

        assertEquals(WorkoutNotificationState.RESTING, model.notificationState)
        assertEquals(45, model.restSecondsRemaining)
        assertFalse(model.isRestUrgent)
    }

    @Test
    fun `maps urgent rest when under ten seconds`() {
        val state = ActiveSessionUiState(
            sessionId = "session-1",
            exercises = listOf(planned to exercise),
            restingExerciseId = exerciseId,
            restSecondsRemaining = 8,
        )

        val model = WorkoutNotificationStateMapper.map(state)

        assertTrue(model.isRestUrgent)
        assertEquals("1:08", WorkoutNotificationStateMapper.formatRestTime(68))
    }

    @Test
    fun `maps active set state`() {
        val state = ActiveSessionUiState(
            sessionId = "session-1",
            exercises = listOf(planned to exercise),
            activeExerciseId = exerciseId,
            quickWeight = 62.5f,
            quickReps = 8,
        )

        val model = WorkoutNotificationStateMapper.map(state)

        assertEquals(WorkoutNotificationState.ACTIVE_SET, model.notificationState)
        assertTrue(model.showCollapsedComplete)
        assertFalse(model.showCollapsedStart)
    }

    @Test
    fun `maps paused workout`() {
        val state = ActiveSessionUiState(
            sessionId = "session-1",
            isPaused = true,
        )

        assertEquals(
            WorkoutNotificationState.WORKOUT_PAUSED,
            WorkoutNotificationStateMapper.resolveNotificationState(state),
        )
    }

    @Test
    fun `formats elapsed time`() {
        val formatted = WorkoutNotificationStateMapper.formatElapsed(
            startMillis = 0L,
            nowMillis = 125_000L,
            isPaused = false,
        )
        assertEquals("02:05", formatted)
    }

    @Test
    fun `maps exercise completed when another exercise remains`() {
        val secondId = "ex-2"
        val secondPlanned = planned.copy(id = "pe-2", exerciseId = secondId, orderIndex = 1)
        val secondExercise = exercise.copy(id = secondId, name = "Rows")
        val state = ActiveSessionUiState(
            sessionId = "session-1",
            exercises = listOf(planned to exercise, secondPlanned to secondExercise),
            loggedSetsByExercise = mapOf(
                exerciseId to listOf(fakeSet(1), fakeSet(2), fakeSet(3)),
            ),
            executionStates = mapOf(
                exerciseId to ExerciseExecutionState.COMPLETED,
                secondId to ExerciseExecutionState.IDLE,
            ),
        )

        assertEquals(
            WorkoutNotificationState.EXERCISE_COMPLETED,
            WorkoutNotificationStateMapper.resolveNotificationState(state),
        )
    }

    private fun fakeSet(n: Int) = com.trackme.domain.model.SessionSet(
        id = "set-$n",
        sessionId = "session-1",
        userId = "user-1",
        exerciseId = exerciseId,
        setNumber = n,
        weightKg = 60f,
        reps = 10,
        completed = true,
        updatedAt = 0L,
    )
}
