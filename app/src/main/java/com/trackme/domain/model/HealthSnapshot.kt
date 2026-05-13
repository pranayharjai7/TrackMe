package com.trackme.domain.model

data class HealthSnapshot(
    val id: String,
    val userId: String,
    val date: Long,
    val weightKg: Float?,
    val heightCm: Float?,
    val bmi: Float?,
    val steps: Long?,
    val activeCaloriesBurned: Float?,
    val heartRateAvg: Int?,
    val updatedAt: Long,
)
