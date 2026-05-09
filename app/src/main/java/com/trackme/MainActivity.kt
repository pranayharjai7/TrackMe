package com.trackme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.trackme.ui.navigation.TrackMeNavGraph
import com.trackme.ui.theme.TrackMeTheme
import com.trackme.domain.usecase.SeedExercisesUseCase
import com.trackme.sync.SyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var seedExercises: SeedExercisesUseCase
    @Inject lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { seedExercises() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                syncManager.enqueueImmediateSync()
                syncManager.schedulePeriodicSync()
            }
        })
        setContent {
            TrackMeTheme {
                TrackMeNavGraph()
            }
        }
    }
}
