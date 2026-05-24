package com.trackme.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolTest {
    @Test
    fun sessionPayloadRoundTripsThroughJson() {
        val payload = SessionStatePayload(
            sessionId = "session-1",
            dayId = "day-1",
            sessionDateMillis = 42L,
            exerciseIndex = 0,
            setIndex = 2,
            completedSets = 1,
            totalSets = 3,
            restRemaining = 45,
            exerciseName = "Bench Press",
            nextExerciseName = "Row",
            muscle = "Chest",
            equipment = "Barbell",
            loggingType = LoggingTypePayload.WEIGHTED_REPS,
            targetReps = 8,
            targetWeight = 80f,
            sessionProgressPercent = 33,
            totalVolumeKg = 640f,
            restActive = true,
            updatedAt = 100L,
        )

        val decoded = WearProtocol.decodeSessionState(WearProtocol.encodeSessionState(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun queuedCommandsSurviveMalformedJsonAsEmptyQueue() {
        assertTrue(WearProtocol.decodeQueuedCommands("{bad-json").isEmpty())
    }

    @Test
    fun workoutStateWatchActionSyncAndMetricsRoundTripThroughJson() {
        val session = SessionStatePayload(
            sessionId = "session-2",
            exerciseIndex = 0,
            setIndex = 1,
            completedSets = 0,
            totalSets = 4,
            restRemaining = 0,
            exerciseName = "Deadlift",
            muscle = "Back",
            equipment = "Barbell",
            loggingType = LoggingTypePayload.WEIGHTED_REPS,
            targetReps = 5,
            targetWeight = 120f,
            sessionProgressPercent = 25,
            updatedAt = 200L,
        )
        val workout = WorkoutStatePayload(
            messageType = WorkoutMessageType.START,
            sessionId = session.sessionId,
            exerciseId = "exercise-1",
            exerciseName = session.exerciseName,
            setNumber = 1,
            totalSets = 4,
            weight = 120f,
            reps = 5,
            lastEventIndex = 7L,
            timestamp = 201L,
            sessionState = session,
        )
        val action = WatchActionPayload(
            actionId = "action-1",
            actionType = WatchActionType.COMPLETE_SET,
            timestamp = 202L,
            sessionId = session.sessionId,
            exerciseId = "exercise-1",
            setNumber = 1,
            eventIndex = 1L,
            log = SetLogPayload(weightKg = 120f, reps = 5),
        )
        val metrics = HealthMetricsPayload(
            metricId = "metric-1",
            sessionId = session.sessionId,
            timestamp = 203L,
            heartRate = 132.0,
            steps = 42.0,
            calories = 8.4,
            activeDurationSeconds = 30L,
        )
        val syncEvents = SyncEventsPayload(
            sessionId = session.sessionId,
            fromExclusiveEventIndex = 6L,
            events = listOf(
                WorkoutSyncEventPayload(
                    eventIndex = 7L,
                    path = WearPaths.WORKOUT_START,
                    timestamp = workout.timestamp,
                    workoutState = workout,
                ),
                WorkoutSyncEventPayload(
                    eventIndex = 8L,
                    path = WearPaths.WATCH_ACTION,
                    timestamp = action.timestamp,
                    action = action,
                ),
            ),
            timestamp = 204L,
        )

        assertEquals(workout, WearProtocol.decodeWorkoutState(WearProtocol.encodeWorkoutState(workout)))
        assertEquals(action, WearProtocol.decodeWatchAction(WearProtocol.encodeWatchAction(action)))
        assertEquals(metrics, WearProtocol.decodeHealthMetrics(WearProtocol.encodeHealthMetrics(metrics)))
        assertEquals(syncEvents, WearProtocol.decodeSyncEvents(WearProtocol.encodeSyncEvents(syncEvents)))
    }

    @Test
    fun durableActionAndHealthQueuesSurviveMalformedJsonAsEmptyQueues() {
        assertTrue(WearProtocol.decodeWatchActions("{bad-json").isEmpty())
        assertTrue(WearProtocol.decodeHealthSamples("{bad-json").isEmpty())
    }
}
