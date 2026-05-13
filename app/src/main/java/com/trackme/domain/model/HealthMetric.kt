package com.trackme.domain.model

data class HealthMetric(
    val id: String,
    val userId: String,
    val category: String,
    val recordType: String,
    val displayName: String,
    val startTime: Long,
    val endTime: Long?,
    val primaryValue: String,
    val primaryUnit: String?,
    val details: List<String>,
    val sourceApp: String?,
    val rawData: String?,
    val updatedAt: Long,
)
