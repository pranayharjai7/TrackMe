package com.trackme.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.ui.theme.*

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAlreadyDone) {
        if (state.isAlreadyDone) onComplete()
    }

    val goals = listOf(
        "LOSE_WEIGHT" to "Lose Weight",
        "BUILD_MUSCLE" to "Build Muscle",
        "MAINTAIN" to "Maintain",
        "IMPROVE_FITNESS" to "Improve Fitness",
    )

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.complete(onComplete)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Welcome to TrackMe",
            style = MaterialTheme.typography.displaySmall,
            color = Violet,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (state.currentStep == 1) "Let's set up your profile" else "Connect Health Data",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { i ->
                Surface(
                    modifier = Modifier.size(width = if (state.currentStep == i + 1) 24.dp else 8.dp, height = 8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (state.currentStep == i + 1) Violet else OnSurfaceMuted.copy(alpha = 0.3f),
                ) {}
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedContent(targetState = state.currentStep, label = "onboardingStep") { step ->
            when (step) {
                1 -> Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Preferred units", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(true to "Kilograms (kg)", false to "Pounds (lbs)").forEach { (isKg, label) ->
                                FilterChip(
                                    selected = state.useKg == isKg,
                                    onClick = { viewModel.setUnit(isKg) },
                                    label = { Text(label) },
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("Fitness goal", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            goals.forEach { (key, label) ->
                                FilterChip(
                                    selected = state.goal == key,
                                    onClick = { viewModel.setGoal(key) },
                                    label = { Text(label) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.nextStep() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Continue", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                else -> Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Coral,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Connect Health Connect",
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "TrackMe can read your weight, steps, and calories from Health Connect to track your body metrics alongside your workouts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceMuted,
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { permissionLauncher.launch(healthPermissions) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.isSaving,
                        ) {
                            Text("Connect Health Connect", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = { viewModel.complete(onComplete) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Skip for now", color = OnSurfaceMuted)
                        }
                    }
                }
            }
        }
    }
}
