package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.WorkoutDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(day: WorkoutDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<WorkoutDayEntity>)

    @Query("SELECT * FROM workout_days WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForUser(userId: String): List<WorkoutDayEntity>

    @Query("SELECT * FROM workout_days WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<WorkoutDayEntity>

    @Query("SELECT * FROM workout_days WHERE planId = :planId AND deletedAt IS NULL ORDER BY dayOfWeek")
    fun getDaysForPlan(planId: String): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_days WHERE planId = :planId AND dayOfWeek = :dayOfWeek AND deletedAt IS NULL LIMIT 1")
    suspend fun getDayByWeekday(planId: String, dayOfWeek: String): WorkoutDayEntity?

    @Query("UPDATE workout_days SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("SELECT * FROM workout_days WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutDayEntity>

    @Query("UPDATE workout_days SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
