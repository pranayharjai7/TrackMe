package com.trackme.wearbridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WearPaths {
    const val LOG_TAG = "TrackMeWear"
    const val LEGACY_LOG_TAG = "TrackMeWearSync"

    const val CAPABILITY_PHONE = "trackme_phone"
    const val CAPABILITY_WATCH = "trackme_watch"

    const val MESSAGE_COMMAND = "/trackme/command"
    const val MESSAGE_HEALTH_BATCH = "/trackme/health_batch"
    const val DATA_SESSION_STATE = "/trackme/session_state"
    const val DATA_DAY_STATE = "/trackme/day_state"

    const val WORKOUT_START = "/workout/start"
    const val WORKOUT_NEXT_SET = "/workout/next_set"
    const val WORKOUT_COMPLETE_SET = "/workout/complete_set"
    const val WORKOUT_REST_START = "/workout/rest_start"
    const val WORKOUT_END = "/workout/end"
    const val WATCH_ACTION = "/watch/action"
    const val HEALTH_METRICS = "/health/metrics"
    const val SYNC_STATE = "/sync/state"
    const val SYNC_EVENTS = "/sync/events"
    const val MESSAGE_PING = "/sync/ping"

    const val KEY_PAYLOAD = "payload"
    const val KEY_UPDATED_AT = "updated_at"
}

object WearProtocol {
    const val SCHEMA_VERSION = 1

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSessionState(payload: SessionStatePayload): String = json.encodeToString(payload)
    fun decodeSessionState(payload: String): SessionStatePayload = json.decodeFromString(payload)

    fun encodeCommand(payload: WatchCommandPayload): String = json.encodeToString(payload)
    fun decodeCommand(payload: String): WatchCommandPayload = json.decodeFromString(payload)

    fun encodeHealthBatch(payload: HealthMetricBatchPayload): String = json.encodeToString(payload)
    fun decodeHealthBatch(payload: String): HealthMetricBatchPayload = json.decodeFromString(payload)

    fun encodeHealthMetrics(payload: HealthMetricsPayload): String = json.encodeToString(payload)
    fun decodeHealthMetrics(payload: String): HealthMetricsPayload = json.decodeFromString(payload)

    fun encodeWorkoutState(payload: WorkoutStatePayload): String = json.encodeToString(payload)
    fun decodeWorkoutState(payload: String): WorkoutStatePayload = json.decodeFromString(payload)

    fun encodeWatchAction(payload: WatchActionPayload): String = json.encodeToString(payload)
    fun decodeWatchAction(payload: String): WatchActionPayload = json.decodeFromString(payload)

    fun encodeWatchActions(payload: List<WatchActionPayload>): String = json.encodeToString(payload)

