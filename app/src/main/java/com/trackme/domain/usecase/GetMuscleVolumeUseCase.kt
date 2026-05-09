package com.trackme.domain.usecase

import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class MuscleVolume(val muscle: String, val totalSets: Int)

class GetMuscleVolumeUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    operator fun invoke(userId: String, fromDate: Long): Flow<List<MuscleVolume>> =
        workoutRepository.getSessionsSince(userId, fromDate).map {
            emptyList()
        }
}
