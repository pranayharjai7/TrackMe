package com.trackme.ui.workout.session.notification

import android.content.Context
import android.content.Intent
import android.os.Build
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.ui.workout.session.WorkoutSessionManager
import com.trackme.ui.workout.session.WorkoutSessionService
import com.trackme.utils.startOfTodayMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts the workout foreground service after process death when a session is still in progress.
 */
@Singleton
class WorkoutNotificationBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val sessionManager: WorkoutSessionManager,
    private val supabase: SupabaseClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recovered = false

    fun recoverIfNeeded() {
        if (recovered) return
        if (sessionManager.uiState.value.sessionId.isNotEmpty()) {
            recovered = true
            ensureServiceRunning()
            return
        }

        scope.launch {
            val userId = runCatching { supabase.auth.currentSessionOrNull()?.user?.id }.getOrNull()
                ?: return@launch
            val inProgress = workoutRepository.getInProgressSession(userId, startOfTodayMillis())
                ?: return@launch

            WorkoutNotificationLogger.i("Recovering in-progress session ${inProgress.id}")
            sessionManager.startOrResumeSession(userId, inProgress.dayId, inProgress.date)
            recovered = true
            ensureServiceRunning()
        }
    }

    private fun ensureServiceRunning() {
        val intent = Intent(context, WorkoutSessionService::class.java).apply {
            action = WorkoutNotificationActions.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
