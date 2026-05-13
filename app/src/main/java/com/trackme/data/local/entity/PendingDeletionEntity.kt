package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_deletions",
    indices = [
        Index(value = ["userId"], name = "idx_pending_deletions_user"),
    ],
)
data class PendingDeletionEntity(
    @PrimaryKey val entityId: String,
    val userId: String,
    val tableName: String,
    val deletedAt: Long,
)
