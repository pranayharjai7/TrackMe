package com.trackme.wearable

import android.app.Application
import com.trackme.wearable.phone.HealthMetricsSender
import com.trackme.wearable.phone.PhoneConnectionManager
import com.trackme.wearable.phone.WorkoutStateSync

class TrackMeWearApplication : Application() {
    lateinit var runtime: TrackMeWearRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = TrackMeWearRuntime(this).also { it.start() }
    }
}

class TrackMeWearRuntime(application: Application) {
    val phoneConnectionManager = PhoneConnectionManager(application)
    val workoutStateSync = WorkoutStateSync(application, phoneConnectionManager)
    val healthMetricsSender = HealthMetricsSender(application, phoneConnectionManager, workoutStateSync)

    fun start() {
        phoneConnectionManager.start()
        workoutStateSync.start()
        healthMetricsSender.start()
    }
}
