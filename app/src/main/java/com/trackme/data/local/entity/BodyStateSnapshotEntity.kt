package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "body_state_snapshots",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["isSynced"])
    ]
)
data class BodyStateSnapshotEntity(
    @PrimaryKey val id: String, // ${userId}
    val userId: String,
    val consistencyScore: Float,
    val readinessScore: Int,
    val muscleBalancePushPull: Float,
    val muscleBalanceQuadHam: Float,
    val muscleBalanceUpperLower: Float,
    val updatedAt: Long,
    val isSynced: Boolean = false,
    val deletedAt: Long? = null
)
