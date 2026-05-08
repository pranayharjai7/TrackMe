package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pr: PersonalRecordEntity)

    @Query("SELECT * FROM personal_records WHERE userId = :userId ORDER BY achievedAt DESC")
    fun getAllForUser(userId: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE userId = :userId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun getForExercise(userId: String, exerciseId: String): PersonalRecordEntity?

    @Delete
    suspend fun delete(pr: PersonalRecordEntity)
}
