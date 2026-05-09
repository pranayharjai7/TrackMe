package com.trackme.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlannedExerciseDto(
    val id: String,
    @SerialName("day_id") val dayId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("target_sets") val targetSets: Int = 3,
    @SerialName("target_reps") val targetReps: Int? = null,
    @SerialName("target_weight_kg") val targetWeightKg: Float? = null,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Int? = null,
    @SerialName("target_distance_km") val targetDistanceKm: Float? = null,
    @SerialName("target_speed_kmh") val targetSpeedKmh: Float? = null,
    @SerialName("target_incline") val targetIncline: Float? = null,
)
