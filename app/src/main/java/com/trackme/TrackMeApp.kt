package com.trackme

import android.app.Application
import com.trackme.sync.HealthSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrackMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HealthSyncWorker.schedule(this)
    }
}
