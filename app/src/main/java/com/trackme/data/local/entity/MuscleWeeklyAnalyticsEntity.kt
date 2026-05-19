package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "muscle_weekly_analytics",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["isSynced"])
    ]
)
data class MuscleWeeklyAnalyticsEntity(
    @PrimaryKey val id: String, // ${userId}_${muscleGroup}_${weekOffset}
    val userId: String,
    val muscleGroup: String,
    val weekOffset: Int,
    val weeklyStimulus: Float,
    val growthIndex: Float,
    val fatigue: Float,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null
)
