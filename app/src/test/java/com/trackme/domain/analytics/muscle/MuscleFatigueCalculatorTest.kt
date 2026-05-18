package com.trackme.domain.analytics.muscle

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleFatigueCalculatorTest {

    private val calculator = MuscleFatigueCalculator()

    @Test
    fun `muscle fatigue decays exponentially based on dynamic muscle group half life`() {
        val now = System.currentTimeMillis()
        val chestExercise = ExerciseAnalyticsData(
            setId = "set_1",
            exerciseId = "bench",
            name = "Bench Press",
            dateMillis = now,
            targetMuscles = listOf("CHEST"),
            weightKg = 100f,
            reps = 20, // 2000kg volume
            durationSeconds = null
        )
        
        val workout = WorkoutSessionAnalyticsData(
            sessionId = "session_1",
            dateMillis = now,
            durationMinutes = 60,
            totalVolumeKg = 2000f,
            exercises = listOf(chestExercise)
        )

        // 1. Calculate immediately (0 hours elapsed)
        val resultImmediate = calculator.calculate(listOf(workout), now)
        val fatigueImmediate = resultImmediate["CHEST"]?.fatiguePercentage ?: 0
        
        // 2000kg chest volume / 5000kg max baseline = 40% fatigue
        assertEquals(40, fatigueImmediate)

        // 2. Calculate exactly 15 hours later (1 half-life elapsed for 60h clear chest)
        // Fatigue should drop by half (40% * 0.5 = 20%)
        val result1HalfLife = calculator.calculate(listOf(workout), now + (15 * 60 * 60 * 1000L))
        val fatigue1HL = result1HalfLife["CHEST"]?.fatiguePercentage ?: 0
        assertEquals(20, fatigue1HL)

        // 3. Calculate exactly 30 hours later (2 half-lives elapsed)
        // Fatigue should drop to a quarter (40% * 0.25 = 10%)
        val result2HalfLifes = calculator.calculate(listOf(workout), now + (30 * 60 * 60 * 1000L))
        val fatigue2HL = result2HalfLifes["CHEST"]?.fatiguePercentage ?: 0
        assertEquals(10, fatigue2HL)
    }
}
