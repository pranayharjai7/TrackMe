package com.trackme.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.trackme.ui.navigation.Routes

private data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
)

@Composable
fun TrackMeBottomBar(navController: NavController, currentRoute: String?) {
    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home, Routes.Home.route),
        BottomNavItem("Workout", Icons.Filled.FitnessCenter, Routes.WeeklyPlanner.route),
        BottomNavItem("Progress", Icons.Filled.BarChart, Routes.Progress.route),
        BottomNavItem("Profile", Icons.Filled.Person, Routes.Profile.route),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
