package com.trackme.wearable.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryScreenTest {

    // ---- formatCalories -------------------------------------------------------

    @Test
    fun `formatCalories returns kcal string when value is present`() {
        assertEquals("250 kcal", formatCalories(250.0))
    }

    @Test
    fun `formatCalories truncates decimal to int`() {
        assertEquals("312 kcal", formatCalories(312.9))
    }

    @Test
    fun `formatCalories returns question mark when null`() {
        assertEquals("? kcal", formatCalories(null))
    }

    @Test
    fun `formatCalories returns zero kcal for zero`() {
        assertEquals("0 kcal", formatCalories(0.0))
    }

    // ---- formatDurationLabel -------------------------------------------------

    @Test
    fun `formatDurationLabel returns 0 colon 00 for zero seconds`() {
        assertEquals("0:00", formatDurationLabel(0L))
    }

    @Test
    fun `formatDurationLabel returns 1 colon 30 for 90 seconds`() {
        assertEquals("1:30", formatDurationLabel(90L))
    }

    @Test
    fun `formatDurationLabel returns 1 colon 00 colon 00 for 3600 seconds`() {
        assertEquals("1:00:00", formatDurationLabel(3600L))
    }

    @Test
    fun `formatDurationLabel returns hours colon mm colon ss for large values`() {
        assertEquals("1:01:01", formatDurationLabel(3661L))
    }

    @Test
    fun `formatDurationLabel returns minutes colon seconds below one hour`() {
        assertEquals("59:59", formatDurationLabel(3599L))
    }
}
