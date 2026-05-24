package com.trackme.wearable

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackme.wearbridge.CommandReplayPlanner
import com.trackme.wearbridge.HealthMetricBatchPayload
import com.trackme.wearbridge.HealthMetricsPayload
import com.trackme.wearbridge.HealthMetricSamplePayload
import com.trackme.wearbridge.SetLogPayload
import com.trackme.wearbridge.SyncEventsPayload
import com.trackme.wearbridge.WatchActionPayload
import com.trackme.wearbridge.WatchActionReplayPlanner
import com.trackme.wearbridge.WatchActionType
import com.trackme.wearbridge.WatchCommandPayload
import com.trackme.wearbridge.WatchCommandType
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import com.trackme.wearbridge.WorkoutMessageType
import com.trackme.wearbridge.WorkoutStatePayload
import com.trackme.wearbridge.WorkoutSyncEventPayload
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearCommunicationInstrumentedTest {
    @Test
    fun serializesRapidSetLoggingCommandsForMessageClient() {
        val commands = (1..5).map { index ->
            WatchCommandPayload(
                commandId = "cmd-$index",
                type = WatchCommandType.LOG_SET,
                createdAt = index.toLong(),
                sessionId = "session",
                exerciseId = "exercise",
                log = SetLogPayload(weightKg = 60f, reps = index),
            )
        }

        val decoded = commands.map { WearProtocol.decodeCommand(WearProtocol.encodeCommand(it)) }

        assertEquals(commands.map { it.commandId }, decoded.map { it.commandId })
        assertEquals(5, decoded.last().log?.reps)
    }

    @Test
    fun simulatesConnectionDropAndReconnectReplayOrder() {
        val queue = (1..3).fold(emptyList<com.trackme.wearbridge.QueuedCommand>()) { acc, index ->
            CommandReplayPlanner.enqueueUnique(
                acc,
                WatchCommandPayload(
                    commandId = "queued-$index",
                    type = WatchCommandType.SKIP_REST,
                    createdAt = index.toLong(),
                    sessionId = "session",
                    exerciseId = "exercise",
                ),
                now = index.toLong(),
            )
        }

        val replay = CommandReplayPlanner.nextReplayBatch(queue)
        val remaining = CommandReplayPlanner.markDelivered(queue, replay.map { it.command.commandId }.toSet())

        assertEquals(listOf("queued-1", "queued-2", "queued-3"), replay.map { it.command.commandId })
        assertEquals(emptyList<com.trackme.wearbridge.QueuedCommand>(), remaining)
    }

    @Test
    fun healthBatchRoundTripPreservesMetricSamples() {
        val batch = HealthMetricBatchPayload(
            batchId = "batch",
            sessionId = "session",
            createdAt = 1L,
            samples = listOf(
                HealthMetricSamplePayload("HEART_RATE", 122.0, "bpm", 1L, 2L),
                HealthMetricSamplePayload("DURATION", 30.0, "s", 1L, 2L),
            ),
        )

        val decoded = WearProtocol.decodeHealthBatch(WearProtocol.encodeHealthBatch(batch))

        assertEquals(2, decoded.samples.size)
        assertEquals("HEART_RATE", decoded.samples.first().metricType)
    }

    @Test
    fun newWatchActionMessageRoundTripMatchesPhonePath() {
        val action = WatchActionPayload(
            actionId = "action-1",
            actionType = WatchActionType.COMPLETE_SET,
            timestamp = 100L,
            sessionId = "session",
            exerciseId = "exercise",
            setNumber = 2,
            eventIndex = 4L,
            log = SetLogPayload(weightKg = 72.5f, reps = 8),
        )

        val decoded = WearProtocol.decodeWatchAction(WearProtocol.encodeWatchAction(action))

        assertEquals(WearPaths.WATCH_ACTION, "/watch/action")
        assertEquals(action, decoded)
    }

    @Test
    fun healthMetricsBatchMessagePreservesLatestStreamingValues() {
        val metrics = HealthMetricsPayload(
            metricId = "metric-1",
            sessionId = "session",
            timestamp = 200L,
            heartRate = 128.0,
            steps = 64.0,
            calories = 9.5,
            activeDurationSeconds = 30L,
        )

        val decoded = WearProtocol.decodeHealthMetrics(WearProtocol.encodeHealthMetrics(metrics))

        assertEquals(WearPaths.HEALTH_METRICS, "/health/metrics")
        assertEquals(128.0, decoded.heartRate)
        assertEquals(30L, decoded.activeDurationSeconds)
    }

    @Test
    fun syncEventsCarryMissingWorkoutStateAfterReconnect() {
        val state = WorkoutStatePayload(
            messageType = WorkoutMessageType.COMPLETE_SET,
            sessionId = "session",
            exerciseId = "exercise",
            exerciseName = "Bench Press",
            setNumber = 2,
            totalSets = 4,
            weight = 80f,
            reps = 8,
            lastEventIndex = 9L,
            timestamp = 300L,
        )
        val payload = SyncEventsPayload(
            sessionId = "session",
            fromExclusiveEventIndex = 8L,
            events = listOf(
                WorkoutSyncEventPayload(
                    eventIndex = 9L,
                    path = WearPaths.WORKOUT_COMPLETE_SET,
                    timestamp = 300L,
                    workoutState = state,
                )
            ),
            timestamp = 301L,
        )

        val decoded = WearProtocol.decodeSyncEvents(WearProtocol.encodeSyncEvents(payload))

        assertEquals(1, decoded.events.size)
        assertEquals(WearPaths.WORKOUT_COMPLETE_SET, decoded.events.single().path)
        assertEquals(WorkoutMessageType.COMPLETE_SET, decoded.events.single().workoutState?.messageType)
    }

    @Test
    fun newOfflineActionReplayFlushesSequentiallyAfterReconnect() {
        val queued = listOf(
            WatchActionPayload(
                actionId = "later",
                actionType = WatchActionType.SKIP_REST,
                timestamp = 20L,
                sessionId = "session",
                eventIndex = 2L,
            ),
            WatchActionPayload(
                actionId = "earlier",
                actionType = WatchActionType.START_SET,
                timestamp = 10L,
                sessionId = "session",
                eventIndex = 1L,
            ),
        )

        val replay = WatchActionReplayPlanner.nextReplayBatch(queued)
        val remaining = WatchActionReplayPlanner.markDelivered(queued, replay.map { it.actionId }.toSet())

        assertEquals(listOf("earlier", "later"), replay.map { it.actionId })
        assertEquals(emptyList<WatchActionPayload>(), remaining)
    }
}
