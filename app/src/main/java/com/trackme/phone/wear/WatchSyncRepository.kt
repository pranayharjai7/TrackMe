package com.trackme.phone.wear

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.WorkoutSessionManager
import com.trackme.wear.session.toWearPayload
import com.trackme.wearbridge.SetLogPayload
import com.trackme.wearbridge.SyncEventsPayload
import com.trackme.wearbridge.SyncStatePayload
import com.trackme.wearbridge.WatchActionPayload
import com.trackme.wearbridge.WatchActionType
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import com.trackme.wearbridge.WorkoutMessageType
import com.trackme.wearbridge.WorkoutStatePayload
import com.trackme.wearbridge.WorkoutSyncEventPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class WatchSyncDebugState(
    val lastMessageReceived: String = "None",
    val lastMessageSent: String = "None",
    val lastSyncTime: Long? = null,
    val watchNodeId: String? = null,
    val lastEventIndex: Long = 0L,
    val lastHeartRateBpm: Double? = null,
    val lastCaloriesKcal: Double? = null,
    val lastSteps: Double? = null,
    val lastActiveDurationSeconds: Long? = null,
)

@Singleton
class WatchSyncRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionManager: WorkoutSessionManager,
    private val connectionManager: WatchConnectionManager,
    private val dataStore: DataStore<Preferences>,
) {
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val dataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val appliedActionsKey = stringSetPreferencesKey("wear_applied_watch_actions")
    private val lastEventIndexKey = longPreferencesKey("wear_last_event_index")
    private val lastSessionIdKey = stringPreferencesKey("wear_last_session_id")

    private val recentEvents = MutableStateFlow<List<WorkoutSyncEventPayload>>(emptyList())

    private val _debugState = MutableStateFlow(WatchSyncDebugState())
    val debugState: StateFlow<WatchSyncDebugState> = _debugState.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        connectionManager.start()
        scope.launch {
            connectionManager.refresh()
            publishSnapshot()
            sendSyncState()
        }
        scope.launch {
            var previous: ActiveSessionUiState? = null
            sessionManager.uiState.collect { state ->
                publishWorkoutState(previous, state)
                previous = state
            }
        }
    }

    fun reconnect() {
        connectionManager.reconnect()
        scope.launch {
            delay(500)
            publishSnapshot()
        }
    }

    fun requestWorkoutSync() {
        scope.launch {
            connectionManager.markSyncing()
            publishSnapshot()
            sendSyncState()
            connectionManager.markSyncComplete()
        }
    }

    suspend fun pingWatch(): Long? {
        if (connectionManager.resolveWatchNodes().isEmpty()) return null
        val started = System.currentTimeMillis()
        _pendingPingStartedAt = started
        val payload = WearProtocol.encodeWatchAction(
            WatchActionPayload(
                actionId = java.util.UUID.randomUUID().toString(),
                actionType = WatchActionType.PING,
                timestamp = started,
                sessionId = sessionManager.uiState.value.sessionId,
            ),
        ).encodeToByteArray()
        if (!sendToWatch(WearPaths.MESSAGE_PING, payload)) {
            _pendingPingStartedAt = null
            return null
        }
        repeat(30) {
            delay(100)
            if (_pendingPingStartedAt == null) {
                return connectionManager.lastPingLatencyMs.value
            }
        }
        _pendingPingStartedAt = null
        return null
    }

    private var _pendingPingStartedAt: Long? = null

    fun testCommunication() {
        scope.launch {
            connectionManager.markSyncing()
            val latency = pingWatch()
            if (latency == null) {
                connectionManager.markError("Ping failed — watch unreachable")
            } else {
                publishSnapshot()
                connectionManager.markSyncComplete()
            }
        }
    }

    fun requestHealthSync() {
        scope.launch {
            sendSyncState()
        }
    }

    suspend fun handleWatchAction(action: WatchActionPayload) {
        noteReceived("${WearPaths.WATCH_ACTION}:${action.actionType}:${action.actionId}")
        if (hasApplied(action)) {
            Log.d(WearPaths.LOG_TAG, "Phone deduped watch action ${action.actionId}")
            publishSnapshot()
            return
        }

        val state = sessionManager.uiState.value
        val exerciseId = action.exerciseId
            ?: state.activeExerciseId
            ?: state.restingExerciseId
            ?: state.exercises.sortedBy { it.first.orderIndex }
                .firstOrNull { (planned, _) ->
                    (state.loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
                }?.first?.exerciseId

        when (action.actionType) {
            WatchActionType.START_SET -> exerciseId?.let(sessionManager::startExercise)
            WatchActionType.COMPLETE_SET -> exerciseId?.let { id ->
                val log = action.log ?: SetLogPayload(weightKg = state.quickWeight, reps = state.quickReps)
                sessionManager.completeSet(
                    exerciseId = id,
                    weightKg = log.weightKg,
                    reps = log.reps,
                    durationSeconds = log.durationSeconds,
                    distanceKm = log.distanceKm,
                    speedKmh = log.speedKmh,
                    inclinePercent = log.inclinePercent,
                )
            }
            WatchActionType.SKIP_EXERCISE -> exerciseId?.let(sessionManager::skipExercise)
            WatchActionType.PREVIOUS_EXERCISE -> exerciseId?.let(sessionManager::previousExercise)
            WatchActionType.END_WORKOUT -> sessionManager.finishSession {}
            WatchActionType.SKIP_REST -> exerciseId?.let(sessionManager::skipRest)
            WatchActionType.REQUEST_SYNC -> publishSnapshot()
            WatchActionType.SWITCH_EXERCISE -> exerciseId?.let(sessionManager::startExercise)
            WatchActionType.PING -> Unit
        }

        markApplied(action)
        appendEvent(
            WorkoutSyncEventPayload(
                eventIndex = nextEventIndex(),
                path = WearPaths.WATCH_ACTION,
                timestamp = action.timestamp,
                action = action,
            )
        )
        delay(150)
        publishSnapshot()
    }

    suspend fun handleSyncState(state: SyncStatePayload) {
        noteReceived("${WearPaths.SYNC_STATE}:watchIndex=${state.lastEventIndex}")
        _pendingPingStartedAt?.let { started ->
            val latency = System.currentTimeMillis() - started
            connectionManager.recordPingLatency(latency)
            _pendingPingStartedAt = null
        }
        connectionManager.updateWatchMetadata(
            nodeId = state.nodeId,
            watchModel = state.watchModel,
            wearOsVersion = state.wearOsVersion,
            batteryPercent = state.batteryPercent,
            activeWorkout = sessionManager.uiState.value.sessionId.isNotBlank(),
        )
        connectionManager.markSyncComplete()
        sendMissingEvents(state)
        publishSnapshot()
    }

    fun noteReceived(message: String) {
        Log.d(WearPaths.LOG_TAG, "Phone received $message")
        _debugState.value = _debugState.value.copy(
            lastMessageReceived = message,
            lastSyncTime = System.currentTimeMillis(),
        )
    }

    fun noteWatchHealth(metrics: com.trackme.wearbridge.HealthMetricsPayload) {
        _debugState.value = _debugState.value.copy(
            lastHeartRateBpm = metrics.heartRate,
            lastCaloriesKcal = metrics.calories,
            lastSteps = metrics.steps,
            lastActiveDurationSeconds = metrics.activeDurationSeconds,
            lastSyncTime = System.currentTimeMillis(),
            lastMessageReceived = "${WearPaths.HEALTH_METRICS}:hr=${metrics.heartRate}",
        )
    }

    private suspend fun publishWorkoutState(previous: ActiveSessionUiState?, current: ActiveSessionUiState) {
        if (current.sessionId.isBlank()) {
            val lastSessionId = dataStore.data.map { it[lastSessionIdKey].orEmpty() }.first()
            if (lastSessionId.isNotBlank()) {
                val eventIndex = nextEventIndex()
                sendWorkoutMessage(
                    path = WearPaths.WORKOUT_END,
                    payload = WorkoutStatePayload(
                        messageType = WorkoutMessageType.END,
                        sessionId = lastSessionId,
                        exerciseId = "",
                        exerciseName = "",
                        setNumber = 0,
                        totalSets = 0,
                        lastEventIndex = eventIndex,
                        timestamp = System.currentTimeMillis(),
                    ),
                    eventIndex = eventIndex,
                )
                dataStore.edit { it.remove(lastSessionIdKey) }
            }
            return
        }

        dataStore.edit { it[lastSessionIdKey] = current.sessionId }
        val payload = current.toWearPayload()
        val previousCompleted = previous?.loggedSets?.size ?: 0
        val messageType = when {
            current.isCompleted -> WorkoutMessageType.END
            current.loggedSets.size > previousCompleted -> WorkoutMessageType.COMPLETE_SET
            current.restTimerRunning || current.restingExerciseId != null -> WorkoutMessageType.REST_START
            current.activeExerciseId != null -> WorkoutMessageType.START
            else -> WorkoutMessageType.NEXT_SET
        }
        val path = when (messageType) {
            WorkoutMessageType.START -> WearPaths.WORKOUT_START
            WorkoutMessageType.NEXT_SET -> WearPaths.WORKOUT_NEXT_SET
            WorkoutMessageType.COMPLETE_SET -> WearPaths.WORKOUT_COMPLETE_SET
            WorkoutMessageType.REST_START -> WearPaths.WORKOUT_REST_START
            WorkoutMessageType.END -> WearPaths.WORKOUT_END
            WorkoutMessageType.SNAPSHOT -> WearPaths.WORKOUT_START
        }
        val eventIndex = nextEventIndex()
        val workoutState = WorkoutStatePayload(
            messageType = messageType,
            sessionId = payload.sessionId,
            exerciseId = payload.exercises.getOrNull(payload.exerciseIndex)?.exerciseId.orEmpty(),
            exerciseName = payload.exerciseName,
            setNumber = payload.setIndex,
            totalSets = payload.exercises.getOrNull(payload.exerciseIndex)?.targetSets ?: payload.totalSets,
            weight = payload.targetWeight ?: 0f,
            reps = payload.targetReps ?: 0,
            restTimeRemaining = payload.restRemaining,
            lastEventIndex = eventIndex,
            timestamp = System.currentTimeMillis(),
            sessionState = payload,
        )
        sendWorkoutMessage(path, workoutState, eventIndex)
        publishDataItem(payload)
    }

    private suspend fun publishSnapshot() {
        val state = sessionManager.uiState.value
        if (state.sessionId.isBlank()) return
        val payload = state.toWearPayload()
        val eventIndex = nextEventIndex()
        val workoutState = WorkoutStatePayload(
            messageType = WorkoutMessageType.SNAPSHOT,
            sessionId = payload.sessionId,
            exerciseId = payload.exercises.getOrNull(payload.exerciseIndex)?.exerciseId.orEmpty(),
            exerciseName = payload.exerciseName,
            setNumber = payload.setIndex,
            totalSets = payload.exercises.getOrNull(payload.exerciseIndex)?.targetSets ?: payload.totalSets,
            weight = payload.targetWeight ?: 0f,
            reps = payload.targetReps ?: 0,
            restTimeRemaining = payload.restRemaining,
            lastEventIndex = eventIndex,
            timestamp = System.currentTimeMillis(),
            sessionState = payload,
        )
        sendWorkoutMessage(WearPaths.WORKOUT_START, workoutState, eventIndex)
        publishDataItem(payload)
    }

    private suspend fun sendWorkoutMessage(path: String, payload: WorkoutStatePayload, eventIndex: Long) {
        val encoded = WearProtocol.encodeWorkoutState(payload).encodeToByteArray()
        val sent = sendToWatch(path, encoded)
        if (sent) {
            appendEvent(
                WorkoutSyncEventPayload(
                    eventIndex = eventIndex,
                    path = path,
                    timestamp = payload.timestamp,
                    workoutState = payload,
                )
            )
        }
    }

    private suspend fun sendSyncState() {
        val node = connectionManager.currentConnectedNode()
        val state = sessionManager.uiState.value
        val payload = SyncStatePayload(
            sessionId = state.sessionId,
            lastEventIndex = currentEventIndex(),
            nodeId = node?.id,
            nodeName = node?.displayName,
            timestamp = System.currentTimeMillis(),
        )
        sendToWatch(WearPaths.SYNC_STATE, WearProtocol.encodeSyncState(payload).encodeToByteArray())
    }

    private suspend fun sendMissingEvents(watchState: SyncStatePayload) {
        val events = recentEvents.value
            .filter { it.sessionIdMatches(watchState.sessionId) && it.eventIndex > watchState.lastEventIndex }
            .sortedBy { it.eventIndex }
        if (events.isEmpty()) return
        val payload = SyncEventsPayload(
            sessionId = watchState.sessionId,
            fromExclusiveEventIndex = watchState.lastEventIndex,
            events = events,
            timestamp = System.currentTimeMillis(),
        )
        sendToWatch(WearPaths.SYNC_EVENTS, WearProtocol.encodeSyncEvents(payload).encodeToByteArray())
    }

    private suspend fun publishDataItem(payload: com.trackme.wearbridge.SessionStatePayload) {
        runCatching {
            val request = PutDataMapRequest.create(WearPaths.DATA_SESSION_STATE).apply {
                dataMap.putString(WearPaths.KEY_PAYLOAD, WearProtocol.encodeSessionState(payload))
                dataMap.putLong(WearPaths.KEY_UPDATED_AT, payload.updatedAt)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }.onFailure { Log.e(WearPaths.LOG_TAG, "Phone DataClient publish failed", it) }
    }

    private suspend fun sendToWatch(path: String, payload: ByteArray): Boolean {
        val nodes = connectionManager.resolveWatchNodes()
        if (nodes.isEmpty()) {
            _debugState.value = _debugState.value.copy(lastMessageSent = "$path failed: no peer nodes")
            Log.w(WearPaths.LOG_TAG, "Phone send skipped for $path: no connected watch nodes")
            return false
        }
        var delivered = false
        var lastError: Throwable? = null
        for (node in nodes) {
            val sent = runCatching {
                messageClient.sendMessage(node.id, path, payload).await()
                Log.d(WearPaths.LOG_TAG, "Phone sent $path to ${node.displayName}(${node.id})")
                true
            }.getOrElse { error ->
                lastError = error
                Log.e(WearPaths.LOG_TAG, "Phone send failed for $path to ${node.id}", error)
                false
            }
            if (sent) {
                delivered = true
                _debugState.value = _debugState.value.copy(
                    lastMessageSent = path,
                    lastSyncTime = System.currentTimeMillis(),
                    watchNodeId = node.id,
                    lastEventIndex = currentEventIndex(),
                )
            }
        }
        if (!delivered) {
            _debugState.value = _debugState.value.copy(
                lastMessageSent = "$path failed: ${lastError?.message ?: "unknown"}",
            )
        }
        return delivered
    }

    private suspend fun nextEventIndex(): Long {
        val next = currentEventIndex() + 1L
        dataStore.edit { it[lastEventIndexKey] = next }
        _debugState.value = _debugState.value.copy(lastEventIndex = next)
        return next
    }

    private suspend fun currentEventIndex(): Long =
        dataStore.data.map { it[lastEventIndexKey] ?: 0L }.first()

    private fun appendEvent(event: WorkoutSyncEventPayload) {
        recentEvents.value = (recentEvents.value + event)
            .sortedBy { it.eventIndex }
            .takeLast(150)
    }

    private suspend fun hasApplied(action: WatchActionPayload): Boolean =
        dataStore.data.map { prefs ->
            val applied = prefs[appliedActionsKey].orEmpty()
            action.actionId in applied || action.timestampKey() in applied
        }.first()

    private suspend fun markApplied(action: WatchActionPayload) {
        dataStore.edit { prefs ->
            val applied = prefs[appliedActionsKey].orEmpty()
            prefs[appliedActionsKey] = (applied + action.actionId + action.timestampKey())
                .toList()
                .takeLast(500)
                .toSet()
        }
    }

    private fun WatchActionPayload.timestampKey(): String =
        "$sessionId:$actionType:$timestamp:${exerciseId.orEmpty()}:${setNumber ?: -1}"

    private fun WorkoutSyncEventPayload.sessionIdMatches(sessionId: String): Boolean =
        workoutState?.sessionId == sessionId || action?.sessionId == sessionId
}
