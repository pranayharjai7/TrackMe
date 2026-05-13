package com.trackme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.trackme.data.local.entity.HealthMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<HealthMetricEntity>)

    @Query("SELECT * FROM health_metrics WHERE userId = :userId ORDER BY startTime DESC, displayName")
    fun getAllForUser(userId: String): Flow<List<HealthMetricEntity>>

    @Query("DELETE FROM health_metrics WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query("DELETE FROM health_metrics")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceForUser(userId: String, metrics: List<HealthMetricEntity>) {
        deleteForUser(userId)
        if (metrics.isNotEmpty()) {
            insertAll(metrics)
        }
    }
}
