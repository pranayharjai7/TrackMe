package com.trackme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

/**
 * WorkManager worker that reconciles local Room rows with Supabase by delegating to SyncManager.
 *
 * Architecture Layer: Sync/Data boundary
 *
 * Responsibilities:
 * - Handle periodic and background sync triggers via WorkManager.
 * - Delegate the core execution to SyncManager's direct execution engine.
 * - Centralize WorkManager retry semantics (success or retry).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.success()
        syncManager.executeSyncDirectly(userId)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )
}
