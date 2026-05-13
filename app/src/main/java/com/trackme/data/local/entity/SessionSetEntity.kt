package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.SessionSet

@Entity(
    tableName = "session_sets",
    indices = [
        Index(value = ["userId"], name = "idx_session_sets_user"),
        Index(value = ["sessionId", "deletedAt", "exerciseId", "setNumber"], name = "idx_session_sets_session"),
        Index(value = ["userId", "exerciseId", "deletedAt", "updatedAt"], name = "idx_session_sets_history"),
        Index(value = ["userId", "updatedAt", "completed", "deletedAt"], name = "idx_session_sets_since"),
        Index(value = ["isSynced"], name = "idx_session_sets_sync"),
    ],
)
data class SessionSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val userId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val completed: Boolean,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null,
    val durationSeconds: Int? = null,
    val distanceKm: Float? = null,
    val speedKmh: Float? = null,
    val inclinePercent: Float? = null,
) {
    fun toDomain() = SessionSet(
        id, sessionId, userId, exerciseId, setNumber, weightKg, reps, completed, updatedAt,
        durationSeconds, distanceKm, speedKmh, inclinePercent,
    )
}

fun SessionSet.toEntity(isSynced: Boolean = false) = SessionSetEntity(
    id, sessionId, userId, exerciseId, setNumber, weightKg, reps, completed, updatedAt, isSynced,
    deletedAt = null,
    durationSeconds, distanceKm, speedKmh, inclinePercent,
)
