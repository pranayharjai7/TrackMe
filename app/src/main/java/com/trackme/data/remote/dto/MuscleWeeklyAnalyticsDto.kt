package com.trackme.data.remote.dto

import com.trackme.data.local.entity.MuscleWeeklyAnalyticsEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MuscleWeeklyAnalyticsDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("muscle_group") val muscleGroup: String,
    @SerialName("week_offset") val weekOffset: Int,
    @SerialName("weekly_stimulus") val weeklyStimulus: Float,
    @SerialName("growth_index") val growthIndex: Float,
    val fatigue: Float,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null
) {
    fun toEntity(): MuscleWeeklyAnalyticsEntity = MuscleWeeklyAnalyticsEntity(
        id = id,
        userId = userId,
        muscleGroup = muscleGroup,
        weekOffset = weekOffset,
        weeklyStimulus = weeklyStimulus,
        growthIndex = growthIndex,
        fatigue = fatigue,
        updatedAt = updatedAt,
        isSynced = true,
        deletedAt = deletedAt
    )
}

fun MuscleWeeklyAnalyticsEntity.toDto(): MuscleWeeklyAnalyticsDto = MuscleWeeklyAnalyticsDto(
    id = id,
    userId = userId,
    muscleGroup = muscleGroup,
    weekOffset = weekOffset,
    weeklyStimulus = weeklyStimulus,
    growthIndex = growthIndex,
    fatigue = fatigue,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
