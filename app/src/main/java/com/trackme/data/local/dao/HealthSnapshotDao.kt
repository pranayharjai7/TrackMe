package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.HealthSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: HealthSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<HealthSnapshotEntity>)

    @Query("SELECT * FROM health_snapshots WHERE userId = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(userId: String): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots WHERE userId = :userId AND date >= :fromDate ORDER BY date")
    fun getSince(userId: String, fromDate: Long): Flow<List<HealthSnapshotEntity>>

    @Query("SELECT * FROM health_snapshots WHERE isSynced = 0")
    suspend fun getUnsynced(): List<HealthSnapshotEntity>

    @Query("UPDATE health_snapshots SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
