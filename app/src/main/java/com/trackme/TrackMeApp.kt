package com.trackme

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.trackme.phone.wear.WearSyncInitializer
import com.trackme.sync.HealthSyncWorker
import com.trackme.utils.globalExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Application class that wires Hilt, WorkManager, Firebase, and global error handling.
 *
 * Architecture Layer: App configuration
 *
 * Responsibilities:
 * - Provide HiltWorkerFactory to WorkManager.
 * - Schedule periodic Health Connect sync once the app process starts.
 * - Initialize Firebase Crashlytics and set up global uncaught exception reporting.
 * - Expose an application-scoped coroutine scope with a global exception handler
 *   so all background work in the app shares consistent crash reporting.
 */
@HiltAndroidApp
class TrackMeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Application-wide coroutine scope protected by a SupervisorJob so individual child
     * failures do not cancel the entire scope. All unhandled exceptions are forwarded to
     * Firebase Crashlytics via [globalExceptionHandler].
     */
    val appScope = CoroutineScope(SupervisorJob() + globalExceptionHandler("TrackMeApp"))

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase and configure Crashlytics
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Set up the default uncaught exception handler to capture anything
        // that falls through coroutine scopes (e.g. from Wear bridge threads)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("TrackMeApp", "Uncaught exception on thread [${thread.name}]", throwable)
            runCatching {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        HealthSyncWorker.schedule(this)
        WearSyncInitializer.start(this)
    }
}
