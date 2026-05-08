package com.trackme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.trackme.ui.navigation.TrackMeNavGraph
import com.trackme.ui.theme.TrackMeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var seedExercises: com.trackme.domain.usecase.SeedExercisesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { seedExercises() }
        setContent {
            TrackMeTheme {
                TrackMeNavGraph()
            }
        }
    }
}
