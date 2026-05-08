package com.trackme.domain.usecase

import com.trackme.domain.repository.ExerciseRepository
import javax.inject.Inject

class SeedExercisesUseCase @Inject constructor(
    private val repository: ExerciseRepository,
) {
    suspend operator fun invoke() = repository.seedIfEmpty()
}
