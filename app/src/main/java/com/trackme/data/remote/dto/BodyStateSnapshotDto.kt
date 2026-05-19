package com.trackme.data.remote.dto

import com.trackme.data.local.entity.BodyStateSnapshotEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BodyStateSnapshotDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("consistency_score") val consistencyScore: Float,
    @SerialName("readiness_score") val readinessScore: Int,
    @SerialName("muscle_balance_push_pull") val muscleBalancePushPull: Float,
    @SerialName("muscle_balance_quad_ham") val muscleBalanceQuadHam: Float,
    @SerialName("muscle_balance_upper_lower") val muscleBalanceUpperLower: Float,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null
) {
    fun toEntity(): BodyStateSnapshotEntity = BodyStateSnapshotEntity(
        id = id,
        userId = userId,
        consistencyScore = consistencyScore,
        readinessScore = readinessScore,
        muscleBalancePushPull = muscleBalancePushPull,
        muscleBalanceQuadHam = muscleBalanceQuadHam,
        muscleBalanceUpperLower = muscleBalanceUpperLower,
        updatedAt = updatedAt,
        isSynced = true,
        deletedAt = deletedAt
    )
}

fun BodyStateSnapshotEntity.toDto(): BodyStateSnapshotDto = BodyStateSnapshotDto(
    id = id,
    userId = userId,
    consistencyScore = consistencyScore,
    readinessScore = readinessScore,
    muscleBalancePushPull = muscleBalancePushPull,
    muscleBalanceQuadHam = muscleBalanceQuadHam,
    muscleBalanceUpperLower = muscleBalanceUpperLower,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
