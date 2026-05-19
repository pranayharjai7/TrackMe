package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.ExerciseProgressSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseProgressSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExerciseProgressSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExerciseProgressSnapshotEntity>)

    @Query("SELECT * FROM exercise_progress_snapshots WHERE userId = :userId AND deletedAt IS NULL")
    fun getAllForUser(userId: String): Flow<List<ExerciseProgressSnapshotEntity>>

    @Query("SELECT * FROM exercise_progress_snapshots WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<ExerciseProgressSnapshotEntity>

    @Query("UPDATE exercise_progress_snapshots SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(entity: ExerciseProgressSnapshotEntity)

    @Query("DELETE FROM exercise_progress_snapshots")
    suspend fun deleteAll()
}
