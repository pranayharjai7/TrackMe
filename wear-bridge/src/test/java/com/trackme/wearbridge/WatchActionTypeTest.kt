package com.trackme.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchActionTypeTest {

    @Test
    fun switchExercise_roundTrips() {
        val action = WatchActionPayload(
            actionId = "id-1",
            actionType = WatchActionType.SWITCH_EXERCISE,
            timestamp = 1L,
            sessionId = "session",
            exerciseId = "exercise-a",
        )
        val json = WearProtocol.encodeWatchAction(action)
        val decoded = WearProtocol.decodeWatchAction(json)
        assertEquals(WatchActionType.SWITCH_EXERCISE, decoded.actionType)
        assertEquals("exercise-a", decoded.exerciseId)
    }
}
