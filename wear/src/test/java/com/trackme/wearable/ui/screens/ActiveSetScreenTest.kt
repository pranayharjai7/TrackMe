package com.trackme.wearable.ui.screens

import com.trackme.wearable.viewmodel.LoggerField
import com.trackme.wearable.viewmodel.LoggerInputState
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSetScreenTest {

    // ---- formatSeconds -------------------------------------------------------

    @Test
    fun `formatSeconds zero returns 0 colon 00`() {
        assertEquals("0:00", formatSeconds(0))
    }

    @Test
    fun `formatSeconds 5 seconds pads to 0 colon 05`() {
        assertEquals("0:05", formatSeconds(5))
    }

    @Test
    fun `formatSeconds 90 seconds returns 1 colon 30`() {
        assertEquals("1:30", formatSeconds(90))
    }

    @Test
    fun `formatSeconds 60 seconds returns 1 colon 00`() {
        assertEquals("1:00", formatSeconds(60))
    }

    @Test
    fun `formatSeconds 125 seconds returns 2 colon 05`() {
        assertEquals("2:05", formatSeconds(125))
    }

    @Test
    fun `formatSeconds 599 seconds returns 9 colon 59`() {
        assertEquals("9:59", formatSeconds(599))
    }

    // ---- formatFieldValue ----------------------------------------------------

    @Test
    fun `formatFieldValue WEIGHT shows weight in kg`() {
        val input = LoggerInputState(weightKg = 80f)
        assertEquals("80.0 kg", formatFieldValue(LoggerField.WEIGHT, input))
    }

    @Test
    fun `formatFieldValue REPS shows plain integer`() {
        val input = LoggerInputState(reps = 12)
        assertEquals("12", formatFieldValue(LoggerField.REPS, input))
    }

    @Test
    fun `formatFieldValue DURATION shows formatted mm colon ss`() {
        val input = LoggerInputState(durationSeconds = 90)
        assertEquals("1:30", formatFieldValue(LoggerField.DURATION, input))
    }

    @Test
    fun `formatFieldValue DISTANCE shows distance in km`() {
        val input = LoggerInputState(distanceKm = 2.5f)
        assertEquals("2.5 km", formatFieldValue(LoggerField.DISTANCE, input))
    }

    @Test
    fun `formatFieldValue SPEED shows speed in km per h`() {
        val input = LoggerInputState(speedKmh = 8.5f)
        assertEquals("8.5 km/h", formatFieldValue(LoggerField.SPEED, input))
    }

    @Test
    fun `formatFieldValue INCLINE shows incline percent`() {
        val input = LoggerInputState(inclinePercent = 5.0f)
        assertEquals("5.0%", formatFieldValue(LoggerField.INCLINE, input))
    }

    @Test
    fun `formatFieldValue WEIGHT zero shows 0 dot 0 kg`() {
        val input = LoggerInputState(weightKg = 0f)
        assertEquals("0.0 kg", formatFieldValue(LoggerField.WEIGHT, input))
    }

    @Test
    fun `formatFieldValue DURATION 5 seconds pads correctly`() {
        val input = LoggerInputState(durationSeconds = 5)
        assertEquals("0:05", formatFieldValue(LoggerField.DURATION, input))
    }
}
