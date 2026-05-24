package com.trackme.wearbridge

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Resolves peer nodes for Wear Data Layer messaging.
 *
 * Order: capability (reachable) → capability (all) → [connectedNodes].
 * Matches the proven JobTracker pattern where [NodeClient.connectedNodes] is the
 * reliable fallback when capability sync is delayed.
 */
object WearNodeDiscovery {

    suspend fun ensureLocalCapability(context: Context, capabilityName: String) {
        runCatching {
            Wearable.getCapabilityClient(context.applicationContext)
                .addLocalCapability(capabilityName)
                .await()
            Log.d(WearPaths.LOG_TAG, "Registered local capability=$capabilityName")
        }.onFailure { error ->
            Log.w(WearPaths.LOG_TAG, "Local capability register skipped ($capabilityName): ${error.message}")
        }
    }

    suspend fun resolvePeerNodes(
        context: Context,
        remoteCapabilityName: String,
    ): List<Node> {
        val appContext = context.applicationContext
        val nodeClient = Wearable.getNodeClient(appContext)
        val capabilityClient = Wearable.getCapabilityClient(appContext)

        val connected = runCatching { nodeClient.connectedNodes.await() }
            .getOrElse { emptyList() }
        val reachable = runCatching {
            capabilityClient
                .getCapability(remoteCapabilityName, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrElse { emptySet() }
        val allCapable = runCatching {
            capabilityClient
                .getCapability(remoteCapabilityName, CapabilityClient.FILTER_ALL)
                .await()
                .nodes
        }.getOrElse { emptySet() }

        val peers = when {
            reachable.isNotEmpty() -> reachable
            allCapable.isNotEmpty() -> allCapable
            connected.isNotEmpty() -> connected.toSet()
            else -> emptySet()
        }

        val ordered = peers
            .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.displayName })
            .distinctBy { it.id }

        Log.d(
            WearPaths.LOG_TAG,
            "resolvePeerNodes capability=$remoteCapabilityName " +
                "reachable=${reachable.size} all=${allCapable.size} connected=${connected.size} -> ${ordered.size}",
        )
        return ordered
    }

    suspend fun localNodeId(context: Context): String? =
        runCatching { Wearable.getNodeClient(context.applicationContext).localNode.await().id }
            .getOrNull()
}
