package com.trackme.ui.workout.session

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.trackme.MainActivity
import com.trackme.R
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground Service that keeps the live workout notification running in the background.
 *
 * Architecture Layer: Service (Android Lifecycle Integration)
 * Pattern: Event-Driven Background Service
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject
    lateinit var sessionManager: WorkoutSessionManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var notificationManager: NotificationManager

    private var wasResting = false

    companion object {
        private const val ONGOING_NOTIFICATION_ID = 2026
        private const val ALERT_NOTIFICATION_ID = 2027

        const val ONGOING_CHANNEL_ID = "workout_ongoing_channel"
        const val ALERTS_CHANNEL_ID = "workout_alerts_channel"

        // Action constants
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        const val ACTION_START_SET = "ACTION_START_SET"
        const val ACTION_COMPLETE_SET = "ACTION_COMPLETE_SET"
        const val ACTION_SKIP_REST = "ACTION_SKIP_REST"
        
        const val ACTION_PAUSE_WORKOUT = "ACTION_PAUSE_WORKOUT"
        const val ACTION_RESUME_WORKOUT = "ACTION_RESUME_WORKOUT"
        const val ACTION_END_WORKOUT = "ACTION_END_WORKOUT"
        
        const val ACTION_SKIP_EXERCISE = "ACTION_SKIP_EXERCISE"
        const val ACTION_PREV_EXERCISE = "ACTION_PREV_EXERCISE"
        
        const val ACTION_WEIGHT_INC = "ACTION_WEIGHT_INC"
        const val ACTION_WEIGHT_DEC = "ACTION_WEIGHT_DEC"
        const val ACTION_REPS_INC = "ACTION_REPS_INC"
        const val ACTION_REPS_DEC = "ACTION_REPS_DEC"
        const val ACTION_SAVE_SET = "ACTION_SAVE_SET"
        
        const val ACTION_REST_ADD_15 = "ACTION_REST_ADD_15"
        const val ACTION_REST_ADD_30 = "ACTION_REST_ADD_30"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        startForegroundWithPlaceholder()

        // Begin observing active session state coordinator
        serviceScope.launch {
            sessionManager.uiState.collect { state ->
                if (state.sessionId.isEmpty()) {
                    // No session active: stop background presence
                    stopSelf()
                } else {
                    updateOngoingNotification(state)
                    checkRestTimerFinished(state)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        // Handle intent control triggers asynchronously
        serviceScope.launch {
            val currentState = sessionManager.uiState.value
            val activeExId = currentState.activeExerciseId
            val restingExId = currentState.restingExerciseId

            when (action) {
                ACTION_START_SERVICE -> {
                    // Handled in initialization collection
                }
                ACTION_STOP_SERVICE -> {
                    stopSelf()
                }
                ACTION_START_SET -> {
                    // Activate first incomplete exercise or current active exercise
                    val targetExerciseId = activeExId ?: currentState.exercises.firstOrNull { (planned, _) ->
                        (currentState.loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
                    }?.first?.exerciseId
                    targetExerciseId?.let { sessionManager.startExercise(it) }
                }
                ACTION_COMPLETE_SET -> {
                    activeExId?.let { exId ->
                        sessionManager.completeSet(
                            exerciseId = exId,
                            weightKg = currentState.quickWeight,
                            reps = currentState.quickReps
                        )
                    }
                }
                ACTION_SKIP_REST -> {
                    restingExId?.let { sessionManager.skipRest(it) }
                    cancelAlertNotification()
                }
                ACTION_PAUSE_WORKOUT -> {
                    sessionManager.pauseWorkout()
                }
                ACTION_RESUME_WORKOUT -> {
                    sessionManager.resumeWorkout()
                }
                ACTION_END_WORKOUT -> {
                    sessionManager.finishSession {}
                }
                ACTION_SKIP_EXERCISE -> {
                    activeExId?.let { sessionManager.skipExercise(it) }
                }
                ACTION_PREV_EXERCISE -> {
                    activeExId?.let { sessionManager.previousExercise(it) }
                }
                ACTION_WEIGHT_INC -> {
                    sessionManager.adjustQuickWeight(2.5f)
                }
                ACTION_WEIGHT_DEC -> {
                    sessionManager.adjustQuickWeight(-2.5f)
                }
                ACTION_REPS_INC -> {
                    sessionManager.adjustQuickReps(1)
                }
                ACTION_REPS_DEC -> {
                    sessionManager.adjustQuickReps(-1)
                }
                ACTION_SAVE_SET -> {
                    sessionManager.saveQuickSet()
                }
                ACTION_REST_ADD_15 -> {
                    sessionManager.adjustRestTime(15)
                }
                ACTION_REST_ADD_30 -> {
                    sessionManager.adjustRestTime(30)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Immediately promotes the service to a foreground service with a placeholder notification.
     * This avoids Android's ForegroundServiceDidNotStartInTimeException.
     */
    private fun startForegroundWithPlaceholder() {
        val packageName = packageName
        val collapsedViews = RemoteViews(packageName, R.layout.notification_workout_collapsed).apply {
            setTextViewText(R.id.tv_workout_name, "Workout Session")
            setTextViewText(R.id.tv_exercise_status, "Starting workout...")
            setImageViewResource(R.id.iv_status_icon, android.R.drawable.checkbox_off_background)
            
            // Hide control buttons on placeholder
            setViewVisibility(R.id.btn_collapsed_start, View.GONE)
            setViewVisibility(R.id.btn_collapsed_complete, View.GONE)
            setViewVisibility(R.id.btn_collapsed_skip_rest, View.GONE)
            setViewVisibility(R.id.btn_collapsed_next_exercise, View.GONE)
        }
        val expandedViews = RemoteViews(packageName, R.layout.notification_workout_expanded).apply {
            setTextViewText(R.id.tv_expanded_workout_name, "Workout Session")
            setTextViewText(R.id.tv_expanded_exercise_status, "Starting workout...")
            setImageViewResource(R.id.iv_expanded_status_icon, android.R.drawable.checkbox_off_background)
            
            // Hide progress bars and buttons/panels on placeholder
            setViewVisibility(R.id.pb_workout_progress, View.GONE)
            setViewVisibility(R.id.tv_progress_bar_text, View.GONE)
            setViewVisibility(R.id.tv_next_exercise_preview, View.GONE)
            setViewVisibility(R.id.btn_expanded_primary, View.GONE)
            setViewVisibility(R.id.ll_rest_adjust_panel, View.GONE)
            setViewVisibility(R.id.ll_quick_log_panel, View.GONE)
            setViewVisibility(R.id.btn_prev_exercise, View.GONE)
            setViewVisibility(R.id.btn_skip_exercise, View.GONE)
            setViewVisibility(R.id.btn_pause_resume, View.GONE)
            setViewVisibility(R.id.btn_end_workout, View.GONE)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ONGOING_NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    /**
     * Rebuilds and posts the ongoing notification reflecting the latest WorkoutState.
     */
    private fun updateOngoingNotification(state: ActiveSessionUiState) {
        val packageName = packageName

        // Initialize collapsed remote views
        val collapsedViews = RemoteViews(packageName, R.layout.notification_workout_collapsed)
        // Initialize expanded remote views
        val expandedViews = RemoteViews(packageName, R.layout.notification_workout_expanded)

        // Find active and next exercise details
        val activeEx = state.exercises.find { it.first.exerciseId == state.activeExerciseId }
        val restingEx = state.exercises.find { it.first.exerciseId == state.restingExerciseId }

        // Find next planned exercise in order
        val activeOrRestingId = state.activeExerciseId ?: state.restingExerciseId
        val orderedPlanned = state.exercises
        val currentIndex = orderedPlanned.indexOfFirst { it.first.exerciseId == activeOrRestingId }
        val nextEx = if (currentIndex != -1 && currentIndex < orderedPlanned.size - 1) {
            orderedPlanned[currentIndex + 1]
        } else {
            null
        }

        // ==========================================
        // 1. POPULATE COLLAPSED & EXPANDED VIEWS
        // ==========================================

        val workoutName = "Live Workout Session"
        collapsedViews.setTextViewText(R.id.tv_workout_name, workoutName)
        expandedViews.setTextViewText(R.id.tv_expanded_workout_name, workoutName)

        val statusText = when {
            state.isPaused -> "Workout Paused"
            state.restingExerciseId != null -> {
                val rem = state.restSecondsRemaining
                val exName = restingEx?.second?.name ?: "Exercise"
                "Resting: ${rem}s · Next: $exName"
            }
            state.activeExerciseId != null -> {
                val exName = activeEx?.second?.name ?: "Exercise"
                val logged = state.loggedSetsByExercise[state.activeExerciseId]?.size ?: 0
                val target = activeEx?.first?.targetSets ?: 3
                "Active: $exName · Set ${logged + 1}/$target"
            }
            else -> "Ready to start next exercise"
        }
        collapsedViews.setTextViewText(R.id.tv_exercise_status, statusText)
        expandedViews.setTextViewText(R.id.tv_expanded_exercise_status, statusText)

        // Set icons
        val iconRes = when {
            state.restingExerciseId != null -> android.R.drawable.ic_popup_sync // Timer indicator
            state.activeExerciseId != null -> android.R.drawable.ic_media_play // Active indicator
            else -> android.R.drawable.checkbox_off_background
        }
        collapsedViews.setImageViewResource(R.id.iv_status_icon, iconRes)
        expandedViews.setImageViewResource(R.id.iv_expanded_status_icon, iconRes)

        // ==========================================
        // 2. CONTEXT ACTION BUTTON VISIBILITIES (Collapsed)
        // ==========================================

        collapsedViews.setViewVisibility(R.id.btn_collapsed_start, View.GONE)
        collapsedViews.setViewVisibility(R.id.btn_collapsed_complete, View.GONE)
        collapsedViews.setViewVisibility(R.id.btn_collapsed_skip_rest, View.GONE)
        collapsedViews.setViewVisibility(R.id.btn_collapsed_next_exercise, View.GONE)

        when {
            state.isPaused -> {
                // Keep controls blank or handled in expanded
            }
            state.restingExerciseId != null -> {
                collapsedViews.setViewVisibility(R.id.btn_collapsed_skip_rest, View.VISIBLE)
            }
            state.activeExerciseId != null -> {
                collapsedViews.setViewVisibility(R.id.btn_collapsed_complete, View.VISIBLE)
            }
            else -> {
                // If there are still exercises, show Start Set
                val hasIncomplete = state.exercises.any { (pe, _) ->
                    (state.loggedSetsByExercise[pe.exerciseId]?.size ?: 0) < pe.targetSets
                }
                if (hasIncomplete) {
                    collapsedViews.setViewVisibility(R.id.btn_collapsed_start, View.VISIBLE)
                } else {
                    collapsedViews.setViewVisibility(R.id.btn_collapsed_next_exercise, View.VISIBLE)
                }
            }
        }

        // ==========================================
        // 3. EXPANDED VIEW DETAILED PANELS
        // ==========================================

        // Progress Bar
        val totalSets = state.exercises.sumOf { it.first.targetSets }
        val completedSets = state.loggedSets.size
        val progressPct = if (totalSets > 0) (completedSets * 100) / totalSets else 0
        val numBlocks = progressPct / 10
        val blockString = "█".repeat(numBlocks) + "░".repeat(10 - numBlocks)
        expandedViews.setTextViewText(R.id.tv_progress_bar_text, "Workout Progress $blockString $progressPct%")
        expandedViews.setProgressBar(R.id.pb_workout_progress, 100, progressPct, false)

        // Next Exercise Preview text
        val nextExName = nextEx?.second?.name ?: "None (Final exercise in progress)"
        expandedViews.setTextViewText(R.id.tv_next_exercise_preview, "Next: $nextExName")

        // Dynamic Primary Action button (Expanded)
        when {
            state.restingExerciseId != null -> {
                expandedViews.setTextViewText(R.id.btn_expanded_primary, "Skip Rest")
                expandedViews.setOnClickPendingIntent(R.id.btn_expanded_primary, createServicePendingIntent(ACTION_SKIP_REST))
            }
            state.activeExerciseId != null -> {
                expandedViews.setTextViewText(R.id.btn_expanded_primary, "Complete Set")
                expandedViews.setOnClickPendingIntent(R.id.btn_expanded_primary, createServicePendingIntent(ACTION_COMPLETE_SET))
            }
            else -> {
                expandedViews.setTextViewText(R.id.btn_expanded_primary, "Start Set")
                expandedViews.setOnClickPendingIntent(R.id.btn_expanded_primary, createServicePendingIntent(ACTION_START_SET))
            }
        }

        // Panels toggle
        if (state.restingExerciseId != null) {
            expandedViews.setViewVisibility(R.id.ll_rest_adjust_panel, View.VISIBLE)
            expandedViews.setViewVisibility(R.id.ll_quick_log_panel, View.GONE)
            expandedViews.setTextViewText(R.id.tv_rest_timer_display, "Rest remaining: ${state.restSecondsRemaining}s")
        } else {
            expandedViews.setViewVisibility(R.id.ll_rest_adjust_panel, View.GONE)
            expandedViews.setViewVisibility(R.id.ll_quick_log_panel, View.VISIBLE)

            // Setup Quick Entry variables
            expandedViews.setTextViewText(R.id.tv_weight_val, "${state.quickWeight} kg")
            expandedViews.setTextViewText(R.id.tv_reps_val, "${state.quickReps}")
        }

        // Toggle bottom Pause/Resume label
        if (state.isPaused) {
            expandedViews.setTextViewText(R.id.btn_pause_resume, "Resume")
            expandedViews.setOnClickPendingIntent(R.id.btn_pause_resume, createServicePendingIntent(ACTION_RESUME_WORKOUT))
        } else {
            expandedViews.setTextViewText(R.id.btn_pause_resume, "Pause")
            expandedViews.setOnClickPendingIntent(R.id.btn_pause_resume, createServicePendingIntent(ACTION_PAUSE_WORKOUT))
        }

        // ==========================================
        // 4. BIND PENDING INTENTS TO ACTIONS
        // ==========================================

        collapsedViews.setOnClickPendingIntent(R.id.btn_collapsed_start, createServicePendingIntent(ACTION_START_SET))
        collapsedViews.setOnClickPendingIntent(R.id.btn_collapsed_complete, createServicePendingIntent(ACTION_COMPLETE_SET))
        collapsedViews.setOnClickPendingIntent(R.id.btn_collapsed_skip_rest, createServicePendingIntent(ACTION_SKIP_REST))
        collapsedViews.setOnClickPendingIntent(R.id.btn_collapsed_next_exercise, createServicePendingIntent(ACTION_SKIP_EXERCISE))

        expandedViews.setOnClickPendingIntent(R.id.btn_rest_plus_15, createServicePendingIntent(ACTION_REST_ADD_15))
        expandedViews.setOnClickPendingIntent(R.id.btn_rest_plus_30, createServicePendingIntent(ACTION_REST_ADD_30))
        expandedViews.setOnClickPendingIntent(R.id.btn_rest_skip, createServicePendingIntent(ACTION_SKIP_REST))

        expandedViews.setOnClickPendingIntent(R.id.btn_weight_minus, createServicePendingIntent(ACTION_WEIGHT_DEC))
        expandedViews.setOnClickPendingIntent(R.id.btn_weight_plus, createServicePendingIntent(ACTION_WEIGHT_INC))
        expandedViews.setOnClickPendingIntent(R.id.btn_reps_minus, createServicePendingIntent(ACTION_REPS_DEC))
        expandedViews.setOnClickPendingIntent(R.id.btn_reps_plus, createServicePendingIntent(ACTION_REPS_INC))
        expandedViews.setOnClickPendingIntent(R.id.btn_save_set, createServicePendingIntent(ACTION_SAVE_SET))

        expandedViews.setOnClickPendingIntent(R.id.btn_prev_exercise, createServicePendingIntent(ACTION_PREV_EXERCISE))
        expandedViews.setOnClickPendingIntent(R.id.btn_skip_exercise, createServicePendingIntent(ACTION_SKIP_EXERCISE))
        expandedViews.setOnClickPendingIntent(R.id.btn_end_workout, createServicePendingIntent(ACTION_END_WORKOUT))

        // Open app when notification clicked
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ==========================================
        // 5. BUILD AND TRIGGER FOREGROUND NOTIFICATION
        // ==========================================

        val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ONGOING_NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    /**
     * Checks transitions to detect when the rest countdown reaches zero.
     * Triggers the high-priority alarm notification when complete.
     */
    private fun checkRestTimerFinished(state: ActiveSessionUiState) {
        val isResting = state.restingExerciseId != null
        
        if (!isResting && wasResting && state.restSecondsRemaining == 0) {
            triggerRestFinishedAlert()
        }

        wasResting = isResting
    }

    /**
     * Fires a high importance alarm notification to get the user's attention.
     */
    private fun triggerRestFinishedAlert() {
        val intentOpen = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 10, intentOpen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pendingStartSet = createServicePendingIntent(ACTION_START_SET)

        val alertNotification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Rest finished!")
            .setContentText("Start your next set to stay locked in.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_play, "Start Set", pendingStartSet)
            .addAction(android.R.drawable.ic_menu_myplaces, "Open Workout", pendingOpen)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun cancelAlertNotification() {
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
    }

    /**
     * Helper to create a service PendingIntent for notification actions.
     */
    private fun createServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, WorkoutSessionService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 
            action.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Configures the system notification channels.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Ongoing channel (Silent updates, low importance)
            val ongoingChannel = NotificationChannel(
                ONGOING_CHANNEL_ID,
                "Ongoing Workout Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays the current workout exercise status and real-time rest timer."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // High priority alerts channel (Sound and heads-up when rest completes)
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Rest Timer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers heads-up alerts immediately when a rest timer expires."
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(ongoingChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }
}
