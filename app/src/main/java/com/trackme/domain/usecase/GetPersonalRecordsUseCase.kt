package com.trackme.domain.usecase

import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPersonalRecordsUseCase @Inject constructor(private val repo: WorkoutRepository) {
    operator fun invoke(userId: String): Flow<List<PersonalRecord>> = repo.getPersonalRecords(userId)
}
