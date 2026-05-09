package com.trackme.domain.usecase

import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

class AddExerciseToDayUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(dayId: String, userId: String, exerciseId: String, orderIndex: Int) {
        repo.addPlannedExercise(
            PlannedExercise(
                id = UUID.randomUUID().toString(),
                dayId = dayId,
                userId = userId,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
