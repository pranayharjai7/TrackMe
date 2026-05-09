package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.SessionSet

@Entity(tableName = "session_sets")
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
) {
    fun toDomain() = SessionSet(id, sessionId, userId, exerciseId, setNumber, weightKg, reps, completed, updatedAt)
}

fun SessionSet.toEntity(isSynced: Boolean = false) =
    SessionSetEntity(id, sessionId, userId, exerciseId, setNumber, weightKg, reps, completed, updatedAt, isSynced)
