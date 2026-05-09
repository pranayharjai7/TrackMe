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
        workoutRepository.getSetsSince(userId, fromDate).map { sets ->
            sets
                .groupBy { it.exerciseId }
                .flatMap { (exerciseId, exerciseSets) ->
                    val exercise = exerciseRepository.getById(exerciseId) ?: return@flatMap emptyList()
                    exercise.primaryMuscles.map { muscle -> muscle to exerciseSets.size }
                }
                .groupBy { it.first }
                .map { (muscle, entries) -> MuscleVolume(muscle, entries.sumOf { it.second }) }
                .sortedByDescending { it.totalSets }
                .take(5)
        }
}
