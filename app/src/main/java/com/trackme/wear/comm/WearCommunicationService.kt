package com.trackme.wear.comm

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.trackme.wear.session.SessionStatePublisher
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
class WearCommunicationService : WearableListenerService() {
    @Inject lateinit var commandHandler: WearCommandHandler
    @Inject lateinit var sessionStatePublisher: SessionStatePublisher
    @Inject lateinit var watchCapabilityManager: WatchCapabilityManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        sessionStatePublisher.start()
        watchCapabilityManager.start()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearPaths.MESSAGE_COMMAND -> serviceScope.launch {
                runCatching {
                    commandHandler.handleCommand(
                        WearProtocol.decodeCommand(messageEvent.data.decodeToString())
                    )
                }.onFailure { Log.e("WearCommunication", "Failed to handle Wear command", it) }
            }
            WearPaths.MESSAGE_HEALTH_BATCH -> serviceScope.launch {
                runCatching {
                    commandHandler.handleHealthBatch(
                        WearProtocol.decodeHealthBatch(messageEvent.data.decodeToString())
                    )
                }.onFailure { Log.e("WearCommunication", "Failed to ingest Wear health batch", it) }
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        sessionStatePublisher.start()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
