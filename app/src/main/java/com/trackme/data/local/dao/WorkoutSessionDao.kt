package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<WorkoutSessionEntity>)

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForUserOnce(userId: String): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND deletedAt IS NULL ORDER BY date DESC")
    fun getAllForUser(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND date >= :fromDate AND deletedAt IS NULL ORDER BY date DESC")
    fun getForUserSince(userId: String, fromDate: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE workout_sessions SET durationMinutes = :durationMinutes, isSynced = 0 WHERE id = :sessionId")
    suspend fun updateDuration(sessionId: String, durationMinutes: Int)

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND date >= :todayStart AND durationMinutes = 0 AND deletedAt IS NULL LIMIT 1")
    suspend fun getInProgressSession(userId: String, todayStart: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND dayId = :dayId AND date >= :todayStart AND durationMinutes = 0 AND deletedAt IS NULL LIMIT 1")
    suspend fun getInProgressSessionForDay(userId: String, dayId: String, todayStart: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND dayId = :dayId AND date >= :todayStart AND date < :todayStart + 86400000 AND deletedAt IS NULL ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSessionForDay(userId: String, dayId: String, todayStart: Long): WorkoutSessionEntity?

    @Query("UPDATE workout_sessions SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()
}
