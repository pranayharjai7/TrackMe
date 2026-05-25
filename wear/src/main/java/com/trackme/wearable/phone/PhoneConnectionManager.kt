package com.trackme.wearable.phone

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.trackme.wearbridge.WearNodeDiscovery
import com.trackme.wearbridge.WearPaths
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

sealed class PhoneConnectionState {
    data object Connecting : PhoneConnectionState()
    data object Disconnected : PhoneConnectionState()
    data class Connected(
        val nodeId: String,
        val nodeName: String,
        val appInstalled: Boolean,
        val lastSeenAt: Long,
    ) : PhoneConnectionState()
}

class PhoneConnectionManager(private val context: Context) : CapabilityClient.OnCapabilityChangedListener {
    private val appContext = context.applicationContext
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow<PhoneConnectionState>(PhoneConnectionState.Connecting)
    val connectionState: StateFlow<PhoneConnectionState> = _connectionState.asStateFlow()

    private val _peerNodes = MutableStateFlow<List<Node>>(emptyList())
    val peerNodes: StateFlow<List<Node>> = _peerNodes.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            WearNodeDiscovery.ensureLocalCapability(appContext, WearPaths.CAPABILITY_WATCH)
        }
        capabilityClient.addListener(this, WearPaths.CAPABILITY_PHONE)
        scope.launch { pollWhileDisconnected() }
    }

    private suspend fun pollWhileDisconnected() {
        refresh()
        var backoffMs = 10_000L
        while (true) {
            if (_connectionState.value !is PhoneConnectionState.Connected) {
                delay(backoffMs)
                refresh()
                backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(60_000L)
            } else {
                backoffMs = 10_000L
                delay(30_000L)
                refresh()
            }
        }
    }

    fun reconnect() {
        _connectionState.value = PhoneConnectionState.Connecting
        scope.launch {
            WearNodeDiscovery.ensureLocalCapability(appContext, WearPaths.CAPABILITY_WATCH)
            repeat(3) { attempt ->
                refresh()
                if (_peerNodes.value.isNotEmpty()) return@launch
                delay(800L * (attempt + 1))
            }
        }
    }

    suspend fun resolvePhoneNodes(): List<Node> {
        refresh()
        return _peerNodes.value.ifEmpty {
            WearNodeDiscovery.resolvePeerNodes(appContext, WearPaths.CAPABILITY_PHONE)
                .also { _peerNodes.value = it }
        }
    }

    suspend fun currentPhoneNode(): Node? = resolvePhoneNodes().firstOrNull()

    suspend fun localNode(): Node? =
        runCatching { Wearable.getNodeClient(appContext).localNode.await() }.getOrNull()

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearPaths.CAPABILITY_PHONE) return
        Log.d(WearPaths.LOG_TAG, "Watch phone capability changed nodes=${capabilityInfo.nodes.size}")
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        runCatching {
            val capabilityReachable = capabilityClient
                .getCapability(WearPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            val peers = WearNodeDiscovery.resolvePeerNodes(appContext, WearPaths.CAPABILITY_PHONE)
            _peerNodes.value = peers
            applyPeers(peers, capabilityReachable.isNotEmpty())
        }.onFailure { error ->
            Log.e(WearPaths.LOG_TAG, "Watch phone connection refresh failed", error)
            _connectionState.value = PhoneConnectionState.Disconnected
        }
    }

    private fun applyPeers(peers: List<Node>, capabilityAdvertised: Boolean) {
        val node = peers.firstOrNull()
        _connectionState.value = if (node != null) {
            PhoneConnectionState.Connected(
                nodeId = node.id,
                nodeName = node.displayName.ifBlank { "Phone" },
                appInstalled = capabilityAdvertised,
                lastSeenAt = System.currentTimeMillis(),
            )
        } else {
            PhoneConnectionState.Disconnected
        }
        Log.d(WearPaths.LOG_TAG, "Watch phone connection state=${_connectionState.value} peers=${peers.size}")
    }
}
