package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_deletions")
data class PendingDeletionEntity(
    @PrimaryKey val entityId: String,
    val userId: String,
    val tableName: String,
    val deletedAt: Long,
)
