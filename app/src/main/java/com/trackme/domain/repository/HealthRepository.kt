package com.trackme.domain.repository

import com.trackme.domain.model.HealthMetric
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.HcSdkStatus
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for health data.
 *
 * Architecture Layer: Domain repository interface
 *
 * Responsibilities:
 * - Expose Health Connect status and locally stored health metrics to ViewModels.
 * - Keep UI and domain code independent from concrete Health Connect/Room classes.
 */
interface HealthRepository {
    suspend fun syncFromHealthConnect(userId: String)
    fun getSnapshots(userId: String, fromDate: Long): Flow<List<HealthSnapshot>>
    fun getMetrics(userId: String): Flow<List<HealthMetric>>
    suspend fun getLatestSnapshot(userId: String): HealthSnapshot?
    fun getHealthConnectStatus(): HcSdkStatus
    fun isHealthConnectAvailable(): Boolean
    suspend fun hasHealthConnectPermissions(): Boolean
    fun getRequiredPermissions(): Set<String>
}
