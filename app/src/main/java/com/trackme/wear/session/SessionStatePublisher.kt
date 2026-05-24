package com.trackme.wear.session

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.trackme.ui.workout.session.WorkoutSessionManager
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class SessionStatePublisher @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionManager: WorkoutSessionManager,
) {
    private val dataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            sessionManager.uiState.collectLatest { state ->
                publish(state.toWearPayload())
            }
        }
    }

    suspend fun publishNow() {
        publish(sessionManager.uiState.value.toWearPayload())
    }

    private suspend fun publish(payload: com.trackme.wearbridge.SessionStatePayload) {
        runCatching {
            val request = PutDataMapRequest.create(WearPaths.DATA_SESSION_STATE).apply {
                dataMap.putString(WearPaths.KEY_PAYLOAD, WearProtocol.encodeSessionState(payload))
                dataMap.putLong(WearPaths.KEY_UPDATED_AT, payload.updatedAt)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }.onFailure { error ->
            Log.w("SessionStatePublisher", "Unable to publish Wear session state", error)
        }
    }
}
