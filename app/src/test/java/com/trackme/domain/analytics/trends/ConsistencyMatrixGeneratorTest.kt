package com.trackme.domain.analytics.trends

import com.trackme.domain.analytics.models.MatrixQuadrant
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyMatrixGeneratorTest {

    private val generator = ConsistencyMatrixGenerator()

    @Test
    fun `returns null when no valid sessions exist in the rolling window`() {
        val now = 10_000_000_000L
        val result = generator.generate(
            history = listOf(
                session(dateMillis = now - 31.days(), durationMinutes = 45, totalVolumeKg = 10_000f),
                session(dateMillis = now - 1.days(), durationMinutes = 0, totalVolumeKg = 10_000f),
            ),
            currentTimeMillis = now,
        )

        assertNull(result)
    }

    @Test
    fun `classifies high consistency and high intensity as juggernaut`() {
        val now = 10_000_000_000L
        val sessions = (0 until 13).map {
            session(dateMillis = now - it.days(), durationMinutes = 40, totalVolumeKg = 8_000f)
        }

        val result = generator.generate(sessions, currentTimeMillis = now)!!

        assertEquals(MatrixQuadrant.JUGGERNAUT, result.quadrant)
        assertTrue(result.consistencyScore >= ConsistencyMatrixGenerator.CONSISTENCY_THRESHOLD_WORKOUTS_PER_WEEK)
        assertTrue(result.intensityScore >= ConsistencyMatrixGenerator.INTENSITY_THRESHOLD_KG_PER_MINUTE)
    }

    @Test
    fun `classifies high consistency and low intensity as builder`() {
        val now = 10_000_000_000L
        val sessions = (0 until 13).map {
            session(dateMillis = now - it.days(), durationMinutes = 60, totalVolumeKg = 3_000f)
        }

        val result = generator.generate(sessions, currentTimeMillis = now)!!

        assertEquals(MatrixQuadrant.BUILDER, result.quadrant)
    }

    @Test
    fun `classifies low consistency and high intensity as weekend warrior`() {
        val now = 10_000_000_000L
        val sessions = listOf(
            session(dateMillis = now - 1.days(), durationMinutes = 30, totalVolumeKg = 6_000f),
            session(dateMillis = now - 8.days(), durationMinutes = 30, totalVolumeKg = 6_000f),
        )

        val result = generator.generate(sessions, currentTimeMillis = now)!!

        assertEquals(MatrixQuadrant.WEEKEND_WARRIOR, result.quadrant)
    }

    @Test
    fun `classifies low consistency and low intensity as recharging`() {
        val now = 10_000_000_000L
        val sessions = listOf(
            session(dateMillis = now - 1.days(), durationMinutes = 60, totalVolumeKg = 2_000f),
            session(dateMillis = now - 8.days(), durationMinutes = 60, totalVolumeKg = 2_000f),
        )

        val result = generator.generate(sessions, currentTimeMillis = now)!!

        assertEquals(MatrixQuadrant.RECHARGING, result.quadrant)
    }

    private fun session(
        dateMillis: Long,
        durationMinutes: Int,
        totalVolumeKg: Float,
    ) = WorkoutSessionAnalyticsData(
        sessionId = "session-$dateMillis",
        dateMillis = dateMillis,
        durationMinutes = durationMinutes,
        totalVolumeKg = totalVolumeKg,
        exercises = emptyList(),
    )

    private fun Int.days(): Long = this * ConsistencyMatrixGenerator.MILLIS_PER_DAY
}
