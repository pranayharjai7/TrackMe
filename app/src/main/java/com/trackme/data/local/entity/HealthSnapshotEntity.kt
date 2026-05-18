package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.HealthSnapshot

@Entity(
    tableName = "health_snapshots",
    indices = [
        Index(value = ["userId", "date"], name = "idx_health_snapshots_user_date"),
        Index(value = ["isSynced"], name = "idx_health_snapshots_sync"),
    ],
)
data class HealthSnapshotEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: Long,
    val weightKg: Float?,
    val heightCm: Float?,
    val bmi: Float?,
    val steps: Long?,
    val activeCaloriesBurned: Float?,
    val heartRateAvg: Int?,
    val hrvRmssd: Float?,
    val restingHeartRate: Int?,
    val sleepDurationMinutes: Int?,
    val deepSleepMinutes: Int?,
    val updatedAt: Long,
    val isSynced: Boolean = false,
) {
    fun toDomain() = HealthSnapshot(id, userId, date, weightKg, heightCm, bmi, steps, activeCaloriesBurned, heartRateAvg, hrvRmssd, restingHeartRate, sleepDurationMinutes, deepSleepMinutes, updatedAt)
}

fun HealthSnapshot.toEntity(isSynced: Boolean = false) =
    HealthSnapshotEntity(id, userId, date, weightKg, heightCm, bmi, steps, activeCaloriesBurned, heartRateAvg, hrvRmssd, restingHeartRate, sleepDurationMinutes, deepSleepMinutes, updatedAt, isSynced)
