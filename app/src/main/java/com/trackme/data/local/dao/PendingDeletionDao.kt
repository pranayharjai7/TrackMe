package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.PendingDeletionEntity

@Dao
interface PendingDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingDeletionEntity)

    @Query("SELECT * FROM pending_deletions WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<PendingDeletionEntity>

    @Query("DELETE FROM pending_deletions WHERE entityId = :entityId")
    suspend fun deleteById(entityId: String)
}
