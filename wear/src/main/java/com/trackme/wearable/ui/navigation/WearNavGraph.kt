package com.trackme.wearable.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.trackme.wearable.ui.screens.HomeScreen
import com.trackme.wearable.ui.screens.PermissionRationaleScreen
import com.trackme.wearable.ui.screens.WorkoutScreen
import com.trackme.wearable.viewmodel.WearSessionViewModel

object WearRoutes {
    const val HOME       = "home"
    const val WORKOUT    = "workout"
    const val PERMISSION = "permission"
}

@Composable
fun WearNavGraph(viewModel: WearSessionViewModel) {
    val navController = rememberSwipeDismissableNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME
    ) {
        composable(WearRoutes.HOME) {
            LaunchedEffect(uiState.session) {
                if (uiState.session != null) {
                    navController.navigate(WearRoutes.WORKOUT) {
                        launchSingleTop = true
                    }
                }
            }
            HomeScreen(
                uiState = uiState,
                onStartWorkout = {
                    navController.navigate(WearRoutes.WORKOUT) { launchSingleTop = true }
                },
            )
        }
        composable(WearRoutes.WORKOUT) {
            WorkoutScreen(
                viewModel = viewModel,
                onWorkoutFinished = { navController.popBackStack(WearRoutes.HOME, inclusive = false) }
            )
        }
        composable(WearRoutes.PERMISSION) {
            PermissionRationaleScreen(
                onPermissionGranted = { navController.popBackStack() },
                onSkip = { navController.popBackStack() }
            )
        }
    }
}
