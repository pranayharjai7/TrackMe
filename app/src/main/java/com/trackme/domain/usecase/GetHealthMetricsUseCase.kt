package com.trackme.domain.usecase

import com.trackme.domain.model.HealthMetric
import com.trackme.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHealthMetricsUseCase @Inject constructor(private val repo: HealthRepository) {
    operator fun invoke(userId: String): Flow<List<HealthMetric>> = repo.getMetrics(userId)
}
