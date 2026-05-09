package com.trackme.domain.repository

import com.trackme.domain.model.HealthSnapshot
import kotlinx.coroutines.flow.Flow

interface HealthRepository {
    suspend fun syncFromHealthConnect(userId: String)
    fun getSnapshots(userId: String, fromDate: Long): Flow<List<HealthSnapshot>>
    suspend fun getLatestSnapshot(userId: String): HealthSnapshot?
    fun isHealthConnectAvailable(): Boolean
    suspend fun hasHealthConnectPermissions(): Boolean
}
