package com.trackme.wearable.ui

import androidx.compose.runtime.Composable
import com.trackme.wearable.ui.navigation.WearNavGraph
import com.trackme.wearable.viewmodel.WearSessionViewModel

@Composable
fun TrackMeWearApp(viewModel: WearSessionViewModel) {
    WearNavGraph(viewModel = viewModel)
}
