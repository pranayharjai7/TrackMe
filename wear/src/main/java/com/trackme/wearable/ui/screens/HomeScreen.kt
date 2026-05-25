package com.trackme.wearable.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trackme.wearable.viewmodel.WearUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: WearUiState,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> ReadinessScreen(uiState = uiState)
            1 -> WorkoutHubScreen(uiState = uiState, onStartWorkout = onStartWorkout)
        }
    }
}
