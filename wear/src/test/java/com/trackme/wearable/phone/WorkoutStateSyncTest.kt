package com.trackme.wearable.phone

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStateSyncTest {

    @Test
    fun `debounce interval constant is at least 5 seconds`() {
        assertTrue(WorkoutStateSync.SYNC_DEBOUNCE_MS >= 5_000L)
    }
}
