package com.trackme.domain.repository

import com.trackme.data.health.HcSdkStatus
import com.trackme.domain.model.HealthMetric
import com.trackme.domain.model.HealthSnapshot
import kotlinx.coroutines.flow.Flow

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
