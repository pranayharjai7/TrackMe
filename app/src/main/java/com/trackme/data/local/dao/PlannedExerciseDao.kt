package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.PlannedExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pe: PlannedExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlannedExerciseEntity>)

    @Query("SELECT * FROM planned_exercises WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE dayId = :dayId ORDER BY orderIndex")
    fun getForDay(dayId: String): Flow<List<PlannedExerciseEntity>>

    @Delete
    suspend fun delete(pe: PlannedExerciseEntity)

    @Query("SELECT * FROM planned_exercises WHERE isSynced = 0")
    suspend fun getUnsynced(): List<PlannedExerciseEntity>

    @Query("UPDATE planned_exercises SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
