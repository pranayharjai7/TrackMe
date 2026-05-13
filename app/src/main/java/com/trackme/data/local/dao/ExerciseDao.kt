package com.trackme.data.local.dao

import androidx.room.*
import com.trackme.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' OR primaryMuscles LIKE '%' || :query || '%' ORDER BY name LIMIT 100")
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE primaryMuscles LIKE '%' || :muscle || '%' OR secondaryMuscles LIKE '%' || :muscle || '%' ORDER BY name LIMIT 100")
    fun getByMuscle(muscle: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT DISTINCT category FROM exercises ORDER BY category")
    fun getAllCategories(): Flow<List<String>>
}
