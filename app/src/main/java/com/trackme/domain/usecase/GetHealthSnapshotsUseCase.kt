package com.trackme.domain.usecase

import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class GetHealthSnapshotsUseCase @Inject constructor(private val repo: HealthRepository) {
    operator fun invoke(userId: String, daysBack: Int = 30): Flow<List<HealthSnapshot>> {
        val fromDate = Instant.now().minus(daysBack.toLong(), ChronoUnit.DAYS).toEpochMilli()
        return repo.getSnapshots(userId, fromDate)
    }
}
