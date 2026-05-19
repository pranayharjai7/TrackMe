package com.trackme.data.remote.dto

import com.trackme.data.local.entity.DailyHealthAnalyticsEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyHealthAnalyticsDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("date_millis") val dateMillis: Long,
    val bmr: Float,
    @SerialName("calories_steps") val caloriesSteps: Float,
    @SerialName("calories_active") val caloriesActive: Float,
    @SerialName("calories_lifting") val caloriesLifting: Float,
    @SerialName("calories_tdee") val caloriesTDEE: Float,
    val steps: Long,
    @SerialName("weight_kg") val weightKg: Float,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null
) {
    fun toEntity(): DailyHealthAnalyticsEntity = DailyHealthAnalyticsEntity(
        id = id,
        userId = userId,
        dateMillis = dateMillis,
        bmr = bmr,
        caloriesSteps = caloriesSteps,
        caloriesActive = caloriesActive,
        caloriesLifting = caloriesLifting,
        caloriesTDEE = caloriesTDEE,
        steps = steps,
        weightKg = weightKg,
        updatedAt = updatedAt,
        isSynced = true,
        deletedAt = deletedAt
    )
}

fun DailyHealthAnalyticsEntity.toDto(): DailyHealthAnalyticsDto = DailyHealthAnalyticsDto(
    id = id,
    userId = userId,
    dateMillis = dateMillis,
    bmr = bmr,
    caloriesSteps = caloriesSteps,
    caloriesActive = caloriesActive,
    caloriesLifting = caloriesLifting,
    caloriesTDEE = caloriesTDEE,
    steps = steps,
    weightKg = weightKg,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
