package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_progress_snapshots",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["isSynced"])
    ]
)
data class ExerciseProgressSnapshotEntity(
    @PrimaryKey val id: String, // ${userId}_${exerciseId}
    val userId: String,
    val exerciseId: String,
    val exerciseName: String,
    val current1RM: Float,
    val projected1RM30Days: Float,
    val projected1RM90Days: Float,
    val projected1RM365Days: Float,
    val isPlateaued: Boolean,
    val improvementPercentage: Float,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null
)
