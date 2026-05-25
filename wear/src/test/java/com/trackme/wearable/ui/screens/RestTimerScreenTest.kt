package com.trackme.wearable.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerScreenTest {

    // ---- restProgress --------------------------------------------------------

    @Test
    fun `restProgress returns 1f when remaining equals total`() {
        assertEquals(1f, restProgress(remaining = 90, total = 90), 0.001f)
    }

    @Test
    fun `restProgress returns 0f when remaining is 0`() {
        assertEquals(0f, restProgress(remaining = 0, total = 90), 0.001f)
    }

    @Test
    fun `restProgress returns half when remaining is half of total`() {
        assertEquals(0.5f, restProgress(remaining = 45, total = 90), 0.001f)
    }

    @Test
    fun `restProgress returns 1f when total is 0 to avoid division by zero`() {
        assertEquals(1f, restProgress(remaining = 0, total = 0), 0.001f)
    }

    @Test
    fun `restProgress clamps to 0f when remaining is negative`() {
        assertEquals(0f, restProgress(remaining = -5, total = 90), 0.001f)
    }

    @Test
    fun `restProgress clamps to 1f when remaining exceeds total`() {
        assertEquals(1f, restProgress(remaining = 120, total = 90), 0.001f)
    }

    @Test
    fun `restProgress returns correct fraction for 10 of 60`() {
        assertEquals(10f / 60f, restProgress(remaining = 10, total = 60), 0.001f)
    }

    // ---- isWarning ----------------------------------------------------------

    @Test
    fun `isWarning returns true when seconds is exactly 10`() {
        assertTrue(isWarning(10))
    }

    @Test
    fun `isWarning returns true when seconds is exactly 1`() {
        assertTrue(isWarning(1))
    }

    @Test
    fun `isWarning returns true when seconds is between 1 and 10`() {
        assertTrue(isWarning(5))
    }

    @Test
    fun `isWarning returns false when seconds is 0`() {
        assertFalse(isWarning(0))
    }

    @Test
    fun `isWarning returns false when seconds is 11`() {
        assertFalse(isWarning(11))
    }

    @Test
    fun `isWarning returns false when seconds is 90`() {
        assertFalse(isWarning(90))
    }

    @Test
    fun `isWarning returns false for large values`() {
        assertFalse(isWarning(300))
    }
}
