package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.PlannedExercise

@Entity(tableName = "planned_exercises")
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val userId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val updatedAt: Long,
    val isSynced: Boolean = false,
) {
    fun toDomain() = PlannedExercise(id, dayId, userId, exerciseId, orderIndex, updatedAt)
}

fun PlannedExercise.toEntity(isSynced: Boolean = false) =
    PlannedExerciseEntity(id, dayId, userId, exerciseId, orderIndex, updatedAt, isSynced)
