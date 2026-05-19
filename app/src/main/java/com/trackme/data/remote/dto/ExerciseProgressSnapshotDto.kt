package com.trackme.data.remote.dto

import com.trackme.data.local.entity.ExerciseProgressSnapshotEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseProgressSnapshotDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("current_1rm") val current1RM: Float,
    @SerialName("projected_1rm_30days") val projected1RM30Days: Float,
    @SerialName("projected_1rm_90days") val projected1RM90Days: Float,
    @SerialName("projected_1rm_365days") val projected1RM365Days: Float,
    @SerialName("is_plateaued") val isPlateaued: Boolean,
    @SerialName("improvement_percentage") val improvementPercentage: Float,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null
) {
    fun toEntity(): ExerciseProgressSnapshotEntity = ExerciseProgressSnapshotEntity(
        id = id,
        userId = userId,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        current1RM = current1RM,
        projected1RM30Days = projected1RM30Days,
        projected1RM90Days = projected1RM90Days,
        projected1RM365Days = projected1RM365Days,
        isPlateaued = isPlateaued,
        improvementPercentage = improvementPercentage,
        updatedAt = updatedAt,
        isSynced = true,
        deletedAt = deletedAt
    )
}

fun ExerciseProgressSnapshotEntity.toDto(): ExerciseProgressSnapshotDto = ExerciseProgressSnapshotDto(
    id = id,
    userId = userId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    current1RM = current1RM,
    projected1RM30Days = projected1RM30Days,
    projected1RM90Days = projected1RM90Days,
    projected1RM365Days = projected1RM365Days,
    isPlateaued = isPlateaued,
    improvementPercentage = improvementPercentage,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
