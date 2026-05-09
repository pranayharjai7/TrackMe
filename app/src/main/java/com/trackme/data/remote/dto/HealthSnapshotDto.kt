package com.trackme.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthSnapshotDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: Long,
    @SerialName("weight_kg") val weightKg: Float?,
    @SerialName("height_cm") val heightCm: Float?,
    val bmi: Float?,
    val steps: Long?,
    @SerialName("active_calories_burned") val activeCaloriesBurned: Float?,
    @SerialName("updated_at") val updatedAt: Long,
)
