package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.WorkoutDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(day: WorkoutDayEntity)

    @Query("SELECT * FROM workout_days WHERE planId = :planId ORDER BY dayOfWeek")
    fun getDaysForPlan(planId: String): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_days WHERE planId = :planId AND dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getDayByWeekday(planId: String, dayOfWeek: String): WorkoutDayEntity?

    @Delete
    suspend fun delete(day: WorkoutDayEntity)

    @Query("SELECT * FROM workout_days WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutDayEntity>

    @Query("UPDATE workout_days SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
