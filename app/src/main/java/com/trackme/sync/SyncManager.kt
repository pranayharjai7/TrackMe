package com.trackme.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.PendingDeletionEntity
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import com.trackme.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Facade for scheduling workout data sync jobs and executing direct syncs.
 *
 * Architecture Layer: Sync coordinator
 *
 * Responsibilities:
 * - Define and execute the unified core synchronization algorithm.
 * - Coalesce immediate background sync requests after local writes.
 * - Register periodic background sync with network constraints.
 * - Run foreground initial syncs directly in a coroutine for instant scheduling.
 * - Listen to network reconnection and trigger sync automatically.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionSetDao: SessionSetDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val muscleWeeklyAnalyticsDao: MuscleWeeklyAnalyticsDao,
    private val exerciseProgressSnapshotDao: ExerciseProgressSnapshotDao,
    private val dailyHealthAnalyticsDao: DailyHealthAnalyticsDao,
    private val bodyStateSnapshotDao: BodyStateSnapshotDao,
    private val remoteSource: WorkoutRemoteSource,
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        syncScope.launch {
            var wasOffline = false
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline && wasOffline) {
                    Log.d("SyncManager", "Network connection re-established, triggering immediate sync.")
                    enqueueImmediateSync()
                }
                wasOffline = !isOnline
            }
        }
    }
    fun enqueueImmediateSync() {
        // Coalesce bursts of local writes (for example rapid set logging) into one sync.
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_immediate", ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_periodic", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancelAllSync() {
        WorkManager.getInstance(context).cancelUniqueWork("sync_immediate")
        WorkManager.getInstance(context).cancelUniqueWork("sync_periodic")
    }

    /**
     * Runs foreground initial sync directly on the current coroutine context
     * to eliminate scheduling overhead and network constraint latency.
     * Includes a 15-second safety timeout block to avoid locking out the user in slow network environments.
     */
    suspend fun runInitialSync(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(15_000) {
            runCatching {
                val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return@runCatching false
                executeSyncDirectly(userId)
                true
            }.onFailure { it.printStackTrace() }.getOrDefault(false)
        } ?: false
    }

    /**
     * Core merging engine that reconciles local Room database rows with remote Supabase rows.
     * Uses a last-write-wins (LWW) resolution and is offline-delete aware.
     * Merges are executed sequentially in strict foreign key dependency order to avoid
     * remote constraint violations and connection pool/rate-limiting exhaustion.
     */
    suspend fun executeSyncDirectly(userId: String) = withContext(Dispatchers.IO) {
        Log.d("SyncManager", "Starting direct sequential sync for user: $userId")

        // 1. Push pending offline deletions first so the merge cannot restore deleted rows.
        pendingDeletionDao.getAllForUser(userId).forEach { op ->
            Log.d("SyncManager", "Processing pending deletion for table ${op.tableName}, id: ${op.entityId}")
            runCatching { remoteSource.markDeleted(op.tableName, op.entityId, op.userId, op.deletedAt) }
                .onSuccess {
                    pendingDeletionDao.deleteById(op.entityId)
                    Log.d("SyncManager", "Successfully processed pending deletion for id: ${op.entityId}")
                }
                .onFailure { e ->
                    Log.e("SyncManager", "Failed to mark deletion for table ${op.tableName}, id: ${op.entityId}", e)
                }
        }

        // Only deletion pushes that failed above remain in this map.
        val failedPending = pendingDeletionDao.getAllForUser(userId).associateBy { it.entityId }

        // 2. Core Workout Schema Merging (Order based on foreign key dependencies)
        Log.d("SyncManager", "[1/9] Merging workout plans...")
        mergePlans(userId)

        Log.d("SyncManager", "[2/9] Merging workout days...")
        mergeDays(userId, failedPending)

        Log.d("SyncManager", "[3/9] Merging planned exercises...")
        mergePlannedExercises(userId, failedPending)

        Log.d("SyncManager", "[4/9] Merging workout sessions...")
        mergeSessions(userId)

        Log.d("SyncManager", "[5/9] Merging session sets...")
        mergeSets(userId, failedPending)

        // 3. Analytics & Snapshot Merging (Independent of core workout schema)
        Log.d("SyncManager", "[6/9] Merging muscle weekly analytics...")
        mergeMuscleWeeklyAnalytics(userId)

        Log.d("SyncManager", "[7/9] Merging exercise progress snapshots...")
        mergeExerciseProgressSnapshots(userId)

        Log.d("SyncManager", "[8/9] Merging daily health analytics...")
        mergeDailyHealthAnalytics(userId)

        Log.d("SyncManager", "[9/9] Merging body state snapshots...")
        mergeBodyStateSnapshots(userId)

        Log.d("SyncManager", "Direct sequential sync successfully completed for user: $userId")
    }

    private suspend fun mergeMuscleWeeklyAnalytics(userId: String) = mergeLastWriteWinsBatch(
        remoteList = remoteSource.fetchMuscleWeeklyAnalytics(userId),
        localList = muscleWeeklyAnalyticsDao.getAllForSync(userId),
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        setSynced = { it.copy(isSynced = true) },
        insertLocalBatch = { muscleWeeklyAnalyticsDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertMuscleWeeklyAnalytics(it) },
    )

    private suspend fun mergeExerciseProgressSnapshots(userId: String) = mergeLastWriteWinsBatch(
        remoteList = remoteSource.fetchExerciseProgressSnapshots(userId),
        localList = exerciseProgressSnapshotDao.getAllForSync(userId),
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        setSynced = { it.copy(isSynced = true) },
        insertLocalBatch = { exerciseProgressSnapshotDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertExerciseProgressSnapshots(it) },
    )

    private suspend fun mergeDailyHealthAnalytics(userId: String) = mergeLastWriteWinsBatch(
        remoteList = remoteSource.fetchDailyHealthAnalytics(userId),
        localList = dailyHealthAnalyticsDao.getAllForSync(userId),
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        setSynced = { it.copy(isSynced = true) },
        insertLocalBatch = { dailyHealthAnalyticsDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertDailyHealthAnalytics(it) },
    )

    private suspend fun mergeBodyStateSnapshots(userId: String) = mergeLastWriteWinsBatch(
        remoteList = remoteSource.fetchBodyStateSnapshots(userId),
        localList = bodyStateSnapshotDao.getAllForSync(userId),
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        setSynced = { it.copy(isSynced = true) },
        insertLocalBatch = { bodyStateSnapshotDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertBodyStateSnapshots(it) },
    )

    private suspend fun mergePlans(userId: String) = mergeLastWriteWinsBatch(
        remoteList = remoteSource.fetchPlans(userId),
        localList = workoutPlanDao.getAllForSync(userId),
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        setSynced = { it.copy(isSynced = true) },
        insertLocalBatch = { workoutPlanDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertPlans(it) },
    )

    private suspend fun mergeDays(
        userId: String,
        failedPending: Map<String, PendingDeletionEntity>,
    ) = mergeSoftDeleteAwareBatch(
        tableName = "workout_days",
        remoteList = remoteSource.fetchDays(userId),
        localList = workoutDayDao.getAllForSync(userId),
        failedPending = failedPending,
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        deletedAt = { it.deletedAt },
        setSynced = { it.copy(isSynced = true) },
        copyAsPendingDelete = { entity, timestamp ->
            entity.copy(deletedAt = timestamp, updatedAt = timestamp, isSynced = false)
        },
        insertLocalBatch = { workoutDayDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertDays(it) },
    )

    private suspend fun mergePlannedExercises(
        userId: String,
        failedPending: Map<String, PendingDeletionEntity>,
    ) = mergeSoftDeleteAwareBatch(
        tableName = "planned_exercises",
        remoteList = remoteSource.fetchPlannedExercises(userId),
        localList = plannedExerciseDao.getAllForSync(userId),
        failedPending = failedPending,
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        deletedAt = { it.deletedAt },
        setSynced = { it.copy(isSynced = true) },
        copyAsPendingDelete = { entity, timestamp ->
            entity.copy(deletedAt = timestamp, updatedAt = timestamp, isSynced = false)
        },
        insertLocalBatch = { plannedExerciseDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertPlannedExercises(it) },
    )

    private suspend fun mergeSessions(userId: String) = mergeLastWriteWinsBatch(
        remoteList = remoteSource.fetchSessions(userId),
        localList = workoutSessionDao.getAllForSync(userId),
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        setSynced = { it.copy(isSynced = true) },
        insertLocalBatch = { workoutSessionDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertSessions(it) },
    )

    private suspend fun mergeSets(
        userId: String,
        failedPending: Map<String, PendingDeletionEntity>,
    ) = mergeSoftDeleteAwareBatch(
        tableName = "session_sets",
        remoteList = remoteSource.fetchSets(userId),
        localList = sessionSetDao.getAllForSync(userId),
        failedPending = failedPending,
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        deletedAt = { it.deletedAt },
        setSynced = { it.copy(isSynced = true) },
        copyAsPendingDelete = { entity, timestamp ->
            entity.copy(deletedAt = timestamp, updatedAt = timestamp, isSynced = false)
        },
        insertLocalBatch = { sessionSetDao.insertAll(it) },
        upsertRemoteBatch = { remoteSource.upsertSets(it) },
    )

    private suspend fun <T> mergeLastWriteWinsBatch(
        remoteList: List<T>,
        localList: List<T>,
        entityId: (T) -> String,
        updatedAt: (T) -> Long,
        isSynced: (T) -> Boolean,
        setSynced: (T) -> T,
        insertLocalBatch: suspend (List<T>) -> Unit,
        upsertRemoteBatch: suspend (List<T>) -> Unit,
    ) {
        val remoteMap = remoteList.associateBy(entityId)
        val localMap = localList.associateBy(entityId)

        val localInserts = mutableListOf<T>()
        val remoteUpserts = mutableListOf<T>()

        (remoteMap.keys + localMap.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]

            when {
                local == null && remote != null -> {
                    localInserts.add(remote)
                }
                remote == null && local != null -> {
                    remoteUpserts.add(local)
                }
                remote != null && local != null && updatedAt(remote) > updatedAt(local) -> {
                    localInserts.add(remote)
                }
                remote != null && local != null && (updatedAt(local) > updatedAt(remote) || !isSynced(local)) -> {
                    remoteUpserts.add(local)
                }
            }
        }

        if (remoteUpserts.isNotEmpty()) {
            upsertRemoteBatch(remoteUpserts)
            val syncedLocals = remoteUpserts.map(setSynced)
            localInserts.addAll(syncedLocals)
        }

        if (localInserts.isNotEmpty()) {
            insertLocalBatch(localInserts)
        }
    }

    private suspend fun <T> mergeSoftDeleteAwareBatch(
        tableName: String,
        remoteList: List<T>,
        localList: List<T>,
        failedPending: Map<String, PendingDeletionEntity>,
        entityId: (T) -> String,
        updatedAt: (T) -> Long,
        isSynced: (T) -> Boolean,
        deletedAt: (T) -> Long?,
        setSynced: (T) -> T,
        copyAsPendingDelete: (T, Long) -> T,
        insertLocalBatch: suspend (List<T>) -> Unit,
        upsertRemoteBatch: suspend (List<T>) -> Unit,
    ) {
        val remoteMap = remoteList.associateBy(entityId)
        val localMap = localList.associateBy(entityId)
        val pendingForTable = failedPending.filter { it.value.tableName == tableName }

        val localInserts = mutableListOf<T>()
        val remoteUpserts = mutableListOf<T>()
        val deletesFromPending = mutableListOf<String>()

        (remoteMap.keys + localMap.keys + pendingForTable.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            val pending = pendingForTable[id]

            when {
                pending != null && local == null -> {
                    if (remote != null) {
                        val delAt = maxOf(updatedAt(remote), pending.deletedAt)
                        localInserts.add(copyAsPendingDelete(remote, delAt))
                    } else {
                        deletesFromPending.add(id)
                    }
                }
                pending != null && local != null && deletedAt(local) == null -> {
                    localInserts.add(copyAsPendingDelete(local, pending.deletedAt))
                }
                else -> {
                    when {
                        local == null && remote != null -> {
                            localInserts.add(remote)
                        }
                        remote == null && local != null -> {
                            remoteUpserts.add(local)
                        }
                        remote != null && local != null && updatedAt(remote) > updatedAt(local) -> {
                            localInserts.add(remote)
                        }
                        remote != null && local != null && (updatedAt(local) > updatedAt(remote) || !isSynced(local)) -> {
                            remoteUpserts.add(local)
                        }
                        else -> {
                            deletesFromPending.add(id)
                        }
                    }
                }
            }
        }

        if (remoteUpserts.isNotEmpty()) {
            upsertRemoteBatch(remoteUpserts)
            val syncedLocals = remoteUpserts.map(setSynced)
            localInserts.addAll(syncedLocals)
            remoteUpserts.forEach {
                deletesFromPending.add(entityId(it))
            }
        }

        if (localInserts.isNotEmpty()) {
            insertLocalBatch(localInserts)
        }

        if (deletesFromPending.isNotEmpty()) {
            pendingDeletionDao.deleteByIds(deletesFromPending)
        }
    }
}
