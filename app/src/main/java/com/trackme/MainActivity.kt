package com.trackme

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.trackme.data.auth.SessionManager
import com.trackme.data.auth.SessionState
import com.trackme.domain.usecase.SeedExercisesUseCase
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
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var seedExercises: SeedExercisesUseCase
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            }

            override fun onStop(owner: LifecycleOwner) {
                sessionManager.stopHeartbeat()
            }
        })
        setContent {
            TrackMeTheme {
                val sessionState by sessionManager.sessionState.collectAsState()

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
