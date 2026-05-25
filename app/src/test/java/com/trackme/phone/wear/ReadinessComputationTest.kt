package com.trackme.phone.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessComputationTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `returns 95 when no completed workout`() {
        assertEquals(95, computeReadinessScore(null, now))
    }

    @Test
    fun `returns 35 immediately after workout`() {
        assertEquals(35, computeReadinessScore(now, now))
    }

    @Test
    fun `returns 65 after 24 hours`() {
        val lastCompleted = now - 24 * 3_600_000L
        assertEquals(65, computeReadinessScore(lastCompleted, now))
    }

    @Test
    fun `returns 95 after 48 hours or more`() {
        val lastCompleted = now - 48 * 3_600_000L
        assertEquals(95, computeReadinessScore(lastCompleted, now))
        assertEquals(95, computeReadinessScore(now - 120 * 3_600_000L, now))
    }
}
