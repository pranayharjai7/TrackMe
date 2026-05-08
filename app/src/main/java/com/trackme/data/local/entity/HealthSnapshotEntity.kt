package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.HealthSnapshot

@Entity(tableName = "health_snapshots")
data class HealthSnapshotEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: Long,
    val weightKg: Float?,
    val heightCm: Float?,
    val bmi: Float?,
    val steps: Long?,
    val activeCaloriesBurned: Float?,
    val updatedAt: Long,
    val isSynced: Boolean = false,
) {
    fun toDomain() = HealthSnapshot(id, userId, date, weightKg, heightCm, bmi, steps, activeCaloriesBurned, updatedAt)
}

fun HealthSnapshot.toEntity(isSynced: Boolean = false) =
    HealthSnapshotEntity(id, userId, date, weightKg, heightCm, bmi, steps, activeCaloriesBurned, updatedAt, isSynced)
