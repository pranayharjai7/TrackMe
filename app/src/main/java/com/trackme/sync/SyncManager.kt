package com.trackme.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueueImmediateSync() {
        // Coalesce bursts of local writes (for example rapid set logging) into one sync.
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_immediate", ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_periodic", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancelAllSync() {
        WorkManager.getInstance(context).cancelUniqueWork("sync_immediate")
        WorkManager.getInstance(context).cancelUniqueWork("sync_periodic")
    }
}
