package com.trackme.domain.usecase

import com.trackme.domain.model.Exercise
import com.trackme.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchExercisesUseCase @Inject constructor(
    private val repository: ExerciseRepository,
) {
    operator fun invoke(query: String): Flow<List<Exercise>> =
        repository.search(query.trim())
}
