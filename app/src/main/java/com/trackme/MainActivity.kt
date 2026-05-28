package com.trackme

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.trackme.data.auth.SessionManager
import com.trackme.data.auth.SessionState
import com.trackme.domain.usecase.SeedExercisesUseCase
import com.trackme.phone.wear.WatchConnectionManager
import com.trackme.phone.wear.WatchSyncRepository
import com.trackme.ui.workout.session.notification.WorkoutNotificationBootstrap
import com.trackme.sync.SyncManager
import com.trackme.ui.components.SyncingLogoutOverlay
import com.trackme.ui.components.TerminatedOverlay
import com.trackme.ui.navigation.TrackMeNavGraph
import com.trackme.ui.theme.TrackMeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Android entry point for the Compose app.
 *
 * Architecture Layer: UI host
 *
 * Responsibilities:
 * - Configure system bars and Compose content.
 * - Seed bundled exercise data at startup.
 * - Schedule sync when the process enters foreground.
 * - Orchestrate background session heartbeat loop and render forced logout overlays.
 * - Request runtime notification permissions on Android 13+.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var seedExercises: SeedExercisesUseCase
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var watchConnectionManager: WatchConnectionManager
    @Inject lateinit var watchSyncRepository: WatchSyncRepository
    @Inject lateinit var workoutNotificationBootstrap: WorkoutNotificationBootstrap

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("MainActivity", "Notification permission request result: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permission)
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(17, 17, 24)))
        lifecycleScope.launch { seedExercises() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                syncManager.enqueueImmediateSync()
                syncManager.schedulePeriodicSync()
                sessionManager.startHeartbeat()
                watchConnectionManager.start()
                watchSyncRepository.start()
                workoutNotificationBootstrap.recoverIfNeeded()
            }

            override fun onStop(owner: LifecycleOwner) {
                sessionManager.stopHeartbeat()
            }
        })
        setContent {
            TrackMeTheme {
                val sessionState by sessionManager.sessionState.collectAsStateWithLifecycle()

                TrackMeNavGraph()

                when (sessionState) {
                    is SessionState.SyncingBeforeLogout -> SyncingLogoutOverlay()
                    is SessionState.Terminated -> TerminatedOverlay()
                    else -> { /* No overlays required for normal Active or LoggedOut states */ }
                }
            }
        }
    }
}
