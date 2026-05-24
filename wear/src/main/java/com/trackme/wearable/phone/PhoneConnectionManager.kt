package com.trackme.wearable.phone

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
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
        val lastSeenAt: Long,
    ) : PhoneConnectionState()
}

class PhoneConnectionManager(context: Context) : CapabilityClient.OnCapabilityChangedListener {
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow<PhoneConnectionState>(PhoneConnectionState.Connecting)
    val connectionState: StateFlow<PhoneConnectionState> = _connectionState.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        capabilityClient.addListener(this, WearPaths.CAPABILITY_PHONE)
        scope.launch {
            while (true) {
                refresh()
                delay(15_000)
            }
        }
    }

    fun reconnect() {
        _connectionState.value = PhoneConnectionState.Connecting
        scope.launch { refresh() }
    }

    suspend fun currentPhoneNode(): Node? {
        refresh()
        val connected = _connectionState.value as? PhoneConnectionState.Connected ?: return null
        return runCatching { nodeClient.connectedNodes.await().firstOrNull { it.id == connected.nodeId } }
            .getOrNull()
    }

    suspend fun localNode(): Node? =
        runCatching { nodeClient.localNode.await() }.getOrNull()

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        Log.d(WearPaths.LOG_TAG, "Watch phone capability changed nodes=${capabilityInfo.nodes.size}")
        applyCapability(capabilityInfo.nodes)
    }

    suspend fun refresh() {
        runCatching {
            val capability = capabilityClient
                .getCapability(WearPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
            applyCapability(capability.nodes)
        }.onFailure { error ->
            Log.e(WearPaths.LOG_TAG, "Watch phone connection refresh failed", error)
            _connectionState.value = PhoneConnectionState.Disconnected
        }
    }

    private fun applyCapability(nodes: Set<Node>) {
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        _connectionState.value = if (node != null) {
            PhoneConnectionState.Connected(
                nodeId = node.id,
                nodeName = node.displayName.ifBlank { "Phone" },
                lastSeenAt = System.currentTimeMillis(),
            )
        } else {
            PhoneConnectionState.Disconnected
        }
        Log.d(WearPaths.LOG_TAG, "Watch phone connection state=${_connectionState.value}")
    }
}
