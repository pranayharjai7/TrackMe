package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY date DESC")
    fun getAllForUser(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND date >= :fromDate ORDER BY date DESC")
    fun getForUserSince(userId: String, fromDate: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
