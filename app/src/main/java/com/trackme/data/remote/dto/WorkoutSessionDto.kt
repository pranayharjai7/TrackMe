package com.trackme.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("day_id") val dayId: String,
    val date: Long,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val notes: String,
    @SerialName("updated_at") val updatedAt: Long,
)
