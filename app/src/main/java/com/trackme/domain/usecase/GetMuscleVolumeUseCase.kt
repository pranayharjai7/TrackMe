package com.trackme.domain.usecase

import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MuscleVolume(val muscle: String, val totalSets: Int)

class GetMuscleVolumeUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    operator fun invoke(userId: String, fromDate: Long): Flow<List<MuscleVolume>> =
        workoutRepository.getSetsSince(userId, fromDate).map { sets ->
            val setsByExercise = withContext(Dispatchers.Default) {
                sets.groupingBy { it.exerciseId }.eachCount()
            }
            val exercisesById = exerciseRepository.getByIds(setsByExercise.keys)

            withContext(Dispatchers.Default) {
                val setsByMuscle = mutableMapOf<String, Int>()
                setsByExercise.forEach { (exerciseId, setCount) ->
                    exercisesById[exerciseId]?.primaryMuscles?.forEach { muscle ->
                        setsByMuscle[muscle] = (setsByMuscle[muscle] ?: 0) + setCount
                    }
                }

                setsByMuscle
                    .map { (muscle, totalSets) -> MuscleVolume(muscle, totalSets) }
                    .sortedByDescending { it.totalSets }
                    .take(5)
            }
        }
}