    fun decodeWatchActions(payload: String?): List<WatchActionPayload> {
        if (payload.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(payload)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    fun encodeSyncState(payload: SyncStatePayload): String = json.encodeToString(payload)
    fun decodeSyncState(payload: String): SyncStatePayload = json.decodeFromString(payload)

    fun encodeSyncEvents(payload: SyncEventsPayload): String = json.encodeToString(payload)
    fun decodeSyncEvents(payload: String): SyncEventsPayload = json.decodeFromString(payload)

    fun encodeDayState(payload: DayPayload): String = json.encodeToString(payload)
    fun decodeDayState(payload: String): DayPayload = json.decodeFromString(payload)

    fun encodeHealthSamples(payload: List<HealthMetricSamplePayload>): String = json.encodeToString(payload)

    fun decodeHealthSamples(payload: String?): List<HealthMetricSamplePayload> {
        if (payload.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(payload)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    fun encodeQueuedCommands(payload: List<QueuedCommand>): String = json.encodeToString(payload)

    fun decodeQueuedCommands(payload: String?): List<QueuedCommand> {
        if (payload.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(payload)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}

@Serializable
enum class WorkoutMessageType {
    START,
    NEXT_SET,
    COMPLETE_SET,
    REST_START,
    END,
    SNAPSHOT,
}

@Serializable
enum class WatchActionType {
    START_SET,
    COMPLETE_SET,
    SKIP_EXERCISE,
    PREVIOUS_EXERCISE,
    END_WORKOUT,
    SKIP_REST,
    REQUEST_SYNC,
    SWITCH_EXERCISE,
    PING,
}

@Serializable
enum class LoggingTypePayload {
    WEIGHTED_REPS,
    BODYWEIGHT_REPS,
    TIMED,
    CARDIO,
}

@Serializable
enum class WatchCommandType {
    START_SESSION,
    LOG_SET,
    DELETE_SET,
    EDIT_SET,
    SKIP_REST,
    SWITCH_EXERCISE,
    FINISH_WORKOUT,
    REQUEST_SNAPSHOT,
    PAUSE_WORKOUT,
    RESUME_WORKOUT,
}

@Serializable
enum class SwitchDirection {
    NEXT,
    PREVIOUS,
    INDEX,
}

@Serializable
data class SetLogPayload(
    val weightKg: Float = 0f,
    val reps: Int = 0,
    val durationSeconds: Int? = null,
    val distanceKm: Float? = null,
    val speedKmh: Float? = null,
    val inclinePercent: Float? = null,
)

@Serializable
data class WorkoutStatePayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val messageType: WorkoutMessageType,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val weight: Float = 0f,
    val reps: Int = 0,
    val restTimeRemaining: Int = 0,
    val lastEventIndex: Long = 0L,
    val timestamp: Long,
    val sessionState: SessionStatePayload? = null,
)

@Serializable
data class WatchActionPayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val actionId: String,
    val actionType: WatchActionType,
    val timestamp: Long,
    val sessionId: String,
    val exerciseId: String? = null,
    val setNumber: Int? = null,
    val eventIndex: Long = 0L,
    val log: SetLogPayload? = null,
)

@Serializable
data class HealthMetricsPayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val metricId: String,
    val sessionId: String,
    val timestamp: Long,
    val heartRate: Double? = null,
    val steps: Double? = null,
    val calories: Double? = null,
    val activeDurationSeconds: Long? = null,
)

@Serializable
data class SyncStatePayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val sessionId: String,
    val lastEventIndex: Long,
    val nodeId: String? = null,
    val nodeName: String? = null,
    val watchModel: String? = null,
    val wearOsVersion: String? = null,
    val batteryPercent: Int? = null,
    val timestamp: Long,
)

@Serializable
data class WorkoutSyncEventPayload(
    val eventIndex: Long,
    val path: String,
    val timestamp: Long,
    val workoutState: WorkoutStatePayload? = null,
    val action: WatchActionPayload? = null,
)

@Serializable
data class SyncEventsPayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val sessionId: String,
    val fromExclusiveEventIndex: Long,
    val events: List<WorkoutSyncEventPayload>,
    val timestamp: Long,
)

@Serializable
data class WatchCommandPayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val commandId: String,
    val type: WatchCommandType,
    val createdAt: Long,
    val sessionId: String = "",
    val dayId: String? = null,
    val sessionDateMillis: Long? = null,
    val exerciseId: String? = null,
    val exerciseIndex: Int? = null,
    val setId: String? = null,
    val setNumber: Int? = null,
    val switchDirection: SwitchDirection? = null,
    val log: SetLogPayload? = null,
)

@Serializable
data class SessionExercisePayload(
    val exerciseId: String,
    val plannedExerciseId: String,
    val orderIndex: Int,
    val exerciseName: String,
    val muscle: String,
    val equipment: String,
    val instructionSummary: String,
    val loggingType: LoggingTypePayload,
    val targetSets: Int,
    val targetReps: Int? = null,
    val targetWeight: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Float? = null,
    val targetSpeedKmh: Float? = null,
    val targetIncline: Float? = null,
    val completedSets: Int,
    val isActive: Boolean,
    val isResting: Boolean,
    val isCompleted: Boolean,
)

@Serializable
data class SetHistoryPayload(
    val setId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val durationSeconds: Int? = null,
    val distanceKm: Float? = null,
    val speedKmh: Float? = null,
    val inclinePercent: Float? = null,
    val completedAt: Long,
)

@Serializable
data class RecentWorkoutPayload(
    val dayName: String,
    val completedAt: Long,
    val durationMinutes: Int,
)

@Serializable
data class DayPayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val workoutName: String,
    val exerciseCount: Int,
    val readinessScore: Int,
    val recentWorkouts: List<RecentWorkoutPayload> = emptyList(),
    val updatedAt: Long,
)

