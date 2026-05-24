package com.trackme.wear.health

import com.trackme.domain.model.HealthMetric
import com.trackme.domain.repository.HealthRepository
import com.trackme.wearbridge.HealthMetricBatchPayload
import com.trackme.wearbridge.HealthMetricSamplePayload
import com.trackme.wearbridge.WearProtocol
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearMetricIngestor @Inject constructor(
    private val healthRepository: HealthRepository,
) {
    suspend fun ingest(userId: String, batch: HealthMetricBatchPayload) {
        if (userId.isBlank() || batch.samples.isEmpty()) return
        val now = System.currentTimeMillis()
        val metrics = batch.samples.mapIndexed { index, sample ->
            sample.toHealthMetric(
                id = "wear-${batch.batchId}-$index-${sample.metricType}-${sample.startTime}",
                userId = userId,
                sessionId = batch.sessionId,
                updatedAt = now,
                rawData = WearProtocol.encodeHealthBatch(batch),
            )
        }
        healthRepository.saveMetrics(metrics)
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
            displayName = normalizedType.toDisplayName(),
            startTime = startTime,
            endTime = endTime,
            primaryValue = value.toString(),
            primaryUnit = unit,
            details = listOf(
                "sessionId=$sessionId",
                "source=$source",
            ),
            sourceApp = source,
            rawData = rawData,
            updatedAt = updatedAt,
        )
    }

    private fun String.toDisplayName(): String =
        lowercase(Locale.US)
            .split("_")
            .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } }
}
