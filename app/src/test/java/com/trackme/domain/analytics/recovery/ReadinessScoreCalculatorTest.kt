package com.trackme.domain.analytics.recovery

import com.trackme.domain.analytics.models.HealthMetricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessScoreCalculatorTest {

    private val calculator = ReadinessScoreCalculator()

    @Test
    fun `returns Establishing Baseline if less than 7 days of history`() {
        val history = List(5) { HealthMetricsData(0L, 50f, 60, 480, 100) }
        val today = HealthMetricsData(0L, 50f, 60, 480, 100)

        val result = calculator.calculate(history, today)

        assertEquals(0, result.score)
        assertTrue(result.status.contains("Establishing Baseline"))
    }

    @Test
    fun `calculates optimal score with great sleep and improved HRV`() {
        val history = List(10) { HealthMetricsData(0L, 50f, 65, 420, 100) } // Baseline HRV 50, RHR 65
        val today = HealthMetricsData(0L, 60f, 60, 500, 150) // HRV went up, RHR went down, long sleep

        val result = calculator.calculate(history, today)

        assertTrue("Score should be very high", result.score >= 85)
        assertEquals("Optimal", result.status)
        assertEquals(-0.1f, result.debug?.sleepScorePenalty ?: 0f, 0.01f)
    }

    @Test
    fun `penalizes score heavily for terrible sleep`() {
        val history = List(10) { HealthMetricsData(0L, 50f, 65, 420, 100) }
        val today = HealthMetricsData(0L, 50f, 65, 180, 50) // Only 3 hours of sleep!

        val result = calculator.calculate(history, today)

        assertTrue("Score should be penalized for 3 hours of sleep", result.score < 80)
        assertTrue((result.debug?.sleepScorePenalty ?: 0f) > 0.1f)
    }

    @Test
    fun `gracefully handles completely missing HRV and RHR data`() {
        val history = List(10) { HealthMetricsData(0L, 50f, 65, 420, 100) }
        // Today user didn't wear their watch during sleep, only manually logged sleep duration
        val today = HealthMetricsData(0L, null, null, 420, null)

        val result = calculator.calculate(history, today)

        // Should not crash, should fall back to a baseline 70-ish score
        assertTrue("Should compute a fallback score", result.score in 60..80)
        assertTrue("Should flag missing data", result.debug?.missingDataFlags?.contains("Missing HRV data") == true)
        assertTrue("Should flag missing data", result.debug?.missingDataFlags?.contains("Missing RHR data") == true)
    }

    @Test
    fun `rebalances weights when sleep is missing`() {
        val history = List(10) { HealthMetricsData(0L, 50f, 65, 420, 100) }
        // Sleep is missing today, but HRV and RHR are present and improved
        val today = HealthMetricsData(0L, 60f, 60, null, null)

        val result = calculator.calculate(history, today)

        // The improved HRV/RHR should have higher influence due to redistribution
        assertTrue("Redistribution should elevate score beyond default weight boundaries", result.score >= 85)
        assertTrue(result.debug?.missingDataFlags?.contains("Missing Sleep data") == true)
    }
}
