package com.trackme.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import com.trackme.data.health.HcSdkStatus
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onPermissionResult(granted) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Violet.copy(alpha = 0.08f), Background)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Violet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.displayName.take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.displaySmall,
                    color = Violet,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(12.dp))
            if (state.displayName.isNotEmpty()) {
                Text(state.displayName, style = MaterialTheme.typography.titleLarge, color = OnBackground, fontWeight = FontWeight.Bold)
            }
            Text(state.email, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)

            Spacer(Modifier.height(32.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Health Connect", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                when (state.hcStatus) {
                                    HcSdkStatus.AVAILABLE -> if (state.healthConnectConnected) "Connected" else "Not connected"
                                    HcSdkStatus.NEEDS_UPDATE -> "App update required"
                                    HcSdkStatus.NEEDS_INSTALL -> "Not installed"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    state.healthConnectConnected -> Teal
                                    state.hcStatus == HcSdkStatus.AVAILABLE -> OnSurfaceMuted
                                    else -> Coral
                                },
                            )
                        }
                        when {
                            state.hcStatus == HcSdkStatus.NEEDS_INSTALL || state.hcStatus == HcSdkStatus.NEEDS_UPDATE -> {
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                                    runCatching { context.startActivity(intent) }
                                }) {
                                    Text(
                                        if (state.hcStatus == HcSdkStatus.NEEDS_INSTALL) "Install" else "Update",
                                        color = Violet,
                                    )
                                }
                            }
                            state.hcStatus == HcSdkStatus.AVAILABLE && !state.healthConnectConnected -> {
                                TextButton(onClick = { permissionLauncher.launch(healthPermissions) }) {
                                    Text("Connect", color = Violet)
                                }
                            }
                            state.healthConnectConnected -> {
                                IconButton(
                                    onClick = { viewModel.syncHealthConnect() },
                                    enabled = !state.isSyncing,
                                ) {
                                    if (state.isSyncing) {
                                        CircularProgressIndicator(Modifier.size(20.dp), color = Violet, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Violet)
                                    }
                                }
                            }
                        }
                    }

                    state.lastSyncTime?.let { syncTime ->
                        HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Last synced: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(syncTime))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceMuted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Weight Unit", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                if (state.useKg) "Kilograms (kg)" else "Pounds (lbs)",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceMuted,
                            )
                        }
                        Switch(
                            checked = state.useKg,
                            onCheckedChange = { viewModel.savePreferences(it, state.fitnessGoal) },
                        )
                    }

                    HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.1f))

                    Column {
                        Text("Fitness Goal", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Spacer(Modifier.height(8.dp))
                        val goals = listOf(
                            "BUILD_MUSCLE" to "Build Muscle",
                            "LOSE_WEIGHT" to "Lose Weight",
                            "IMPROVE_ENDURANCE" to "Improve Endurance",
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            goals.forEach { (key, label) ->
                                FilterChip(
                                    selected = state.fitnessGoal == key,
                                    onClick = { viewModel.savePreferences(state.useKg, key) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Violet.copy(alpha = 0.2f),
                                        selectedLabelColor = Violet,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Coral)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", color = Coral)
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out?") },
            text = { Text("Your data is synced to the cloud. You can sign back in anytime.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.signOut(onSignOut) },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
}
