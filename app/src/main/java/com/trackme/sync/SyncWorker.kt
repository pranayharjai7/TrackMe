package com.trackme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trackme.data.local.dao.PlannedExerciseDao
import com.trackme.data.local.dao.PendingDeletionDao
import com.trackme.data.local.dao.SessionSetDao
import com.trackme.data.local.dao.WorkoutDayDao
import com.trackme.data.local.dao.WorkoutPlanDao
import com.trackme.data.local.dao.WorkoutSessionDao
import com.trackme.data.local.dao.MuscleWeeklyAnalyticsDao
import com.trackme.data.local.dao.ExerciseProgressSnapshotDao
import com.trackme.data.local.dao.DailyHealthAnalyticsDao
import com.trackme.data.local.dao.BodyStateSnapshotDao
import com.trackme.data.local.entity.PendingDeletionEntity
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

/**
 * WorkManager worker that reconciles local Room rows with Supabase.
 *
 * Architecture Layer: Sync/Data boundary
 *
 * Responsibilities:
 * - Push queued soft deletions before any pull/merge operation.
 * - Run last-write-wins reconciliation for each synced table.
 * - Keep retry semantics centralized in WorkManager instead of ViewModels.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.success()

        // Push pending offline deletions first so the merge cannot restore deleted rows.
        pendingDeletionDao.getAllForUser(userId).forEach { op ->
            runCatching { remoteSource.markDeleted(op.tableName, op.entityId, op.userId, op.deletedAt) }
                .onSuccess { pendingDeletionDao.deleteById(op.entityId) }
        }

        // Only deletion pushes that failed above remain in this map.
        val failedPending = pendingDeletionDao.getAllForUser(userId).associateBy { it.entityId }

        mergePlans(userId)
        mergeDays(userId, failedPending)
        mergePlannedExercises(userId, failedPending)
        mergeSessions(userId)
        mergeSets(userId, failedPending)
        mergeMuscleWeeklyAnalytics(userId)
        mergeExerciseProgressSnapshots(userId)
        mergeDailyHealthAnalytics(userId)
        mergeBodyStateSnapshots(userId)
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    private suspend fun mergeMuscleWeeklyAnalytics(userId: String) = mergeLastWriteWins(
        remoteMap = remoteSource.fetchMuscleWeeklyAnalytics(userId).associateBy { it.id },
        localMap = muscleWeeklyAnalyticsDao.getAllForSync(userId).associateBy { it.id },
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        insertLocal = { muscleWeeklyAnalyticsDao.insert(it) },
        upsertRemote = { remoteSource.upsertMuscleWeeklyAnalytics(it) },
        markSynced = { muscleWeeklyAnalyticsDao.markSynced(it) },
    )

    private suspend fun mergeExerciseProgressSnapshots(userId: String) = mergeLastWriteWins(
        remoteMap = remoteSource.fetchExerciseProgressSnapshots(userId).associateBy { it.id },
        localMap = exerciseProgressSnapshotDao.getAllForSync(userId).associateBy { it.id },
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        insertLocal = { exerciseProgressSnapshotDao.insert(it) },
        upsertRemote = { remoteSource.upsertExerciseProgressSnapshot(it) },
        markSynced = { exerciseProgressSnapshotDao.markSynced(it) },
    )

    private suspend fun mergeDailyHealthAnalytics(userId: String) = mergeLastWriteWins(
        remoteMap = remoteSource.fetchDailyHealthAnalytics(userId).associateBy { it.id },
        localMap = dailyHealthAnalyticsDao.getAllForSync(userId).associateBy { it.id },
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        insertLocal = { dailyHealthAnalyticsDao.insert(it) },
        upsertRemote = { remoteSource.upsertDailyHealthAnalytics(it) },
        markSynced = { dailyHealthAnalyticsDao.markSynced(it) },
    )

    private suspend fun mergeBodyStateSnapshots(userId: String) = mergeLastWriteWins(
        remoteMap = remoteSource.fetchBodyStateSnapshots(userId).associateBy { it.id },
        localMap = bodyStateSnapshotDao.getAllForSync(userId).associateBy { it.id },
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        insertLocal = { bodyStateSnapshotDao.insert(it) },
        upsertRemote = { remoteSource.upsertBodyStateSnapshot(it) },
        markSynced = { bodyStateSnapshotDao.markSynced(it) },
    )

    private suspend fun mergePlans(userId: String) = mergeLastWriteWins(
        remoteMap = remoteSource.fetchPlans(userId).associateBy { it.id },
        localMap = workoutPlanDao.getAllForSync(userId).associateBy { it.id },
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        insertLocal = { workoutPlanDao.insert(it) },
        upsertRemote = { remoteSource.upsertPlan(it) },
        markSynced = { workoutPlanDao.markSynced(it) },
    )

    private suspend fun mergeDays(
        userId: String,
        failedPending: Map<String, PendingDeletionEntity>,
    ) = mergeSoftDeleteAware(
        tableName = "workout_days",
        remoteMap = remoteSource.fetchDays(userId).associateBy { it.id },
        localMap = workoutDayDao.getAllForSync(userId).associateBy { it.id },
        failedPending = failedPending,
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        deletedAt = { it.deletedAt },
        copyAsPendingDelete = { entity, timestamp ->
            entity.copy(deletedAt = timestamp, updatedAt = timestamp, isSynced = false)
        },
        softDeleteLocal = { id, timestamp -> workoutDayDao.softDelete(id, timestamp) },
        insertLocal = { workoutDayDao.insert(it) },
        upsertRemote = { remoteSource.upsertDay(it) },
        markSynced = { workoutDayDao.markSynced(it) },
    )

    private suspend fun mergePlannedExercises(
        userId: String,
        failedPending: Map<String, PendingDeletionEntity>,
    ) = mergeSoftDeleteAware(
        tableName = "planned_exercises",
        remoteMap = remoteSource.fetchPlannedExercises(userId).associateBy { it.id },
        localMap = plannedExerciseDao.getAllForSync(userId).associateBy { it.id },
        failedPending = failedPending,
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        deletedAt = { it.deletedAt },
        copyAsPendingDelete = { entity, timestamp ->
            entity.copy(deletedAt = timestamp, updatedAt = timestamp, isSynced = false)
        },
        softDeleteLocal = { id, timestamp -> plannedExerciseDao.softDelete(id, timestamp) },
        insertLocal = { plannedExerciseDao.insert(it) },
        upsertRemote = { remoteSource.upsertPlannedExercise(it) },
        markSynced = { plannedExerciseDao.markSynced(it) },
    )

    private suspend fun mergeSessions(userId: String) = mergeLastWriteWins(
        remoteMap = remoteSource.fetchSessions(userId).associateBy { it.id },
        localMap = workoutSessionDao.getAllForSync(userId).associateBy { it.id },
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        insertLocal = { workoutSessionDao.insert(it) },
        upsertRemote = { remoteSource.upsertSession(it) },
        markSynced = { workoutSessionDao.markSynced(it) },
    )

    private suspend fun mergeSets(
        userId: String,
        failedPending: Map<String, PendingDeletionEntity>,
    ) = mergeSoftDeleteAware(
        tableName = "session_sets",
        remoteMap = remoteSource.fetchSets(userId).associateBy { it.id },
        localMap = sessionSetDao.getAllForSync(userId).associateBy { it.id },
        failedPending = failedPending,
        entityId = { it.id },
        updatedAt = { it.updatedAt },
        isSynced = { it.isSynced },
        deletedAt = { it.deletedAt },
        copyAsPendingDelete = { entity, timestamp ->
            entity.copy(deletedAt = timestamp, updatedAt = timestamp, isSynced = false)
        },
        softDeleteLocal = { id, timestamp -> sessionSetDao.softDelete(id, timestamp) },
        insertLocal = { sessionSetDao.insert(it) },
        upsertRemote = { remoteSource.upsertSet(it) },
        markSynced = { sessionSetDao.markSynced(it) },
    )

    /**
     * Runs the shared last-write-wins merge for tables without delete recovery.
     *
     * Inputs:
     * - remoteMap/localMap: rows keyed by stable entity ID.
     * - callbacks: table-specific Room and Supabase operations.
     */
    private suspend fun <T> mergeLastWriteWins(
        remoteMap: Map<String, T>,
        localMap: Map<String, T>,
        entityId: (T) -> String,
        updatedAt: (T) -> Long,
        isSynced: (T) -> Boolean,
        insertLocal: suspend (T) -> Unit,
        upsertRemote: suspend (T) -> Unit,
        markSynced: suspend (String) -> Unit,
    ) {
        (remoteMap.keys + localMap.keys).forEach { id ->
            mergeOneLastWriteWins(
                remote = remoteMap[id],
                local = localMap[id],
                entityId = entityId,
                updatedAt = updatedAt,
                isSynced = isSynced,
                insertLocal = insertLocal,
                upsertRemote = upsertRemote,
                markSynced = markSynced,
                afterRemoteUpsert = {},
            )
        }
    }

    /**
     * Runs LWW merge plus offline-delete recovery for soft-deletable tables.
     *
     * Side effects:
     * - Creates a local soft-deleted row when a failed delete exists only remotely.
     * - Clears pending deletion rows only after the remote row is gone or updated.
     */
    private suspend fun <T> mergeSoftDeleteAware(
        tableName: String,
        remoteMap: Map<String, T>,
        localMap: Map<String, T>,
        failedPending: Map<String, PendingDeletionEntity>,
        entityId: (T) -> String,
        updatedAt: (T) -> Long,
        isSynced: (T) -> Boolean,
        deletedAt: (T) -> Long?,
        copyAsPendingDelete: (T, Long) -> T,
        softDeleteLocal: suspend (String, Long) -> Unit,
        insertLocal: suspend (T) -> Unit,
        upsertRemote: suspend (T) -> Unit,
        markSynced: suspend (String) -> Unit,
    ) {
        val pendingForTable = failedPending.filter { it.value.tableName == tableName }
        (remoteMap.keys + localMap.keys + pendingForTable.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            val pending = pendingForTable[id]

            when {
                pending != null && local == null -> recoverMissingLocalDelete(
                    id = id,
                    remote = remote,
                    pending = pending,
                    updatedAt = updatedAt,
                    copyAsPendingDelete = copyAsPendingDelete,
                    insertLocal = insertLocal,
                )
                pending != null && local != null && deletedAt(local) == null -> {
                    softDeleteLocal(entityId(local), pending.deletedAt)
                }
                else -> mergeOneLastWriteWins(
                    remote = remote,
                    local = local,
                    entityId = entityId,
                    updatedAt = updatedAt,
                    isSynced = isSynced,
                    insertLocal = insertLocal,
                    upsertRemote = upsertRemote,
                    markSynced = markSynced,
                    afterRemoteUpsert = { pendingDeletionDao.deleteById(it) },
                    afterNoOp = { pendingDeletionDao.deleteById(id) },
                )
            }
        }
    }

    private suspend fun <T> recoverMissingLocalDelete(
        id: String,
        remote: T?,
        pending: PendingDeletionEntity,
        updatedAt: (T) -> Long,
        copyAsPendingDelete: (T, Long) -> T,
        insertLocal: suspend (T) -> Unit,
    ) {
        if (remote != null) {
            val deletedAt = maxOf(updatedAt(remote), pending.deletedAt)
            insertLocal(copyAsPendingDelete(remote, deletedAt))
        } else {
            pendingDeletionDao.deleteById(id)
        }
    }

    private suspend fun <T> mergeOneLastWriteWins(
        remote: T?,
        local: T?,
        entityId: (T) -> String,
        updatedAt: (T) -> Long,
        isSynced: (T) -> Boolean,
        insertLocal: suspend (T) -> Unit,
        upsertRemote: suspend (T) -> Unit,
        markSynced: suspend (String) -> Unit,
        afterRemoteUpsert: suspend (String) -> Unit,
        afterNoOp: suspend () -> Unit = {},
    ) {
        when {
            local == null && remote != null -> insertLocal(remote)
            remote == null && local != null -> {
                upsertRemote(local)
                markSynced(entityId(local))
                afterRemoteUpsert(entityId(local))
            }
            remote != null && local != null && updatedAt(remote) > updatedAt(local) -> {
                insertLocal(remote)
            }
            remote != null && local != null && (updatedAt(local) > updatedAt(remote) || !isSynced(local)) -> {
                upsertRemote(local)
                markSynced(entityId(local))
                afterRemoteUpsert(entityId(local))
            }
            else -> afterNoOp()
        }
    }
}
