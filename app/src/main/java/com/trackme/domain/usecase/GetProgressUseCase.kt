package com.trackme.domain.usecase

import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProgressUseCase @Inject constructor(private val repo: WorkoutRepository) {
    operator fun invoke(userId: String, exerciseId: String): Flow<List<SessionSet>> =
        repo.getHistoryForExercise(userId, exerciseId)
}
