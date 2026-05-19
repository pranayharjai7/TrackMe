package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.MuscleWeeklyAnalyticsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MuscleWeeklyAnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MuscleWeeklyAnalyticsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MuscleWeeklyAnalyticsEntity>)

    @Query("SELECT * FROM muscle_weekly_analytics WHERE userId = :userId AND deletedAt IS NULL")
    fun getAllForUser(userId: String): Flow<List<MuscleWeeklyAnalyticsEntity>>

    @Query("SELECT * FROM muscle_weekly_analytics WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<MuscleWeeklyAnalyticsEntity>

    @Query("UPDATE muscle_weekly_analytics SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(entity: MuscleWeeklyAnalyticsEntity)

    @Query("DELETE FROM muscle_weekly_analytics")
    suspend fun deleteAll()
}
