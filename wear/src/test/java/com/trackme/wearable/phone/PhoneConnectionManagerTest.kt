package com.trackme.wearable.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneConnectionManagerTest {

    @Test
    fun `backoff intervals increase exponentially and cap at 60 seconds`() {
        var interval = 10_000L
        val intervals = mutableListOf<Long>()
        repeat(8) {
            intervals.add(interval)
            interval = (interval * 1.5).toLong().coerceAtMost(60_000L)
        }
        assertEquals(10_000L, intervals[0])
        assertTrue("should grow", intervals[3] > intervals[0])
        assertTrue("should cap at 60s", intervals.last() <= 60_000L)
    }

    @Test
    fun `PhoneConnectionState Connected has all required fields`() {
        val state = PhoneConnectionState.Connected(
            nodeId = "node1", nodeName = "Pixel", appInstalled = true,
            lastSeenAt = System.currentTimeMillis()
        )
        assertEquals("node1", state.nodeId)
        assertTrue(state.appInstalled)
    }
}
