package com.trackme.wearable.phone

import com.trackme.wearbridge.DayPayload
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStateSyncTest {

    @Test
    fun `debounce interval constant is at least 5 seconds`() {
        assertTrue(WorkoutStateSync.SYNC_DEBOUNCE_MS >= 5_000L)
    }

    @Test
    fun `day state uses shared DataClient path and payload codec`() {
        assertEquals("/trackme/day_state", WearPaths.DATA_DAY_STATE)
        val payload = DayPayload(
            workoutName = "Push Day",
            exerciseCount = 6,
            readinessScore = 82,
            updatedAt = 99L,
        )
        val decoded = WearProtocol.decodeDayState(WearProtocol.encodeDayState(payload))
        assertEquals(payload, decoded)
    }
}
