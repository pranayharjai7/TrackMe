package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.SessionSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: SessionSetEntity)

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    fun getSetsForSession(sessionId: String): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets WHERE userId = :userId AND exerciseId = :exerciseId ORDER BY updatedAt DESC LIMIT 50")
    fun getHistoryForExercise(userId: String, exerciseId: String): Flow<List<SessionSetEntity>>

    @Query("SELECT MAX(weightKg) FROM session_sets WHERE userId = :userId AND exerciseId = :exerciseId AND completed = 1")
    suspend fun getMaxWeightForExercise(userId: String, exerciseId: String): Float?

    @Query("SELECT * FROM session_sets WHERE isSynced = 0")
    suspend fun getUnsynced(): List<SessionSetEntity>

    @Query("UPDATE session_sets SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(set: SessionSetEntity)
}
