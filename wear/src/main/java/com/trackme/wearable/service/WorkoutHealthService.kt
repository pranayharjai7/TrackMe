package com.trackme.wearable.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trackme.wearable.TrackMeWearApplication

class WorkoutHealthService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        (application as TrackMeWearApplication).runtime.healthMetricsSender.start()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("TrackMe workout")
                .setContentText("Streaming workout metrics")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "TrackMe workout metrics",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "trackme_wear_workout_metrics"
        const val NOTIFICATION_ID = 4104
    }
}
