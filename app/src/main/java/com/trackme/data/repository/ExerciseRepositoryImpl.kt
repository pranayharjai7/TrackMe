package com.trackme.data.repository

import com.trackme.data.exercise.ExerciseAssetLoader
import com.trackme.data.local.dao.ExerciseDao
import com.trackme.domain.model.Exercise
import com.trackme.domain.repository.ExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val assetLoader: ExerciseAssetLoader,
) : ExerciseRepository {

    override suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (exerciseDao.count() == 0) {
            exerciseDao.insertAll(assetLoader.loadExercises())
        }
    }

    override fun search(query: String): Flow<List<Exercise>> =
        exerciseDao.search(query).map { list -> list.map { it.toDomain() } }

    override fun getByMuscle(muscle: String): Flow<List<Exercise>> =
        exerciseDao.getByMuscle(muscle).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Exercise? =
        exerciseDao.getById(id)?.toDomain()

    override suspend fun getByIds(ids: Collection<String>): Map<String, Exercise> {
        if (ids.isEmpty()) return emptyMap()
        return exerciseDao.getByIds(ids.distinct()).associate { entity ->
            entity.id to entity.toDomain()
        }
    }

    override fun getAllCategories(): Flow<List<String>> =
        exerciseDao.getAllCategories()
}
