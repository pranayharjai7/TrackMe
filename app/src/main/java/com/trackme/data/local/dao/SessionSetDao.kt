package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.SessionSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: SessionSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<SessionSetEntity>)

    @Query("SELECT * FROM session_sets WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForUser(userId: String): List<SessionSetEntity>

    @Query("SELECT * FROM session_sets WHERE userId = :userId")
    suspend fun getAllForSync(userId: String): List<SessionSetEntity>

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY exerciseId, setNumber")
    fun getSetsForSession(sessionId: String): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets WHERE userId = :userId AND exerciseId = :exerciseId AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 50")
    fun getHistoryForExercise(userId: String, exerciseId: String): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets WHERE userId = :userId AND updatedAt >= :fromDate AND completed = 1 AND deletedAt IS NULL")
    fun getSetsSince(userId: String, fromDate: Long): Flow<List<SessionSetEntity>>

    @Query("SELECT MAX(weightKg) FROM session_sets WHERE userId = :userId AND exerciseId = :exerciseId AND completed = 1 AND deletedAt IS NULL")
    suspend fun getMaxWeightForExercise(userId: String, exerciseId: String): Float?

    @Query("SELECT * FROM session_sets WHERE isSynced = 0")
    suspend fun getUnsynced(): List<SessionSetEntity>

    @Query("UPDATE session_sets SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE session_sets SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}
