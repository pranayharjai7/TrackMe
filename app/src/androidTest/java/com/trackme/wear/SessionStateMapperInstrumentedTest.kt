package com.trackme.wear

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.ExerciseExecutionState
import com.trackme.wear.session.toWearPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStateMapperInstrumentedTest {
    @Test
    fun mapsLoggedSessionStateForWearPayload() {
        val planned = PlannedExercise(
            id = "planned",
            dayId = "day",
            userId = "user",
            exerciseId = "exercise",
            orderIndex = 0,
            updatedAt = 1L,
            targetSets = 3,
            targetReps = 8,
            targetWeightKg = 80f,
        )
        val exercise = Exercise(
            id = "exercise",
            name = "Bench Press",
            category = "Strength",
            primaryMuscles = listOf("Chest"),
            secondaryMuscles = listOf("Triceps"),
            equipment = "Barbell",
            instructions = listOf("Lower under control."),
            gifUrl = "",
            youtubeQuery = "",
        )
        val set = SessionSet(
            id = "set",
            sessionId = "session",
            userId = "user",
            exerciseId = "exercise",
            setNumber = 1,
            weightKg = 80f,
            reps = 8,
            completed = true,
            updatedAt = 2L,
        )
        val state = ActiveSessionUiState(
            sessionId = "session",
            dayId = "day",
            sessionDateMillis = 3L,
            exercises = listOf(planned to exercise),
            loggedSets = listOf(set),
            loggedSetsByExercise = mapOf("exercise" to listOf(set)),
            executionStates = mapOf("exercise" to ExerciseExecutionState.ACTIVE_SET),
            activeExerciseId = "exercise",
        )

        val payload = state.toWearPayload(now = 10L)

        assertEquals("session", payload.sessionId)
        assertEquals("Bench Press", payload.exerciseName)
        assertEquals(1, payload.completedSets)
        assertEquals(3, payload.totalSets)
        assertEquals(640f, payload.totalVolumeKg)
        assertTrue(payload.sessionProgressPercent in 33..34)
    }
}
