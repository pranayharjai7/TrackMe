package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: WorkoutPlanEntity)

    @Query("SELECT * FROM workout_plans WHERE userId = :userId")
    fun getAllForUser(userId: String): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun getActivePlan(userId: String): Flow<WorkoutPlanEntity?>

    @Query("UPDATE workout_plans SET isActive = 0 WHERE userId = :userId")
    suspend fun clearActivePlan(userId: String)

    @Query("UPDATE workout_plans SET isActive = 1, isSynced = 0 WHERE id = :planId")
    suspend fun setActivePlan(planId: String)

    @Delete
    suspend fun delete(plan: WorkoutPlanEntity)

    @Query("SELECT * FROM workout_plans WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutPlanEntity>

    @Query("UPDATE workout_plans SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
