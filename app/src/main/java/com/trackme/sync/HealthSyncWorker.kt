package com.trackme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.trackme.data.local.dao.HealthSnapshotDao
import com.trackme.data.remote.supabase.HealthRemoteSource
import com.trackme.domain.repository.HealthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs Health Connect snapshots to local Room and Supabase.
 *
 * Architecture Layer: Sync/Data boundary
 *
 * Responsibilities:
 * - Pull Health Connect data through HealthRepository.
 * - Push unsynced daily snapshots to Supabase.
 * - Retry transient failures while treating permission failures as terminal.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val healthRepository: HealthRepository,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val healthRemoteSource: HealthRemoteSource,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.success()
        return try {
            healthRepository.syncFromHealthConnect(userId)
            healthSnapshotDao.getUnsynced().forEach { entity ->
                healthRemoteSource.upsertSnapshot(entity)
                healthSnapshotDao.markSynced(entity.id)
            }
            Result.success()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "health_sync_periodic", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
