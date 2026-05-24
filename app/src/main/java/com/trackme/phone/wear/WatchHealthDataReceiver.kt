package com.trackme.phone.wear

import android.util.Log
import com.trackme.domain.model.HealthMetric
import com.trackme.domain.repository.HealthRepository
import com.trackme.wearbridge.HealthMetricBatchPayload
import com.trackme.wearbridge.HealthMetricSamplePayload
import com.trackme.wearbridge.HealthMetricsPayload
import com.trackme.wearbridge.WearPaths
import com.trackme.wearbridge.WearProtocol
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHealthDataReceiver @Inject constructor(
    private val healthRepository: HealthRepository,
    private val supabase: SupabaseClient,
    private val watchSyncRepository: WatchSyncRepository,
) {
    private val userId: String
        get() = runCatching { supabase.auth.currentSessionOrNull()?.user?.id }.getOrNull().orEmpty()

    suspend fun receiveMetrics(metrics: HealthMetricsPayload) {
        val user = userId
        if (user.isBlank()) return
        val samples = buildList {
            metrics.heartRate?.let { add(HealthMetricSamplePayload("HEART_RATE", it, "bpm", metrics.timestamp, metrics.timestamp)) }
            metrics.steps?.let { add(HealthMetricSamplePayload("STEPS", it, "count", metrics.timestamp, metrics.timestamp)) }
            metrics.calories?.let { add(HealthMetricSamplePayload("ACTIVE_CALORIES", it, "kcal", metrics.timestamp, metrics.timestamp)) }
            metrics.activeDurationSeconds?.let { add(HealthMetricSamplePayload("DURATION", it.toDouble(), "s", metrics.timestamp, metrics.timestamp)) }
        }
        persist(
            userId = user,
            sessionId = metrics.sessionId,
            batchId = metrics.metricId,
            createdAt = metrics.timestamp,
            rawData = WearProtocol.encodeHealthMetrics(metrics),
            samples = samples,
        )
        watchSyncRepository.noteWatchHealth(metrics)
        Log.d(WearPaths.LOG_TAG, "Phone received health metrics id=${metrics.metricId} samples=${samples.size}")
    }

    suspend fun receiveBatch(batch: HealthMetricBatchPayload) {
        val user = userId
        if (user.isBlank()) return
        persist(
            userId = user,
            sessionId = batch.sessionId,
            batchId = batch.batchId,
            createdAt = batch.createdAt,
            rawData = WearProtocol.encodeHealthBatch(batch),
            samples = batch.samples,
        )
        Log.d(WearPaths.LOG_TAG, "Phone received legacy health batch id=${batch.batchId} samples=${batch.samples.size}")
    }

    private suspend fun persist(
        userId: String,
        sessionId: String,
        batchId: String,
        createdAt: Long,
        rawData: String,
        samples: List<HealthMetricSamplePayload>,
    ) {
        if (samples.isEmpty()) return
        healthRepository.saveMetrics(
            samples.mapIndexed { index, sample ->
                sample.toHealthMetric(
                    id = "wear-$batchId-$index-${sample.metricType}-${sample.startTime}",
                    userId = userId,
                    sessionId = sessionId,
                    updatedAt = createdAt,
                    rawData = rawData,
                )
            }
        )
    }

    private fun HealthMetricSamplePayload.toHealthMetric(
        id: String,
        userId: String,
        sessionId: String,
        updatedAt: Long,
        rawData: String,
    ): HealthMetric {
        val normalizedType = metricType.uppercase(Locale.US)
        return HealthMetric(
            id = id,
            userId = userId,
            category = "Wear Workout",
            recordType = "wear_${normalizedType.lowercase(Locale.US)}",
            displayName = normalizedType.lowercase(Locale.US)
                .split("_")
                .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.US) } },
            startTime = startTime,
            endTime = endTime,
            primaryValue = value.toString(),
            primaryUnit = unit,
            details = listOf("sessionId=$sessionId", "source=$source"),
            sourceApp = source,
            rawData = rawData,
            updatedAt = updatedAt,
        )
    }
}
