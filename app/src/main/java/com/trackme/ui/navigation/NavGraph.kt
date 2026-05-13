package com.trackme.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trackme.ui.auth.AuthScreen
import com.trackme.ui.components.TrackMeBottomBar
import com.trackme.ui.health.HealthMetricsScreen
import com.trackme.ui.home.HomeScreen
import com.trackme.ui.onboarding.OnboardingScreen
import com.trackme.ui.profile.ProfileScreen
import com.trackme.ui.progress.ProgressScreen
import com.trackme.ui.theme.Background
import com.trackme.ui.theme.Violet
import com.trackme.ui.workout.exercise.ExerciseDetailScreen
import com.trackme.ui.workout.exercise.ExerciseSearchScreen
import com.trackme.ui.workout.planner.DayEditorScreen
import com.trackme.ui.workout.planner.WeeklyPlannerScreen
import com.trackme.ui.workout.session.ActiveSessionScreen

@Composable
fun TrackMeNavGraph(navViewModel: NavViewModel = hiltViewModel()) {
    val startDestination by navViewModel.startDestination.collectAsStateWithLifecycle()

    if (startDestination == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Violet)
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.Home.route, Routes.WeeklyPlanner.route,
        Routes.Progress.route, Routes.Profile.route,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Scaffold(
            containerColor = Background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    TrackMeBottomBar(navController = navController, currentRoute = currentRoute)
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination!!,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(innerPadding),
            ) {
            composable(Routes.Auth.route) {
                AuthScreen(
                    onAuthSuccess = { _ ->
                        navController.navigate(Routes.Onboarding.route) {
                            popUpTo(Routes.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.Home.route) {
                HomeScreen(
                    onStartSession = { dayId -> navController.navigate(Routes.ActiveSession.createRoute(dayId)) },
                    onResumeSession = { dayId -> navController.navigate(Routes.ActiveSession.createRoute(dayId)) },
                )
            }
            composable(Routes.WeeklyPlanner.route) {
                WeeklyPlannerScreen(
                    onEditDay = { dayId -> navController.navigate(Routes.DayEditor.createRoute(dayId)) }
                )
            }
            composable(
                route = Routes.DayEditor.route,
                arguments = listOf(navArgument("dayId") { type = NavType.StringType }),
            ) { backStack ->
                val dayId = backStack.arguments?.getString("dayId") ?: return@composable
                DayEditorScreen(
                    dayId = dayId,
                    onAddExercise = { navController.navigate(Routes.ExerciseSearch.createRoute(dayId)) },
                    onExerciseClick = { exerciseId -> navController.navigate(Routes.ExerciseDetail.createRoute(exerciseId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ExerciseSearch.route,
                arguments = listOf(navArgument("dayId") { type = NavType.StringType; defaultValue = "" }),
            ) { backStack ->
                val dayId = backStack.arguments?.getString("dayId") ?: ""
                ExerciseSearchScreen(
                    onExerciseClick = { exerciseId -> navController.navigate(Routes.ExerciseDetail.createRoute(exerciseId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ExerciseDetail.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
            ) { backStack ->
                val exerciseId = backStack.arguments?.getString("exerciseId") ?: return@composable
                ExerciseDetailScreen(
                    exerciseId = exerciseId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ActiveSession.route,
                arguments = listOf(navArgument("dayId") { type = NavType.StringType }),
            ) { backStack ->
                val dayId = backStack.arguments?.getString("dayId") ?: return@composable
                ActiveSessionScreen(
                    dayId = dayId,
                    onSessionFinished = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onExerciseClick = { exerciseId -> navController.navigate(Routes.ExerciseDetail.createRoute(exerciseId)) },
                    onAddExercise = { navController.navigate(Routes.ExerciseSearch.createRoute(dayId)) }
                )
            }
            composable(Routes.Progress.route) { ProgressScreen() }
            composable(Routes.Profile.route) {
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Routes.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onViewHealthData = { navController.navigate(Routes.HealthMetrics.route) },
                )
            }
            composable(Routes.HealthMetrics.route) {
                HealthMetricsScreen(onBack = { navController.popBackStack() })
            }
            }
        }
    }
}
