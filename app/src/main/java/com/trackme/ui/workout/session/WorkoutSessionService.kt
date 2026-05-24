package com.trackme.ui.workout.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trackme.R
import com.trackme.ui.workout.session.notification.SessionNotificationController
import com.trackme.ui.workout.session.notification.WorkoutNotificationActions
import com.trackme.ui.workout.session.notification.WorkoutNotificationActionHandler
import com.trackme.ui.workout.session.notification.WorkoutNotificationLogger
import com.trackme.ui.workout.session.notification.WorkoutNotificationRenderer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sticky foreground service that keeps the live workout notification alive in the background.
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject lateinit var sessionManager: WorkoutSessionManager
    @Inject lateinit var actionHandler: WorkoutNotificationActionHandler

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var notificationManager: NotificationManager
    private lateinit var renderer: WorkoutNotificationRenderer
    private lateinit var controller: SessionNotificationController

    companion object {
        const val ONGOING_NOTIFICATION_ID = 2026
        const val ALERT_NOTIFICATION_ID = 2027

        const val ONGOING_CHANNEL_ID = "workout_ongoing_channel"
        const val ALERTS_CHANNEL_ID = "workout_alerts_channel"

        /** @deprecated Use [WorkoutNotificationActions] */
        @Deprecated("Use WorkoutNotificationActions", ReplaceWith("WorkoutNotificationActions.ACTION_START_SERVICE"))
        const val ACTION_START_SERVICE = WorkoutNotificationActions.ACTION_START_SERVICE

        @Deprecated("Use WorkoutNotificationActions", ReplaceWith("WorkoutNotificationActions.ACTION_STOP_SERVICE"))
        const val ACTION_STOP_SERVICE = WorkoutNotificationActions.ACTION_STOP_SERVICE
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        renderer = WorkoutNotificationRenderer(this)
        controller = SessionNotificationController(
            context = this,
            sessionManager = sessionManager,
            notificationManager = notificationManager,
            renderer = renderer,
            onStopService = { stopSelf() },
            onStartForeground = { notification -> promoteForeground(notification) },
        )
        startForegroundWithPlaceholder()
        controller.start(serviceScope)
        WorkoutNotificationLogger.i("WorkoutSessionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WorkoutNotificationActions.ACTION_STOP_SERVICE -> {
                WorkoutNotificationLogger.i("Stop requested")
                stopSelf()
            }
            WorkoutNotificationActions.ACTION_SKIP_REST -> {
                serviceScope.launch {
                    actionHandler.handle(WorkoutNotificationActions.ACTION_SKIP_REST)
                    controller.cancelRestAlert()
                }
            }
            WorkoutNotificationActions.ACTION_START_SERVICE -> {
                WorkoutNotificationLogger.d("Service start acknowledged")
            }
            null -> Unit
            else -> {
                val action = intent.action ?: return START_STICKY
                serviceScope.launch {
                    actionHandler.handle(action)
                    if (action == WorkoutNotificationActions.ACTION_SKIP_REST) {
                        controller.cancelRestAlert()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        WorkoutNotificationLogger.i("WorkoutSessionService destroyed")
        serviceJob.cancel()
        renderer.reset()
        notificationManager.cancel(ONGOING_NOTIFICATION_ID)
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithPlaceholder() {
        val placeholder = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setContentTitle(getString(R.string.notification_starting_title))
            .setContentText(getString(R.string.notification_starting_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        promoteForeground(placeholder)
    }

    private fun promoteForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_NOTIFICATION_ID, notification, foregroundServiceType())
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            getString(R.string.notification_channel_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_ongoing_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alertsChannel = NotificationChannel(
            ALERTS_CHANNEL_ID,
            getString(R.string.notification_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_alerts_desc)
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(ongoingChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }
}
