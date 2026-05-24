package com.trackme.phone.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.trackme.wearbridge.WearNodeDiscovery
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
    data object Syncing : WatchConnectionState()
    data class Connected(
        val nodeId: String,
        val nodeName: String,
        val appInstalled: Boolean,
        val lastSeenAt: Long,
        val watchModel: String? = null,
        val wearOsVersion: String? = null,
        val batteryPercent: Int? = null,
        val activeWorkout: Boolean = false,
    ) : WatchConnectionState()
    data class Error(val message: String) : WatchConnectionState()
}

@Singleton
class WatchConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : CapabilityClient.OnCapabilityChangedListener {
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Connecting)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _peerNodes = MutableStateFlow<List<Node>>(emptyList())
    val peerNodes: StateFlow<List<Node>> = _peerNodes.asStateFlow()

    private val _lastPingLatencyMs = MutableStateFlow<Long?>(null)
    val lastPingLatencyMs: StateFlow<Long?> = _lastPingLatencyMs.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            WearNodeDiscovery.ensureLocalCapability(context, WearPaths.CAPABILITY_PHONE)
        }
        capabilityClient.addListener(this, WearPaths.CAPABILITY_WATCH)
        scope.launch {
            refresh()
            while (true) {
                delay(10_000)
                refresh()
            }
        }
    }

    fun reconnect() {
        _connectionState.value = WatchConnectionState.Connecting
        scope.launch {
            WearNodeDiscovery.ensureLocalCapability(context, WearPaths.CAPABILITY_PHONE)
            repeat(3) { attempt ->
                refresh()
                if (_peerNodes.value.isNotEmpty()) return@launch
                delay(800L * (attempt + 1))
            }
        }
    }

    suspend fun resolveWatchNodes(): List<Node> {
        refresh()
        return _peerNodes.value.ifEmpty {
            WearNodeDiscovery.resolvePeerNodes(context, WearPaths.CAPABILITY_WATCH)
                .also { _peerNodes.value = it }
        }
    }

    suspend fun currentConnectedNode(): Node? = resolveWatchNodes().firstOrNull()

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearPaths.CAPABILITY_WATCH) return
        Log.d(WearPaths.LOG_TAG, "Phone capability changed: nodes=${capabilityInfo.nodes.size}")
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        runCatching {
            val capabilityReachable = capabilityClient
                .getCapability(WearPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            val peers = WearNodeDiscovery.resolvePeerNodes(context, WearPaths.CAPABILITY_WATCH)
            _peerNodes.value = peers
            applyPeers(peers, capabilityReachable.isNotEmpty())
        }.onFailure { error ->
            Log.e(WearPaths.LOG_TAG, "Phone watch connection refresh failed", error)
            _connectionState.value = WatchConnectionState.Error(error.message ?: "Refresh failed")
        }
    }

    private var lastConnectedSnapshot: WatchConnectionState.Connected? = null

    fun markSyncing() {
        val current = _connectionState.value
        if (current is WatchConnectionState.Connected) {
            lastConnectedSnapshot = current
            _connectionState.value = WatchConnectionState.Syncing
        }
    }

    fun markSyncComplete() {
        lastConnectedSnapshot?.let { snapshot ->
            _connectionState.value = snapshot.copy(lastSeenAt = System.currentTimeMillis())
        } ?: scope.launch { refresh() }
    }

    fun markError(message: String) {
        _connectionState.value = WatchConnectionState.Error(message)
    }

    fun updateWatchMetadata(
        nodeId: String?,
        watchModel: String? = null,
        wearOsVersion: String? = null,
        batteryPercent: Int? = null,
        activeWorkout: Boolean? = null,
    ) {
        if (nodeId == null) return
        val current = _connectionState.value
        val connected = when (current) {
            is WatchConnectionState.Connected -> current
            is WatchConnectionState.Syncing -> lastConnectedSnapshot
            else -> null
        } ?: return
        if (connected.nodeId == nodeId) {
            _connectionState.value = connected.copy(
                watchModel = watchModel ?: connected.watchModel,
                wearOsVersion = wearOsVersion ?: connected.wearOsVersion,
                batteryPercent = batteryPercent ?: connected.batteryPercent,
                activeWorkout = activeWorkout ?: connected.activeWorkout,
                lastSeenAt = System.currentTimeMillis(),
                appInstalled = true,
            )
        }
    }

    fun recordPingLatency(latencyMs: Long) {
        _lastPingLatencyMs.value = latencyMs
    }

    private fun applyPeers(peers: List<Node>, capabilityAdvertised: Boolean) {
        val node = peers.firstOrNull()
        _connectionState.value = if (node != null) {
            WatchConnectionState.Connected(
                nodeId = node.id,
                nodeName = node.displayName.ifBlank { "Wear OS watch" },
                appInstalled = capabilityAdvertised,
                lastSeenAt = System.currentTimeMillis(),
                watchModel = lastConnectedSnapshot?.takeIf { it.nodeId == node.id }?.watchModel,
                wearOsVersion = lastConnectedSnapshot?.takeIf { it.nodeId == node.id }?.wearOsVersion,
                batteryPercent = lastConnectedSnapshot?.takeIf { it.nodeId == node.id }?.batteryPercent,
                activeWorkout = lastConnectedSnapshot?.takeIf { it.nodeId == node.id }?.activeWorkout ?: false,
            ).also { lastConnectedSnapshot = it }
        } else {
            WatchConnectionState.Disconnected
        }
        Log.d(WearPaths.LOG_TAG, "Phone watch connection state=${_connectionState.value} peers=${peers.size}")
    }
}
