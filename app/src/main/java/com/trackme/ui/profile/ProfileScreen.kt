package com.trackme.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.trackme.data.health.HcSdkStatus
import com.trackme.ui.components.*
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onViewHealthData: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onPermissionResult(granted) }

    Box(modifier = Modifier.fillMaxSize()) {
        ProfileMeshGradient(state = state.dashboardState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            InteractiveProfileHeader(state)

            GoalSanctuaryCard(
                currentGoal = state.fitnessGoal,
                onGoalSelected = { viewModel.savePreferences(state.useKg, it, state.inputStyle) }
            )

            HealthConnectPulseCard(
                state = state,
                onConnect = { permissionLauncher.launch(state.healthPermissions) },
                onSync = { viewModel.syncHealthConnect() },
                onViewHealthData = onViewHealthData,
                onManagePermissions = {
                    val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    runCatching { context.startActivity(intent) }
                },
                onInstall = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                    runCatching { context.startActivity(intent) }
                }
            )

            GlassmorphicPreferencesCard(
                state = state,
                onPrefChange = { useKg, style -> viewModel.savePreferences(useKg, state.fitnessGoal, style) }
            )

            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Coral.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out?", fontWeight = FontWeight.Bold) },
            text = { Text("Your training data is safe in the cloud. We'll be here when you return.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.signOut(onSignOut) },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel", color = OnSurfaceMuted) }
            },
            containerColor = SurfaceVariant,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun InteractiveProfileHeader(state: ProfileUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated Halo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.1f + (haloAlpha * 0.1f))
                    .border(2.dp, Violet.copy(alpha = haloAlpha), CircleShape)
            )
            
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Violet.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (state.avatarUrl != null) {
                    AsyncImage(
                        model = state.avatarUrl,
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        state.displayName.take(1).uppercase().ifEmpty { "?" },
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            state.displayName.ifEmpty { "Champion" },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            state.email,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun GoalSanctuaryCard(currentGoal: String, onGoalSelected: (String) -> Unit) {
    val goals = listOf(
        Triple("BUILD_MUSCLE", "The Athlete", Icons.Default.FitnessCenter),
        Triple("LOSE_WEIGHT", "The Shapeshifter", Icons.Default.Whatshot),
        Triple("IMPROVE_ENDURANCE", "The Vitality", Icons.AutoMirrored.Filled.DirectionsRun)
    )

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Your Path",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                goals.forEach { (key, label, icon) ->
                    val isSelected = currentGoal == key
                    val bgColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent
                    val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onGoalSelected(key) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Violet.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthConnectPulseCard(
    state: ProfileUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onViewHealthData: () -> Unit,
    onManagePermissions: () -> Unit,
    onInstall: () -> Unit
) {
    val isSyncing = state.isSyncing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSyncing) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Coral, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Health Data", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        when (state.hcStatus) {
                            HcSdkStatus.AVAILABLE -> if (state.healthConnectConnected) "Connected to Health Connect" else "Sync health data"
                            HcSdkStatus.NEEDS_UPDATE -> "Update Health Connect"
                            HcSdkStatus.NEEDS_INSTALL -> "Install Health Connect"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                when {
                    state.hcStatus != HcSdkStatus.AVAILABLE -> {
                        IconButton(onClick = onInstall) {
                            Icon(Icons.Default.Download, contentDescription = "Install", tint = Violet)
                        }
                    }
                    !state.healthConnectConnected -> {
                        Button(
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Violet.copy(alpha = 0.2f), contentColor = Violet),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Connect", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        IconButton(onClick = onSync, enabled = !isSyncing) {
                            if (isSyncing) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Violet, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Violet)
                            }
                        }
                    }
                }
            }

            if (state.lastSyncTime != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Last synced: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(state.lastSyncTime))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
                
                if (state.healthConnectConnected) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onViewHealthData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Violet.copy(alpha = 0.45f)),
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("View Health Metrics", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.hcStatus == HcSdkStatus.AVAILABLE) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onManagePermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Manage Permissions", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun GlassmorphicPreferencesCard(
    state: ProfileUiState,
    onPrefChange: (Boolean, String) -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Preferences", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            PreferenceRow(
                label = "Weight Unit",
                value = if (state.useKg) "Metric (kg)" else "Imperial (lbs)",
                icon = Icons.Default.Scale,
                trailing = {
                    Switch(
                        checked = state.useKg,
                        onCheckedChange = { onPrefChange(it, state.inputStyle) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Violet,
                            checkedTrackColor = Violet.copy(alpha = 0.3f),
                            uncheckedThumbColor = OnSurfaceMuted,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(Modifier.height(16.dp))

            PreferenceRow(
                label = "Input Style",
                value = if (state.inputStyle == "TAP_EXPAND") "Tap to Expand" else "Horizontal Rulers",
                icon = Icons.Default.EditAttributes,
                trailing = {
                    IconButton(onClick = { 
                        onPrefChange(state.useKg, if (state.inputStyle == "TAP_EXPAND") "HORIZONTAL" else "TAP_EXPAND")
                    }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Toggle", tint = Violet)
                    }
                }
            )
        }
    }
}

@Composable
private fun PreferenceRow(label: String, value: String, icon: ImageVector, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }
        trailing()
    }
}
