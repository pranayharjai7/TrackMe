package com.trackme.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.phone.wear.WatchConnectionState
import com.trackme.phone.wear.WatchSyncDebugState
import com.trackme.ui.components.GlassmorphicCard
import com.trackme.ui.profile.ProfileViewModel
import com.trackme.ui.theme.Coral
import com.trackme.ui.theme.Teal
import com.trackme.ui.theme.Violet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearOsSettingsScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connection = state.watchConnectionState
    val connected = connection is WatchConnectionState.Connected
    val debug = state.watchDebugState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wear OS", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color(0xFF111118),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WatchStatusCard(connection = connection, lastSync = debug.lastSyncTime)
            SyncControlsCard(
                connected = connected,
                syncing = state.isWatchSyncing,
                onSyncWorkout = viewModel::syncWorkoutDataToWatch,
                onSyncHealth = viewModel::syncWearHealthData,
                onReconnect = viewModel::reconnectWatch,
                onPing = viewModel::pingWatch,
                onTest = viewModel::testWatchCommunication,
            )
            HealthMetricsCard(debug = debug)
            DebugCard(
                nodeId = (connection as? WatchConnectionState.Connected)?.nodeId ?: debug.watchNodeId,
                lastSent = debug.lastMessageSent,
                lastReceived = debug.lastMessageReceived,
                eventIndex = debug.lastEventIndex,
                latency = viewModel.watchPingLatencyMs.collectAsStateWithLifecycle().value,
            )
        }
    }
}

@Composable
private fun WatchStatusCard(connection: WatchConnectionState, lastSync: Long?) {
    val statusLabel = when (connection) {
        WatchConnectionState.Connecting -> "Connecting"
        WatchConnectionState.Disconnected -> "Disconnected"
        WatchConnectionState.Syncing -> "Syncing"
        is WatchConnectionState.Connected -> "Connected"
        is WatchConnectionState.Error -> "Error"
    }
    val statusColor = when (connection) {
        is WatchConnectionState.Connected -> Teal
        WatchConnectionState.Syncing -> Violet
        is WatchConnectionState.Error -> Coral
        else -> Coral
    }
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Watch, contentDescription = null, tint = Violet)
                Spacer(Modifier.width(12.dp))
                Text("Watch status", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
            SettingLine("Connection", statusLabel, statusColor)
            if (connection is WatchConnectionState.Connected) {
                SettingLine("Model", connection.watchModel ?: connection.nodeName)
                SettingLine("Watch app", if (connection.appInstalled) "Detected" else "Pair only — open watch app")
                connection.batteryPercent?.let { SettingLine("Battery", "$it%") }
                connection.wearOsVersion?.let { SettingLine("Wear OS", it) }
                SettingLine("Workout", if (connection.activeWorkout) "Active" else "Idle")
            }
            if (connection is WatchConnectionState.Error) {
                SettingLine("Error", connection.message, Coral)
            }
            SettingLine(
                "Last sync",
                lastSync?.let { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "Never",
            )
        }
    }
}

@Composable
private fun SyncControlsCard(
    connected: Boolean,
    syncing: Boolean,
    onSyncWorkout: () -> Unit,
    onSyncHealth: () -> Unit,
    onReconnect: () -> Unit,
    onPing: () -> Unit,
    onTest: () -> Unit,
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sync controls", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSyncWorkout,
                    enabled = connected && !syncing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet.copy(alpha = 0.3f)),
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Workout")
                }
                Button(
                    onClick = onSyncHealth,
                    enabled = connected && !syncing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal.copy(alpha = 0.25f)),
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Health")
                }
            }
            OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth(), enabled = !syncing) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Force reconnect")
            }
            OutlinedButton(onClick = onPing, modifier = Modifier.fillMaxWidth(), enabled = connected && !syncing) {
                Icon(Icons.Default.NetworkPing, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ping watch")
            }
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth(), enabled = connected && !syncing) {
                Text("Test communication")
            }
        }
    }
}

@Composable
private fun HealthMetricsCard(debug: WatchSyncDebugState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Health stream", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            SettingLine("Heart rate", debug.lastHeartRateBpm?.let { "${it.toInt()} bpm" } ?: "—")
            SettingLine("Calories", debug.lastCaloriesKcal?.let { "${it.toInt()} kcal" } ?: "—")
            SettingLine("Steps", debug.lastSteps?.let { it.toInt().toString() } ?: "—")
            SettingLine(
                "Active duration",
                debug.lastActiveDurationSeconds?.let { formatDuration(it) } ?: "—",
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val rem = seconds % 60
    return if (minutes > 0) "${minutes}m ${rem}s" else "${rem}s"
}

@Composable
private fun DebugCard(
    nodeId: String?,
    lastSent: String,
    lastReceived: String,
    eventIndex: Long,
    latency: Long?,
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Debug", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            SettingLine("Node", nodeId ?: "—")
            SettingLine("Ping latency", latency?.let { "${it}ms" } ?: "—")
            SettingLine("Last sent", lastSent)
            SettingLine("Last received", lastReceived)
            SettingLine("Event index", eventIndex.toString())
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String, valueColor: Color = Color.White.copy(alpha = 0.8f)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.width(110.dp), style = MaterialTheme.typography.labelMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.labelMedium, maxLines = 2)
    }
}
