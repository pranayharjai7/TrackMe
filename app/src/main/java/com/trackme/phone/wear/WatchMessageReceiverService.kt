package com.trackme.phone.wear

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WatchMessageReceiverService : WearableListenerService() {
    @Inject lateinit var watchSyncRepository: WatchSyncRepository
    @Inject lateinit var watchHealthDataReceiver: WatchHealthDataReceiver
    @Inject lateinit var watchConnectionManager: WatchConnectionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        watchConnectionManager.start()
        watchSyncRepository.start()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload = messageEvent.data.decodeToString()
        when (messageEvent.path) {
            WearPaths.WATCH_ACTION -> serviceScope.launch {
                runCatching {
                    watchSyncRepository.handleWatchAction(WearProtocol.decodeWatchAction(payload))
                }.onFailure { Log.e(WearPaths.LOG_TAG, "Phone failed to handle watch action", it) }
            }
            WearPaths.HEALTH_METRICS -> serviceScope.launch {
                runCatching {
                    watchHealthDataReceiver.receiveMetrics(WearProtocol.decodeHealthMetrics(payload))
                    watchSyncRepository.noteReceived(WearPaths.HEALTH_METRICS)
                }.onFailure { Log.e(WearPaths.LOG_TAG, "Phone failed to handle health metrics", it) }
            }
            WearPaths.MESSAGE_HEALTH_BATCH -> serviceScope.launch {
                runCatching {
                    watchHealthDataReceiver.receiveBatch(WearProtocol.decodeHealthBatch(payload))
                    watchSyncRepository.noteReceived(WearPaths.MESSAGE_HEALTH_BATCH)
                }.onFailure { Log.e(WearPaths.LOG_TAG, "Phone failed to handle legacy health batch", it) }
            }
            WearPaths.SYNC_STATE -> serviceScope.launch {
                runCatching {
                    watchSyncRepository.handleSyncState(WearProtocol.decodeSyncState(payload))
                }.onFailure { Log.e(WearPaths.LOG_TAG, "Phone failed to handle sync state", it) }
            }
            else -> {
                Log.d(WearPaths.LOG_TAG, "Phone ignored message path=${messageEvent.path}")
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        if (capabilityInfo.name == WearPaths.CAPABILITY_WATCH) {
            watchConnectionManager.onCapabilityChanged(capabilityInfo)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
