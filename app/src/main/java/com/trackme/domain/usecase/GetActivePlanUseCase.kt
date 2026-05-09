package com.trackme.domain.usecase

import com.trackme.domain.model.WorkoutPlan
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActivePlanUseCase @Inject constructor(private val repo: WorkoutRepository) {
    operator fun invoke(userId: String): Flow<WorkoutPlan?> = repo.getActivePlan(userId)
}
