package com.trackme.data.repository

import com.trackme.data.health.HealthConnectManager
import com.trackme.data.local.dao.HealthMetricDao
import com.trackme.data.local.dao.HealthSnapshotDao
import com.trackme.domain.model.HcSdkStatus
import com.trackme.domain.model.HealthMetric
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for Health Connect and health metric persistence.
 *
 * Architecture Layer: Data repository
 *
 * Responsibilities:
 * - Coordinate Health Connect reads through HealthConnectManager.
 * - Persist normalized snapshots and detailed metrics in Room.
 * - Expose domain models through the HealthRepository interface.
 */
@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val healthMetricDao: HealthMetricDao,
) : HealthRepository {

    override suspend fun syncFromHealthConnect(userId: String) = withContext(Dispatchers.IO) {
        val snapshots = runCatching { healthConnectManager.readLast30Days(userId, 30) }
            .getOrElse { emptyList() }

        if (snapshots.isNotEmpty()) {
            healthSnapshotDao.insertAll(snapshots)
        }

        val metrics = runCatching { healthConnectManager.readDetailedMetricsLast30Days(userId, 30) }
            .getOrElse { emptyList() }

        healthMetricDao.replaceForUser(userId, metrics)
    }

    override suspend fun syncFromHealthConnectForDate(userId: String, date: java.time.LocalDate) = withContext(Dispatchers.IO) {
        val snapshot = runCatching { healthConnectManager.readDate(userId, date) }
            .getOrNull()

        if (snapshot != null) {
            healthSnapshotDao.insertAll(listOf(snapshot))
        }

        val metrics = runCatching { healthConnectManager.readDetailedMetricsForDate(userId, date) }
            .getOrElse { emptyList() }

        if (metrics.isNotEmpty()) {
            healthMetricDao.insertAll(metrics)
        }
    }

    override suspend fun syncFromHealthConnectBootstrap(userId: String) = withContext(Dispatchers.IO) {
        val snapshots = runCatching { healthConnectManager.readLast30Days(userId, 90) }
            .getOrElse { emptyList() }

        if (snapshots.isNotEmpty()) {
            healthSnapshotDao.insertAll(snapshots)
        }

        val metrics = runCatching { healthConnectManager.readDetailedMetricsLast30Days(userId, 90) }
            .getOrElse { emptyList() }

        healthMetricDao.replaceForUser(userId, metrics)
    }

    override fun getSnapshots(userId: String, fromDate: Long): Flow<List<HealthSnapshot>> =
        healthSnapshotDao.getSince(userId, fromDate).map { list -> list.map { it.toDomain() } }

    override fun getMetrics(userId: String): Flow<List<HealthMetric>> =
        healthMetricDao.getAllForUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getLatestSnapshot(userId: String): HealthSnapshot? =
        healthSnapshotDao.getLatest(userId)?.toDomain()

    override fun getRequiredPermissions(): Set<String> = healthConnectManager.requiredPermissions

    override fun getHealthConnectStatus(): HcSdkStatus = healthConnectManager.getSdkStatus()

    override fun isHealthConnectAvailable(): Boolean = healthConnectManager.isAvailable()

    override suspend fun hasHealthConnectPermissions(): Boolean = healthConnectManager.hasPermissions()
}
