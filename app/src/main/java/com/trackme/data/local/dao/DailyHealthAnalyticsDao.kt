package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.DailyHealthAnalyticsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHealthAnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DailyHealthAnalyticsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DailyHealthAnalyticsEntity>)

    @Query("SELECT * FROM daily_health_analytics WHERE userId = :userId AND deletedAt IS NULL ORDER BY dateMillis DESC")
    fun getAllForUser(userId: String): Flow<List<DailyHealthAnalyticsEntity>>

    @Query("SELECT * FROM daily_health_analytics WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<DailyHealthAnalyticsEntity>

    @Query("UPDATE daily_health_analytics SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(entity: DailyHealthAnalyticsEntity)

    @Query("DELETE FROM daily_health_analytics")
    suspend fun deleteAll()
}
