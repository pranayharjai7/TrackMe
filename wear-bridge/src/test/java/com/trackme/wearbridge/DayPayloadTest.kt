package com.trackme.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class DayPayloadTest {

    @Test
    fun `DayPayload roundtrips through encode and decode`() {
        val payload = DayPayload(
            workoutName = "Leg Day",
            exerciseCount = 5,
            readinessScore = 75,
            recentWorkouts = listOf(
                RecentWorkoutPayload(dayName = "Push Day", completedAt = 1_000_000L, durationMinutes = 45)
            ),
            updatedAt = 12345L,
        )
        val encoded = WearProtocol.encodeDayState(payload)
        val decoded = WearProtocol.decodeDayState(encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `DayPayload defaults to empty recentWorkouts`() {
        val payload = DayPayload(
            workoutName = "Rest Day",
            exerciseCount = 0,
            readinessScore = 95,
            updatedAt = 0L,
        )
        val decoded = WearProtocol.decodeDayState(WearProtocol.encodeDayState(payload))
        assertEquals(emptyList<RecentWorkoutPayload>(), decoded.recentWorkouts)
    }
}
