package com.trackme.domain.analytics.performance

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRMProjectionEngineTest {

    private val engine = OneRMProjectionEngine()

    @Test
    fun `returns null when all logs are outside the lookback window or invalid`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            set(dateMillis = now - 120.days(), weightKg = 100f, reps = 5),
            set(dateMillis = now, weightKg = 0f, reps = 5),
            set(dateMillis = now, weightKg = 100f, reps = 0),
        )

        val projection = engine.calculateProjection(
            history = history,
            exerciseId = "bench",
            exerciseName = "Bench Press",
            limitDays = 90,
        )

        assertNull(projection)
    }

    @Test
    fun `uses the best estimated one rep max per day`() {
        val now = System.currentTimeMillis()
        val sameDay = now - 1.days()
        val projection = engine.calculateProjection(
            history = listOf(
                set(dateMillis = sameDay, weightKg = 100f, reps = 5),
                set(dateMillis = sameDay + 60_000L, weightKg = 100f, reps = 10),
            ),
            exerciseId = "bench",
            exerciseName = "Bench Press",
        )

        assertNotNull(projection)
        assertEquals(1, projection!!.historyPoints.size)
        assertEquals(133.333f, projection.current1RM, 0.01f)
        assertEquals(projection.current1RM, projection.projected1RM14Days, 0.001f)
    }

    @Test
    fun `removes extreme outlier days before reporting current strength`() {
        val now = System.currentTimeMillis()
        val projection = engine.calculateProjection(
            history = listOf(
                set(dateMillis = now - 5.days(), weightKg = 100f, reps = 5),
                set(dateMillis = now - 4.days(), weightKg = 101f, reps = 5),
                set(dateMillis = now - 3.days(), weightKg = 102f, reps = 5),
                set(dateMillis = now - 2.days(), weightKg = 103f, reps = 5),
                set(dateMillis = now - 1.days(), weightKg = 300f, reps = 5),
            ),
            exerciseId = "bench",
            exerciseName = "Bench Press",
        )

        assertNotNull(projection)
        assertEquals(4, projection!!.historyPoints.size)
        assertTrue("Outlier should not become current 1RM", projection.current1RM < 130f)
    }

    @Test
    fun `never projects a lower future one rep max than the latest current value`() {
        val now = System.currentTimeMillis()
        val projection = engine.calculateProjection(
            history = listOf(
                set(dateMillis = now - 5.days(), weightKg = 130f, reps = 5),
                set(dateMillis = now - 3.days(), weightKg = 120f, reps = 5),
                set(dateMillis = now - 1.days(), weightKg = 110f, reps = 5),
            ),
            exerciseId = "bench",
            exerciseName = "Bench Press",
        )

        assertNotNull(projection)
        assertEquals(projection!!.current1RM, projection.projected1RM14Days, 0.001f)
    }

    private fun set(
        dateMillis: Long,
        weightKg: Float,
        reps: Int,
        exerciseId: String = "bench",
    ) = ExerciseAnalyticsData(
        setId = "$exerciseId-$dateMillis-$weightKg-$reps",
        exerciseId = exerciseId,
        name = "Bench Press",
        dateMillis = dateMillis,
        targetMuscles = listOf("CHEST"),
        weightKg = weightKg,
        reps = reps,
        durationSeconds = null,
    )

    private fun Int.days(): Long = this * OneRMProjectionEngine.MILLIS_PER_DAY
}
