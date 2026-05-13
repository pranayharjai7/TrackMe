package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.PlannedExercise

@Entity(
    tableName = "planned_exercises",
    indices = [
        Index(value = ["userId", "deletedAt"], name = "idx_planned_exercises_user"),
        Index(value = ["dayId", "deletedAt", "orderIndex"], name = "idx_planned_exercises_day"),
        Index(value = ["exerciseId"], name = "idx_planned_exercises_exercise"),
        Index(value = ["isSynced"], name = "idx_planned_exercises_sync"),
    ],
)
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val userId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null,
    val targetSets: Int = 3,
    val targetReps: Int? = null,
    val targetWeightKg: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Float? = null,
    val targetSpeedKmh: Float? = null,
    val targetIncline: Float? = null,
) {
    fun toDomain() = PlannedExercise(
        id, dayId, userId, exerciseId, orderIndex, updatedAt,
        targetSets, targetReps, targetWeightKg,
        targetDurationSeconds, targetDistanceKm, targetSpeedKmh, targetIncline,
    )
}

fun PlannedExercise.toEntity(isSynced: Boolean = false) = PlannedExerciseEntity(
    id, dayId, userId, exerciseId, orderIndex, updatedAt, isSynced,
    deletedAt = null,
    targetSets, targetReps, targetWeightKg,
    targetDurationSeconds, targetDistanceKm, targetSpeedKmh, targetIncline,
)
