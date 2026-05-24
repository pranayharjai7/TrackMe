package com.trackme

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.trackme.phone.wear.WearSyncInitializer
import com.trackme.sync.HealthSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class that wires Hilt and WorkManager.
 *
 * Architecture Layer: App configuration
 *
 * Responsibilities:
 * - Provide HiltWorkerFactory to WorkManager.
 * - Schedule periodic Health Connect sync once the app process starts.
 */
@HiltAndroidApp
class TrackMeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        HealthSyncWorker.schedule(this)
        WearSyncInitializer.start(this)
    }
}
