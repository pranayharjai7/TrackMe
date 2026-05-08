package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay

@Entity(tableName = "workout_days")
data class WorkoutDayEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val userId: String,
    val dayOfWeek: String,   // DayOfWeek.name
    val name: String,
    val updatedAt: Long,
    val isSynced: Boolean = false,
) {
    fun toDomain() = WorkoutDay(id, planId, userId, DayOfWeek.valueOf(dayOfWeek), name, updatedAt)
}

fun WorkoutDay.toEntity(isSynced: Boolean = false) =
    WorkoutDayEntity(id, planId, userId, dayOfWeek.name, name, updatedAt, isSynced)
