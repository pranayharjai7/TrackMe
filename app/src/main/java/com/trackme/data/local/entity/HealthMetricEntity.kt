package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.HealthMetric

@Entity(
    tableName = "health_metrics",
    indices = [
        Index(value = ["userId", "startTime"], name = "idx_health_metrics_user_time"),
        Index(value = ["userId", "category"], name = "idx_health_metrics_user_category"),
        Index(value = ["recordType"], name = "idx_health_metrics_record_type"),
    ],
)
data class HealthMetricEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String,
    val recordType: String,
    val displayName: String,
    val startTime: Long,
    val endTime: Long?,
    val primaryValue: String,
    val primaryUnit: String?,
    val details: String,
    val sourceApp: String?,
    val rawData: String?,
    val updatedAt: Long,
) {
    fun toDomain() = HealthMetric(
        id = id,
        userId = userId,
        category = category,
        recordType = recordType,
        displayName = displayName,
        startTime = startTime,
        endTime = endTime,
        primaryValue = primaryValue,
        primaryUnit = primaryUnit,
        details = details.lines().filter { it.isNotBlank() },
        sourceApp = sourceApp,
        rawData = rawData,
        updatedAt = updatedAt,
    )
}
