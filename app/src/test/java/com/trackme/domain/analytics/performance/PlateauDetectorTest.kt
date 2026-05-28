package com.trackme.domain.analytics.performance

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateauDetectorTest {

    private val detector = PlateauDetector()

    @Test
    fun `does not flag plateau with fewer than three distinct training days`() {
        val now = 1_000_000_000L
        val alerts = detector.detectPlateaus(
            history = listOf(
                set(dateMillis = now - 1.days()),
                set(dateMillis = now - 1.days() + 60_000L),
                set(dateMillis = now - 8.days()),
            ),
            currentTimeMillis = now,
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `flags weight and volume plateau when weekly work is effectively flat`() {
        val now = 1_000_000_000L
        val alerts = detector.detectPlateaus(
            history = listOf(
                set(dateMillis = now - 1.days(), weightKg = 100f, reps = 10),
                set(dateMillis = now - 8.days(), weightKg = 100.5f, reps = 10),
                set(dateMillis = now - 15.days(), weightKg = 99.5f, reps = 10),
            ),
            currentTimeMillis = now,
        )

        assertEquals(1, alerts.size)
        assertEquals("bench", alerts.single().exerciseId)
        assertTrue(alerts.single().recommendation.contains("Weight and volume"))
    }

    @Test
    fun `flags max weight plateau separately when volume is still changing`() {
        val now = 1_000_000_000L
        val alerts = detector.detectPlateaus(
            history = listOf(
                set(dateMillis = now - 1.days(), weightKg = 100f, reps = 8),
                set(dateMillis = now - 8.days(), weightKg = 100.5f, reps = 10),
                set(dateMillis = now - 15.days(), weightKg = 99.5f, reps = 14),
            ),
            currentTimeMillis = now,
        )

        assertEquals(1, alerts.size)
        assertTrue(alerts.single().recommendation.contains("Max weight"))
    }

    @Test
    fun `ignores older history outside the plateau window`() {
        val now = 1_000_000_000L
        val alerts = detector.detectPlateaus(
            history = listOf(
                set(dateMillis = now - 50.days(), weightKg = 100f, reps = 10),
                set(dateMillis = now - 43.days(), weightKg = 100f, reps = 10),
                set(dateMillis = now - 36.days(), weightKg = 100f, reps = 10),
                set(dateMillis = now - 1.days(), weightKg = 120f, reps = 10),
            ),
            currentTimeMillis = now,
        )

        assertTrue(alerts.isEmpty())
    }

    private fun set(
        dateMillis: Long,
        weightKg: Float = 100f,
        reps: Int = 10,
    ) = ExerciseAnalyticsData(
        setId = "set-$dateMillis-$weightKg-$reps",
        exerciseId = "bench",
        name = "Bench Press",
        dateMillis = dateMillis,
        targetMuscles = listOf("CHEST"),
        weightKg = weightKg,
        reps = reps,
        durationSeconds = null,
    )

    private fun Int.days(): Long = this * 24 * 60 * 60 * 1000L
}
