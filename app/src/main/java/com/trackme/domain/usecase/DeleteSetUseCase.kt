package com.trackme.domain.usecase

import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.WorkoutRepository
import javax.inject.Inject

class DeleteSetUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(set: SessionSet) {
        repo.deleteSet(set)
    }
}
