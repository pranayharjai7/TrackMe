package com.trackme.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSessionReducerTest {
    @Test
    fun reducerAppliesSessionConnectivityAndQueueEvents() {
        val payload = SessionStatePayload(
            sessionId = "session",
            exerciseIndex = 1,
            setIndex = 2,
            completedSets = 4,
            totalSets = 8,
            restRemaining = 0,
            exerciseName = "Squat",
            muscle = "Legs",
            equipment = "Barbell",
            loggingType = LoggingTypePayload.WEIGHTED_REPS,
            sessionProgressPercent = 50,
            updatedAt = 99L,
        )

        val state = WatchSessionReducer.reduce(WatchReducerState(), WatchReducerEvent.SessionUpdated(payload))
            .let { WatchSessionReducer.reduce(it, WatchReducerEvent.ConnectivityChanged(offline = true)) }
            .let { WatchSessionReducer.reduce(it, WatchReducerEvent.QueueChanged(queuedCount = 3)) }
            .let { WatchSessionReducer.reduce(it, WatchReducerEvent.CommandAccepted("cmd-1")) }

        assertEquals(payload, state.session)
        assertEquals(true, state.offline)
        assertEquals(3, state.queuedCount)
        assertEquals("cmd-1", state.lastAppliedCommandId)
    }
}
