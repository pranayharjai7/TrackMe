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

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { /* permissions updated — ViewModel will re-check on next launch */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Violet.copy(alpha = 0.08f), Background)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
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
                                when {
                                    !state.healthConnectAvailable -> "Not available on this device"
                                    state.healthConnectConnected -> "Connected"
                                    else -> "Not connected"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.healthConnectConnected) Teal else OnSurfaceMuted,
                            )
                        }
                        if (state.healthConnectAvailable && !state.healthConnectConnected) {
                            TextButton(onClick = { permissionLauncher.launch(healthPermissions) }) {
                                Text("Connect", color = Violet)
                            }
                        } else if (state.healthConnectConnected) {
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

                    HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.1f))

                    state.lastSyncTime?.let { syncTime ->
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
