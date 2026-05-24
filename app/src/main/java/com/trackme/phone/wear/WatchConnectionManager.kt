package com.trackme.phone.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.trackme.wearbridge.WearPaths
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class WatchConnectionState {
    data object Connecting : WatchConnectionState()
    data object Disconnected : WatchConnectionState()
    data class Connected(
        val nodeId: String,
        val nodeName: String,
        val appInstalled: Boolean,
        val lastSeenAt: Long,
        val watchModel: String? = null,
    ) : WatchConnectionState()
}

@Singleton
class WatchConnectionManager @Inject constructor(
    @ApplicationContext context: Context,
) : CapabilityClient.OnCapabilityChangedListener {
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Connecting)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _knownNodes = MutableStateFlow<List<Node>>(emptyList())
    val knownNodes: StateFlow<List<Node>> = _knownNodes.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        capabilityClient.addListener(this, WearPaths.CAPABILITY_WATCH)
        scope.launch {
            while (true) {
                refresh()
                delay(15_000)
            }
        }
    }

    fun reconnect() {
        _connectionState.value = WatchConnectionState.Connecting
        scope.launch { refresh() }
    }

    suspend fun currentConnectedNode(): Node? {
        refresh()
        val connected = connectionState.value as? WatchConnectionState.Connected ?: return null
        return _knownNodes.value.firstOrNull { it.id == connected.nodeId }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        Log.d(WearPaths.LOG_TAG, "Phone capability changed: ${capabilityInfo.name} nodes=${capabilityInfo.nodes.size}")
        applyCapability(capabilityInfo.nodes)
    }

    suspend fun refresh() {
        runCatching {
            val nodes = nodeClient.connectedNodes.await()
            _knownNodes.value = nodes
            val capability = capabilityClient
                .getCapability(WearPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
            applyCapability(capability.nodes)
        }.onFailure { error ->
            Log.e(WearPaths.LOG_TAG, "Phone watch connection refresh failed", error)
            _connectionState.value = WatchConnectionState.Disconnected
        }
    }

    fun updateWatchMetadata(nodeId: String?, watchModel: String?) {
        if (nodeId == null || watchModel.isNullOrBlank()) return
        val current = _connectionState.value as? WatchConnectionState.Connected ?: return
        if (current.nodeId == nodeId) {
            _connectionState.value = current.copy(watchModel = watchModel, lastSeenAt = System.currentTimeMillis())
        }
    }

    private fun applyCapability(nodesWithApp: Set<Node>) {
        val node = nodesWithApp.firstOrNull { it.isNearby } ?: nodesWithApp.firstOrNull()
        _connectionState.value = if (node != null) {
            WatchConnectionState.Connected(
                nodeId = node.id,
                nodeName = node.displayName.ifBlank { "Wear OS watch" },
                appInstalled = true,
                lastSeenAt = System.currentTimeMillis(),
                watchModel = (connectionState.value as? WatchConnectionState.Connected)
                    ?.takeIf { it.nodeId == node.id }
                    ?.watchModel,
            )
        } else {
            WatchConnectionState.Disconnected
        }
        Log.d(WearPaths.LOG_TAG, "Phone watch connection state=${_connectionState.value}")
    }
}
