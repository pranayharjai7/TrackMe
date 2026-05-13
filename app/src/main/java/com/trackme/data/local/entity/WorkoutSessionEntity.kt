package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.WorkoutSession

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["userId"], name = "idx_workout_sessions_user"),
        Index(value = ["userId", "date", "deletedAt"], name = "idx_workout_sessions_user_date"),
        Index(value = ["userId", "dayId", "date", "durationMinutes", "deletedAt"], name = "idx_workout_sessions_in_progress"),
        Index(value = ["isSynced"], name = "idx_workout_sessions_sync"),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dayId: String,
    val date: Long,
    val durationMinutes: Int,
    val notes: String,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null,
) {
    fun toDomain() = WorkoutSession(id, userId, dayId, date, durationMinutes, notes, updatedAt)
}

fun WorkoutSession.toEntity(isSynced: Boolean = false) =
    WorkoutSessionEntity(id, userId, dayId, date, durationMinutes, notes, updatedAt, isSynced)
