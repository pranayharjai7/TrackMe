package com.trackme.wearable.phone

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.trackme.wearable.service.ActiveWorkoutService
import com.trackme.wearable.service.WorkoutHealthService
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionStatePayload
import com.trackme.wearbridge.SetLogPayload
import com.trackme.wearbridge.SyncEventsPayload
import com.trackme.wearbridge.SyncStatePayload
import com.trackme.wearbridge.WatchActionPayload
import com.trackme.wearbridge.WatchActionReplayPlanner
import com.trackme.wearbridge.WatchActionType
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import com.trackme.wearbridge.WorkoutMessageType
import com.trackme.wearbridge.WorkoutStatePayload
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Context.workoutStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trackme_watch_sync_state",
)

class WorkoutStateSync(
    context: Context,
    private val connectionManager: PhoneConnectionManager,
) : DataClient.OnDataChangedListener {
    private val appContext = context.applicationContext
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val dataStore = appContext.workoutStateDataStore
    private val actionStore = QueuedWatchActionStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val localEventIndexKey = longPreferencesKey("watch_last_event_index")
    private val phoneEventIndexKey = longPreferencesKey("phone_last_event_index")

    private val _sessionState = MutableStateFlow<SessionStatePayload?>(null)
    val sessionState: StateFlow<SessionStatePayload?> = _sessionState.asStateFlow()

    private val _lastPhoneEventIndex = MutableStateFlow(0L)
    val lastPhoneEventIndex: StateFlow<Long> = _lastPhoneEventIndex.asStateFlow()

    val queuedCount = actionStore.queuedCount

    companion object {
        const val SYNC_DEBOUNCE_MS = 5_000L
    }

    @Volatile private var lastSyncStateAt = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        dataClient.addListener(this)
        scope.launch {
            fetchInitialSessionSnapshot()
        }
        scope.launch {
            connectionManager.connectionState.collect { state ->
                if (state is PhoneConnectionState.Connected) {
                    flushActionQueue()
                    sendSyncState()
                }
            }
        }
        scope.launch {
            while (true) {
                delay(20_000)
                if (connectionManager.resolvePhoneNodes().isNotEmpty()) {
                    sendSyncState()
                }
            }
        }
    }

    private suspend fun fetchInitialSessionSnapshot() {
        runCatching {
            val items = dataClient.dataItems.await()
            for (item in items) {
                if (item.uri.path != WearPaths.DATA_SESSION_STATE) continue
                val json = DataMapItem.fromDataItem(item).dataMap.getString(WearPaths.KEY_PAYLOAD)
                    ?: continue
                _sessionState.value = WearProtocol.decodeSessionState(json)
                Log.d(WearPaths.LOG_TAG, "Watch loaded initial DataClient session snapshot")
                return
            }
        }.onFailure { error ->
            Log.w(WearPaths.LOG_TAG, "Watch initial DataClient snapshot read failed", error)
        }
    }

    suspend fun sendAction(
        actionType: WatchActionType,
        exerciseId: String? = null,
        setNumber: Int? = null,
        log: SetLogPayload? = null,
    ) {
        val session = _sessionState.value
        val action = WatchActionPayload(
            actionId = UUID.randomUUID().toString(),
            actionType = actionType,
            timestamp = System.currentTimeMillis(),
            sessionId = session?.sessionId.orEmpty(),
            exerciseId = exerciseId ?: session?.exercises?.getOrNull(session.exerciseIndex)?.exerciseId,
            setNumber = setNumber ?: session?.setIndex,
            eventIndex = nextLocalEventIndex(),
            log = log,
        )
        if (!sendToPhone(WearPaths.WATCH_ACTION, WearProtocol.encodeWatchAction(action).encodeToByteArray())) {
            actionStore.enqueue(action)
            Log.w(WearPaths.LOG_TAG, "Watch queued action ${action.actionType}:${action.actionId}")
        }
    }

    suspend fun flushActionQueue() {
        val queued = actionStore.read()
        if (queued.isEmpty()) return
        val delivered = mutableSetOf<String>()
        WatchActionReplayPlanner.nextReplayBatch(queued).forEach { action ->
            val sent = sendToPhone(WearPaths.WATCH_ACTION, WearProtocol.encodeWatchAction(action).encodeToByteArray())
            if (sent) delivered += action.actionId
        }
        if (delivered.isNotEmpty()) actionStore.remove(delivered)
    }

    suspend fun sendSyncState() {
        val now = System.currentTimeMillis()
        if (now - lastSyncStateAt < SYNC_DEBOUNCE_MS) return
        lastSyncStateAt = now
        val localNode = connectionManager.localNode()
        val payload = SyncStatePayload(
            sessionId = _sessionState.value?.sessionId.orEmpty(),
            lastEventIndex = _lastPhoneEventIndex.value,
            nodeId = localNode?.id,
            nodeName = localNode?.displayName,
            watchModel = android.os.Build.MODEL,
            wearOsVersion = android.os.Build.VERSION.RELEASE,
            batteryPercent = batteryPercent(),
            timestamp = System.currentTimeMillis(),
        )
        sendToPhone(WearPaths.SYNC_STATE, WearProtocol.encodeSyncState(payload).encodeToByteArray())
    }

    fun handleWorkoutState(payload: WorkoutStatePayload) {
        Log.d(WearPaths.LOG_TAG, "Watch received workout ${payload.messageType} index=${payload.lastEventIndex}")
        _lastPhoneEventIndex.value = maxOf(_lastPhoneEventIndex.value, payload.lastEventIndex)
        scope.launch { dataStore.edit { it[phoneEventIndexKey] = _lastPhoneEventIndex.value } }
        val nextSession = payload.sessionState ?: _sessionState.value?.copy(
            sessionId = payload.sessionId,
            exerciseIndex = _sessionState.value?.exerciseIndex ?: 0,
            setIndex = payload.setNumber,
            restRemaining = payload.restTimeRemaining,
            exerciseName = payload.exerciseName,
            targetReps = payload.reps.takeIf { it > 0 },
            targetWeight = payload.weight.takeIf { it > 0f },
            restActive = payload.messageType == WorkoutMessageType.REST_START,
            isCompleted = payload.messageType == WorkoutMessageType.END,
            updatedAt = payload.timestamp,
        ) ?: payload.toSessionState()

        _sessionState.value = nextSession
        if (nextSession.isCompleted || payload.messageType == WorkoutMessageType.END || nextSession.sessionId.isBlank()) {
            appContext.stopService(Intent(appContext, WorkoutHealthService::class.java))
            ActiveWorkoutService.stop(appContext)
        } else {
            ContextCompat.startForegroundService(appContext, Intent(appContext, WorkoutHealthService::class.java))
            ActiveWorkoutService.start(
                appContext,
                title = nextSession.exerciseName.ifBlank { "Workout" },
                body = "Set ${nextSession.setIndex} · ${nextSession.sessionProgressPercent}%",
            )
        }
    }

    fun handleSyncEvents(payload: SyncEventsPayload) {
        payload.events.sortedBy { it.eventIndex }.forEach { event ->
            event.workoutState?.let(::handleWorkoutState)
        }
    }

    fun handleSyncState(payload: SyncStatePayload) {
        Log.d(WearPaths.LOG_TAG, "Watch received phone sync state index=${payload.lastEventIndex}")
        _lastPhoneEventIndex.value = maxOf(_lastPhoneEventIndex.value, payload.lastEventIndex)
        scope.launch {
            dataStore.edit { it[phoneEventIndexKey] = _lastPhoneEventIndex.value }
            flushActionQueue()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearPaths.DATA_SESSION_STATE) {
                val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearPaths.KEY_PAYLOAD)
                    ?: return@forEach
                runCatching {
                    _sessionState.value = WearProtocol.decodeSessionState(json)
                    Log.d(WearPaths.LOG_TAG, "Watch DataClient session snapshot received")
                }.onFailure { Log.e(WearPaths.LOG_TAG, "Watch failed to decode DataClient snapshot", it) }
            }
        }
    }

    private suspend fun sendToPhone(path: String, payload: ByteArray): Boolean {
        val nodes = connectionManager.peerNodes.value.ifEmpty {
            connectionManager.resolvePhoneNodes()
        }
        if (nodes.isEmpty()) {
            Log.w(WearPaths.LOG_TAG, "Watch send skipped for $path: no connected phone nodes")
            return false
        }
        var delivered = false
        for (node in nodes) {
            val sent = runCatching {
                messageClient.sendMessage(node.id, path, payload).await()
                Log.d(WearPaths.LOG_TAG, "Watch sent $path to ${node.displayName}(${node.id})")
                true
            }.getOrElse { error ->
                Log.e(WearPaths.LOG_TAG, "Watch send failed for $path to ${node.id}", error)
                false
            }
            if (sent) delivered = true
        }
        return delivered
    }

    private suspend fun nextLocalEventIndex(): Long =
        dataStore.updateData { prefs ->
            val next = (prefs[localEventIndexKey] ?: 0L) + 1L
            prefs.toMutablePreferences().apply { set(localEventIndexKey, next) }
        }[localEventIndexKey] ?: 1L

    private fun WorkoutStatePayload.toSessionState(): SessionStatePayload =
        SessionStatePayload(
            sessionId = sessionId,
            exerciseIndex = 0,
            setIndex = setNumber,
            completedSets = 0,
            totalSets = totalSets,
            restRemaining = restTimeRemaining,
            exerciseName = exerciseName,
            muscle = "",
            equipment = "",
            loggingType = LoggingTypePayload.WEIGHTED_REPS,
            targetReps = reps.takeIf { it > 0 },
            targetWeight = weight.takeIf { it > 0f },
            sessionProgressPercent = 0,
            restActive = messageType == WorkoutMessageType.REST_START,
            isCompleted = messageType == WorkoutMessageType.END,
            updatedAt = timestamp,
        )

    private fun batteryPercent(): Int? {
        val batteryManager = appContext.getSystemService(android.os.BatteryManager::class.java) ?: return null
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it >= 0 }
    }
}
