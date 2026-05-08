package com.trackme.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.ui.theme.*

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val goals = listOf(
        "LOSE_WEIGHT" to "Lose Weight",
        "BUILD_MUSCLE" to "Build Muscle",
        "MAINTAIN" to "Maintain",
        "IMPROVE_FITNESS" to "Improve Fitness",
    )

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
            "Let's set up your profile",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 40.dp),
        )

        Card(
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
                    onClick = { viewModel.complete(onComplete) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !state.isSaving,
                ) {
                    Text("Let's Go", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
