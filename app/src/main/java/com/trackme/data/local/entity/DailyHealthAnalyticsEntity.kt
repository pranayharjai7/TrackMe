package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_health_analytics",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["isSynced"])
    ]
)
data class DailyHealthAnalyticsEntity(
    @PrimaryKey val id: String, // ${userId}_${dateMillis}
    val userId: String,
    val dateMillis: Long,
    val bmr: Float,
    val caloriesSteps: Float,
    val caloriesActive: Float,
    val caloriesLifting: Float,
    val caloriesTDEE: Float,
    val steps: Long,
    val weightKg: Float,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null
)
