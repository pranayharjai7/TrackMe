package com.trackme.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionSetDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("set_number") val setNumber: Int,
    @SerialName("weight_kg") val weightKg: Float,
    val reps: Int,
    val completed: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("distance_km") val distanceKm: Float? = null,
    @SerialName("speed_kmh") val speedKmh: Float? = null,
    @SerialName("incline_percent") val inclinePercent: Float? = null,
)
