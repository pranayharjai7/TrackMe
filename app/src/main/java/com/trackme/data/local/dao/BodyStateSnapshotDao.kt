package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.BodyStateSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyStateSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BodyStateSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BodyStateSnapshotEntity>)

    @Query("SELECT * FROM body_state_snapshots WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    fun getForUser(userId: String): Flow<BodyStateSnapshotEntity?>

    @Query("SELECT * FROM body_state_snapshots WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getForUserSync(userId: String): BodyStateSnapshotEntity?

    @Query("SELECT * FROM body_state_snapshots WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<BodyStateSnapshotEntity>

    @Query("UPDATE body_state_snapshots SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(entity: BodyStateSnapshotEntity)

    @Query("DELETE FROM body_state_snapshots")
    suspend fun deleteAll()
}
