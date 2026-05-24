package com.trackme.wearable.comm

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.trackme.wearable.data.QueuedCommandStore
import com.trackme.wearbridge.CommandReplayPlanner
import com.trackme.wearbridge.HealthMetricBatchPayload
import com.trackme.wearbridge.SessionStatePayload
import com.trackme.wearbridge.WatchCommandPayload
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearSessionRepository(context: Context) : DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val appContext = context.applicationContext
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)
    private val commandStore = QueuedCommandStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow<SessionStatePayload?>(null)
    val sessionState: StateFlow<SessionStatePayload?> = _sessionState.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    val queuedCount = commandStore.queuedCount

    fun start() {
        dataClient.addListener(this)
        capabilityClient.addListener(this, WearPaths.CAPABILITY_PHONE)
        scope.launch {
            refreshConnectivity()
            requestSnapshot()
            flushQueuedCommands()
        }
    }

    suspend fun sendCommand(command: WatchCommandPayload): Boolean {
        val sent = sendPayload(WearPaths.MESSAGE_COMMAND, WearProtocol.encodeCommand(command).encodeToByteArray())
        if (!sent) {
            commandStore.enqueue(command)
        }
        return sent
    }

    suspend fun sendHealthBatch(batch: HealthMetricBatchPayload): Boolean =
        sendPayload(WearPaths.MESSAGE_HEALTH_BATCH, WearProtocol.encodeHealthBatch(batch).encodeToByteArray())

    suspend fun requestSnapshot() {
        val current = _sessionState.value
        sendCommand(
            WatchCommandPayload(
                commandId = java.util.UUID.randomUUID().toString(),
                type = com.trackme.wearbridge.WatchCommandType.REQUEST_SNAPSHOT,
                createdAt = System.currentTimeMillis(),
                sessionId = current?.sessionId.orEmpty(),
                dayId = current?.dayId,
                sessionDateMillis = current?.sessionDateMillis,
            )
        )
    }

    suspend fun flushQueuedCommands() {
        val queue = commandStore.readQueue()
        val batch = CommandReplayPlanner.nextReplayBatch(queue)
        if (batch.isEmpty()) return

        val delivered = mutableSetOf<String>()
        val attempted = mutableSetOf<String>()
        for (queued in batch) {
            attempted += queued.command.commandId
            val sent = sendPayload(
                WearPaths.MESSAGE_COMMAND,
                WearProtocol.encodeCommand(queued.command).encodeToByteArray(),
            )
            if (sent) delivered += queued.command.commandId
        }

        if (delivered.isNotEmpty()) commandStore.markDelivered(delivered)
        if (attempted.minus(delivered).isNotEmpty()) commandStore.markAttempted(attempted.minus(delivered))
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearPaths.DATA_SESSION_STATE) {
                val payload = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap
                    .getString(WearPaths.KEY_PAYLOAD)
                    ?: return@forEach
                runCatching {
                    _sessionState.value = WearProtocol.decodeSessionState(payload)
                }.onFailure { Log.w("WearSessionRepository", "Invalid session payload", it) }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _offline.value = capabilityInfo.nodes.none { it.isNearby || it.isConnected }
        if (!_offline.value) {
            scope.launch { flushQueuedCommands() }
        }
    }

    private suspend fun sendPayload(path: String, data: ByteArray): Boolean {
        val nodes = reachablePhones()
        if (nodes.isEmpty()) {
            _offline.value = true
            return false
        }

        val delivered = nodes.map { node ->
            runCatching {
                messageClient.sendMessage(node.id, path, data).await()
                true
            }.getOrElse { error ->
                Log.w("WearSessionRepository", "Unable to send $path to ${node.displayName}", error)
                false
            }
        }.any { it }
        _offline.value = !delivered
        return delivered
    }

    private suspend fun refreshConnectivity() {
        _offline.value = reachablePhones().isEmpty()
    }

    private suspend fun reachablePhones(): Set<Node> =
        runCatching {
            capabilityClient
                .getCapability(WearPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrElse {
            Log.w("WearSessionRepository", "Unable to resolve phone capability", it)
            emptySet()
        }

    private val Node.isConnected: Boolean
        get() = isNearby
}
