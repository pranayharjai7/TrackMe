package com.trackme.wearable.phone

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.trackme.wearable.data.HealthMetricSampleStore
import com.trackme.wearable.health.WearHealthMetricCollector
import com.trackme.wearable.health.WearHealthSnapshot
import com.trackme.wearbridge.HealthMetricSamplePayload
import com.trackme.wearbridge.HealthMetricsPayload
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HealthMetricsSender(
    context: Context,
    private val connectionManager: PhoneConnectionManager,
    private val workoutStateSync: WorkoutStateSync,
) {
    private val appContext = context.applicationContext
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val sampleStore = HealthMetricSampleStore(appContext)
    private val collector = WearHealthMetricCollector(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    val snapshot: StateFlow<WearHealthSnapshot> = collector.snapshot

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            workoutStateSync.sessionState.collect { state ->
                val activeState = state
                if (activeState != null && activeState.sessionId.isNotBlank() && !activeState.isCompleted) {
                    collector.start(activeState.sessionId) {
                        if (workoutStateSync.sessionState.value?.restActive == true) 15_000L else 5_000L
                    }
                } else {
                    collector.stop()
                }
            }
        }
        scope.launch {
            collector.samples.collect { samples ->
                sampleStore.append(samples)
            }
        }
        scope.launch {
            while (true) {
                delay(30_000)
                flushNow()
            }
        }
    }

    suspend fun flushNow() {
        val sessionId = workoutStateSync.sessionState.value?.sessionId.orEmpty()
        if (sessionId.isBlank()) return
        val samples = sampleStore.drain()
        if (samples.isEmpty()) return
        val metrics = samples.toMetrics(sessionId)
        val sent = sendMetrics(metrics)
        if (!sent) {
            sampleStore.prepend(samples)
        }
    }

    private suspend fun sendMetrics(metrics: HealthMetricsPayload): Boolean {
        val node = connectionManager.currentPhoneNode()
        if (node == null) return false
        return runCatching {
            messageClient.sendMessage(
                node.id,
                WearPaths.HEALTH_METRICS,
                WearProtocol.encodeHealthMetrics(metrics).encodeToByteArray(),
            ).await()
            Log.d(WearPaths.LOG_TAG, "Watch sent health metrics ${metrics.metricId}")
            true
        }.getOrElse { error ->
            Log.e(WearPaths.LOG_TAG, "Watch health metrics send failed", error)
            false
        }
    }

    private fun List<HealthMetricSamplePayload>.toMetrics(sessionId: String): HealthMetricsPayload {
        fun latest(type: String): HealthMetricSamplePayload? =
            filter { it.metricType == type }.maxByOrNull { it.startTime }
        val timestamp = maxOfOrNull { it.startTime } ?: System.currentTimeMillis()
        return HealthMetricsPayload(
            metricId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            timestamp = timestamp,
            heartRate = latest("HEART_RATE")?.value,
            steps = latest("STEPS")?.value,
            calories = latest("ACTIVE_CALORIES")?.value,
            activeDurationSeconds = latest("DURATION")?.value?.toLong(),
        )
    }
}
