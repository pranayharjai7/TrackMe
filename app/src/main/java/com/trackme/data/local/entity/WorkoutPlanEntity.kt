package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.WorkoutPlan

@Entity(
    tableName = "workout_plans",
    indices = [
        Index(value = ["userId"], name = "idx_workout_plans_user"),
        Index(value = ["userId", "isActive", "deletedAt"], name = "idx_workout_plans_active"),
        Index(value = ["isSynced"], name = "idx_workout_plans_sync"),
    ],
)
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null,
) {
    fun toDomain() = WorkoutPlan(id, userId, name, isActive, createdAt, updatedAt)
}

fun WorkoutPlan.toEntity(isSynced: Boolean = false) =
    WorkoutPlanEntity(id, userId, name, isActive, createdAt, updatedAt, isSynced)
