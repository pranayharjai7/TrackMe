package com.trackme.wearable.phone

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.trackme.wearable.TrackMeWearApplication
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PhoneMessageReceiverService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val runtime by lazy {
        (application as TrackMeWearApplication).runtime
    }

    override fun onCreate() {
        super.onCreate()
        runtime.start()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload = messageEvent.data.decodeToString()
        when (messageEvent.path) {
            WearPaths.WORKOUT_START,
            WearPaths.WORKOUT_NEXT_SET,
            WearPaths.WORKOUT_COMPLETE_SET,
            WearPaths.WORKOUT_REST_START,
            WearPaths.WORKOUT_END -> runCatching {
                runtime.workoutStateSync.handleWorkoutState(WearProtocol.decodeWorkoutState(payload))
            }.onFailure { Log.e(WearPaths.LOG_TAG, "Watch failed to handle workout state", it) }
            WearPaths.SYNC_STATE -> runCatching {
                runtime.workoutStateSync.handleSyncState(WearProtocol.decodeSyncState(payload))
            }.onFailure { Log.e(WearPaths.LOG_TAG, "Watch failed to handle sync state", it) }
            WearPaths.SYNC_EVENTS -> runCatching {
                runtime.workoutStateSync.handleSyncEvents(WearProtocol.decodeSyncEvents(payload))
            }.onFailure { Log.e(WearPaths.LOG_TAG, "Watch failed to handle sync events", it) }
            else -> Log.d(WearPaths.LOG_TAG, "Watch ignored message path=${messageEvent.path}")
        }
        serviceScope.launch {
            runtime.healthMetricsSender.flushNow()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        runtime.workoutStateSync.onDataChanged(dataEvents)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        if (capabilityInfo.name == WearPaths.CAPABILITY_PHONE) {
            runtime.phoneConnectionManager.onCapabilityChanged(capabilityInfo)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
