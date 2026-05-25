package com.trackme.wearable.ui.screens

import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionExercisePayload
import com.trackme.wearbridge.SessionStatePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHubScreenTest {

    private fun makeExercise(targetSets: Int, isActive: Boolean = false) = SessionExercisePayload(
        exerciseId = "ex1",
        plannedExerciseId = "pex1",
        orderIndex = 0,
        exerciseName = "Bench Press",
        muscle = "Chest",
        equipment = "Barbell",
        instructionSummary = "",
        loggingType = LoggingTypePayload.WEIGHTED_REPS,
        targetSets = targetSets,
        completedSets = 0,
        isActive = isActive,
        isResting = false,
        isCompleted = false,
    )

    private fun makeSession(
        exerciseIndex: Int,
        totalSets: Int,
        exercises: List<SessionExercisePayload>,
    ) = SessionStatePayload(
        sessionId = "s1",
        exerciseIndex = exerciseIndex,
        setIndex = 1,
        completedSets = 0,
        totalSets = totalSets,
        restRemaining = 0,
        exerciseName = "Bench Press",
        muscle = "Chest",
        equipment = "Barbell",
        loggingType = LoggingTypePayload.WEIGHTED_REPS,
        sessionProgressPercent = 0,
        updatedAt = 0L,
        exercises = exercises,
    )

    @Test
    fun `currentTargetSets returns targetSets of exerciseIndex-th exercise`() {
        val session = makeSession(
            exerciseIndex = 0,
            totalSets = 10,
            exercises = listOf(makeExercise(targetSets = 4)),
        )
        assertEquals(4, currentTargetSets(session))
    }

    @Test
    fun `currentTargetSets returns totalSets when exercises list is empty`() {
        val session = makeSession(
            exerciseIndex = 0,
            totalSets = 12,
            exercises = emptyList(),
        )
        assertEquals(12, currentTargetSets(session))
    }

    @Test
    fun `currentTargetSets returns totalSets when exerciseIndex is out of bounds`() {
        val session = makeSession(
            exerciseIndex = 5,
            totalSets = 8,
            exercises = listOf(makeExercise(targetSets = 3)),
        )
        assertEquals(8, currentTargetSets(session))
    }

    @Test
    fun `currentTargetSets returns correct exercise when multiple exercises present`() {
        val session = makeSession(
            exerciseIndex = 1,
            totalSets = 10,
            exercises = listOf(
                makeExercise(targetSets = 3),
                makeExercise(targetSets = 5),
            ),
        )
        assertEquals(5, currentTargetSets(session))
    }
}
