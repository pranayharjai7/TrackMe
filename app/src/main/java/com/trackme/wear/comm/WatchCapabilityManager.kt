package com.trackme.wear.comm

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.trackme.wearbridge.WearPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class WatchCapabilityManager @Inject constructor(
    @ApplicationContext context: Context,
) : CapabilityClient.OnCapabilityChangedListener {
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _connectedWatches = MutableStateFlow<List<String>>(emptyList())
    val connectedWatches: StateFlow<List<String>> = _connectedWatches.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        capabilityClient.addListener(this, WearPaths.CAPABILITY_WATCH)
        scope.launch { refresh() }
    }

    suspend fun hasConnectedWatch(): Boolean {
        refresh()
        return _connectedWatches.value.isNotEmpty()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _connectedWatches.value = capabilityInfo.nodes.map { it.stableName() }
    }

    private suspend fun refresh() {
        runCatching {
            val capability = capabilityClient
                .getCapability(WearPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
            _connectedWatches.value = capability.nodes.map { it.stableName() }
        }.onFailure { error ->
            Log.w("WatchCapabilityManager", "Unable to refresh watch capability", error)
            _connectedWatches.value = emptyList()
        }
    }

    private fun Node.stableName(): String = displayName.takeIf { it.isNotBlank() } ?: id
}
