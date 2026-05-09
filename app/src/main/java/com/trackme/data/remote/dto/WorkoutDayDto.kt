package com.trackme.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutDayDto(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("day_of_week") val dayOfWeek: String,
    val name: String,
    @SerialName("updated_at") val updatedAt: Long,
)
