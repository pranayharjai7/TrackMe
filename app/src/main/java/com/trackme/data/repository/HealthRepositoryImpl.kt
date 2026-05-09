package com.trackme.data.repository

import com.trackme.data.health.HealthConnectManager
import com.trackme.data.local.dao.HealthSnapshotDao
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val healthSnapshotDao: HealthSnapshotDao,
) : HealthRepository {

    override suspend fun syncFromHealthConnect(userId: String) {
        val snapshots = healthConnectManager.readLast30Days(userId)
        healthSnapshotDao.insertAllIfAbsent(snapshots)
        // No sync trigger here — HealthSyncWorker handles the upload pipeline
    }

    override fun getSnapshots(userId: String, fromDate: Long): Flow<List<HealthSnapshot>> =
        healthSnapshotDao.getSince(userId, fromDate).map { list -> list.map { it.toDomain() } }

    override suspend fun getLatestSnapshot(userId: String): HealthSnapshot? =
        healthSnapshotDao.getLatest(userId)?.toDomain()

    override fun isHealthConnectAvailable(): Boolean = healthConnectManager.isAvailable()

    override suspend fun hasHealthConnectPermissions(): Boolean = healthConnectManager.hasPermissions()
}
