package com.trackme.wearable.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseSummaryScreenTest {

    // ---- formatVolume --------------------------------------------------------

    @Test
    fun `formatVolume returns null when volume is zero`() {
        assertNull(formatVolume(0f))
    }

    @Test
    fun `formatVolume returns null when volume is negative`() {
        assertNull(formatVolume(-5f))
    }

    @Test
    fun `formatVolume returns formatted string when volume is positive`() {
        assertEquals("100 kg total", formatVolume(100f))
    }

    @Test
    fun `formatVolume truncates decimal to int`() {
        assertEquals("75 kg total", formatVolume(75.9f))
    }

    // ---- formatPrBadge -------------------------------------------------------

    @Test
    fun `formatPrBadge returns positive delta with kg PR suffix`() {
        assertEquals("+10 kg PR", formatPrBadge(10f))
    }

    @Test
    fun `formatPrBadge truncates decimal to int`() {
        assertEquals("+5 kg PR", formatPrBadge(5.7f))
    }
}
