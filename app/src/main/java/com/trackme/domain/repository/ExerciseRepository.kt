package com.trackme.domain.repository

import com.trackme.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    suspend fun seedIfEmpty()
    fun search(query: String): Flow<List<Exercise>>
    fun getByMuscle(muscle: String): Flow<List<Exercise>>
    suspend fun getById(id: String): Exercise?
    suspend fun getByIds(ids: Collection<String>): Map<String, Exercise>
    fun getAllCategories(): Flow<List<String>>
}
