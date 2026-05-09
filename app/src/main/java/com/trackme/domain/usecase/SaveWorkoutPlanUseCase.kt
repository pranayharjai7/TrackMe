package com.trackme.domain.usecase

import com.trackme.domain.model.WorkoutPlan
import com.trackme.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

class SaveWorkoutPlanUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(userId: String, name: String): WorkoutPlan {
        val plan = WorkoutPlan(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        repo.savePlan(plan)
        repo.setActivePlan(plan.id, userId)
        return plan
    }
}
