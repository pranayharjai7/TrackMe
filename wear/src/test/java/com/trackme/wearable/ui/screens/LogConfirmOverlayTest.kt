package com.trackme.wearable.ui.screens

import com.trackme.wearable.viewmodel.LoggerInputState
import com.trackme.wearbridge.LoggingTypePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class LogConfirmOverlayTest {

    private val weightedInput = LoggerInputState(
        weightKg = 80.0f,
        reps = 12,
        durationSeconds = 90,
        distanceKm = 2.50f,
    )

    @Test
    fun `formatLogSummary WEIGHTED_REPS shows weight x reps`() {
        val result = formatLogSummary(LoggingTypePayload.WEIGHTED_REPS, weightedInput)
        assertEquals("80.0 kg × 12 reps", result)
    }

    @Test
    fun `formatLogSummary BODYWEIGHT_REPS shows reps only`() {
        val result = formatLogSummary(LoggingTypePayload.BODYWEIGHT_REPS, weightedInput)
        assertEquals("12 reps", result)
    }

    @Test
    fun `formatLogSummary TIMED shows formatted duration`() {
        val result = formatLogSummary(LoggingTypePayload.TIMED, weightedInput)
        assertEquals("1:30", result)
    }

    @Test
    fun `formatLogSummary CARDIO shows duration and distance`() {
        val result = formatLogSummary(LoggingTypePayload.CARDIO, weightedInput)
        assertEquals("1:30 · 2.50 km", result)
    }

    @Test
    fun `formatLogSummary null loggingType returns empty string`() {
        val result = formatLogSummary(null, weightedInput)
        assertEquals("", result)
    }

    @Test
    fun `formatLogSummary WEIGHTED_REPS with fractional weight formats to 1 decimal`() {
        val input = weightedInput.copy(weightKg = 102.5f, reps = 5)
        val result = formatLogSummary(LoggingTypePayload.WEIGHTED_REPS, input)
        assertEquals("102.5 kg × 5 reps", result)
    }
}
