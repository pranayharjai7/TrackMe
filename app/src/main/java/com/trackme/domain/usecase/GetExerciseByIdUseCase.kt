package com.trackme.domain.usecase

import com.trackme.domain.model.Exercise
import com.trackme.domain.repository.ExerciseRepository
import javax.inject.Inject

class GetExerciseByIdUseCase @Inject constructor(
    private val repository: ExerciseRepository,
) {
    suspend operator fun invoke(id: String): Exercise? = repository.getById(id)
}
