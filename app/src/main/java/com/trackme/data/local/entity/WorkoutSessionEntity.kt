package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.WorkoutSession

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dayId: String,
    val date: Long,
    val durationMinutes: Int,
    val notes: String,
    val updatedAt: Long,
    val isSynced: Boolean = false,
) {
    fun toDomain() = WorkoutSession(id, userId, dayId, date, durationMinutes, notes, updatedAt)
}

fun WorkoutSession.toEntity(isSynced: Boolean = false) =
    WorkoutSessionEntity(id, userId, dayId, date, durationMinutes, notes, updatedAt, isSynced)