@Serializable
data class SessionStatePayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val sessionId: String,
    val dayId: String? = null,
    val sessionDateMillis: Long? = null,
    val exerciseIndex: Int,
    val setIndex: Int,
    val completedSets: Int,
    val totalSets: Int,
    val restRemaining: Int,
    val exerciseName: String,
    val nextExerciseName: String? = null,
    val muscle: String,
    val equipment: String,
    val instructionSummary: String = "",
    val loggingType: LoggingTypePayload,
    val targetReps: Int? = null,
    val targetWeight: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Float? = null,
    val sessionProgressPercent: Int,
    val totalVolumeKg: Float = 0f,
    val restActive: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val updatedAt: Long,
    val exercises: List<SessionExercisePayload> = emptyList(),
    val setHistory: List<SetHistoryPayload> = emptyList(),
)

@Serializable
data class HealthMetricSamplePayload(
    val metricType: String,
    val value: Double,
    val unit: String,
    val startTime: Long,
    val endTime: Long? = null,
    val source: String = "TrackMe-Wearable",
)

@Serializable
data class HealthMetricBatchPayload(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val batchId: String,
    val sessionId: String,
    val createdAt: Long,
    val samples: List<HealthMetricSamplePayload>,
)

@Serializable
data class QueuedCommand(
    val command: WatchCommandPayload,
    val enqueuedAt: Long,
    val attempts: Int = 0,
)

data class WatchReducerState(
    val session: SessionStatePayload? = null,
    val offline: Boolean = false,
    val queuedCount: Int = 0,
    val lastAppliedCommandId: String? = null,
)

sealed interface WatchReducerEvent {
    data class SessionUpdated(val payload: SessionStatePayload) : WatchReducerEvent
    data class ConnectivityChanged(val offline: Boolean) : WatchReducerEvent
    data class QueueChanged(val queuedCount: Int) : WatchReducerEvent
    data class CommandAccepted(val commandId: String) : WatchReducerEvent
}

object WatchSessionReducer {
    fun reduce(state: WatchReducerState, event: WatchReducerEvent): WatchReducerState =
        when (event) {
            is WatchReducerEvent.SessionUpdated -> state.copy(session = event.payload)
            is WatchReducerEvent.ConnectivityChanged -> state.copy(offline = event.offline)
            is WatchReducerEvent.QueueChanged -> state.copy(queuedCount = event.queuedCount)
            is WatchReducerEvent.CommandAccepted -> state.copy(lastAppliedCommandId = event.commandId)
        }
}

object CommandReplayPlanner {
    fun enqueueUnique(queue: List<QueuedCommand>, command: WatchCommandPayload, now: Long): List<QueuedCommand> {
        if (queue.any { it.command.commandId == command.commandId }) return queue
        return (queue + QueuedCommand(command = command, enqueuedAt = now)).sortedBy { it.enqueuedAt }
    }

    fun nextReplayBatch(queue: List<QueuedCommand>, maxSize: Int = 25): List<QueuedCommand> =
        queue.sortedWith(compareBy<QueuedCommand> { it.enqueuedAt }.thenBy { it.command.createdAt })
            .take(maxSize.coerceAtLeast(1))

    fun markDelivered(queue: List<QueuedCommand>, deliveredCommandIds: Set<String>): List<QueuedCommand> =
        queue.filterNot { it.command.commandId in deliveredCommandIds }

    fun markAttempted(queue: List<QueuedCommand>, attemptedCommandIds: Set<String>): List<QueuedCommand> =
        queue.map { queued ->
            if (queued.command.commandId in attemptedCommandIds) {
                queued.copy(attempts = queued.attempts + 1)
            } else {
                queued
            }
        }
}

object WatchActionReplayPlanner {
    fun enqueueUnique(queue: List<WatchActionPayload>, action: WatchActionPayload): List<WatchActionPayload> {
        if (queue.any { it.actionId == action.actionId }) return queue
        return (queue + action).sortedForReplay()
    }

    fun nextReplayBatch(queue: List<WatchActionPayload>, maxSize: Int = 25): List<WatchActionPayload> =
        queue.sortedForReplay().take(maxSize.coerceAtLeast(1))

    fun markDelivered(queue: List<WatchActionPayload>, deliveredActionIds: Set<String>): List<WatchActionPayload> =
        queue.filterNot { it.actionId in deliveredActionIds }

    private fun List<WatchActionPayload>.sortedForReplay(): List<WatchActionPayload> =
        sortedWith(compareBy<WatchActionPayload> { it.eventIndex }.thenBy { it.timestamp }.thenBy { it.actionId })
}
