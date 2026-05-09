package com.trackme.ui.workout.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyPlannerScreen(
    onEditDay: (dayId: String) -> Unit,
    viewModel: WeeklyPlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var showAddDayDialog by remember { mutableStateOf(false) }
    var pendingDayOfWeek by remember { mutableStateOf<DayOfWeek?>(null) }
    var newDayName by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteDay by remember { mutableStateOf<WorkoutDay?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.activePlan?.name ?: "My Routine") },
                actions = {
                    IconButton(onClick = viewModel::showNewPlanDialog) {
                        Icon(Icons.Default.Add, "New plan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        if (state.activePlan == null) {
            EmptyPlanState(onCreatePlan = viewModel::showNewPlanDialog, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(DayOfWeek.entries, key = { it.name }) { dow ->
                    val day = state.days.firstOrNull { it.dayOfWeek == dow }
                    val count = day?.let { state.exerciseCounts[it.id] } ?: 0
                    DayCard(
                        dayOfWeek = dow,
                        workoutDay = day,
                        exerciseCount = count,
                        onClick = {
                            if (day != null) {
                                onEditDay(day.id)
                            } else {
                                pendingDayOfWeek = dow
                                newDayName = dow.name.lowercase().replaceFirstChar { it.uppercase() } + " Workout"
                                showAddDayDialog = true
                            }
                        },
                        onDelete = if (day != null) ({
                            pendingDeleteDay = day
                            showDeleteDialog = true
                        }) else null,
                    )
                }
            }
        }
    }

    if (showDeleteDialog && pendingDeleteDay != null) {
        val day = pendingDeleteDay!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; pendingDeleteDay = null },
            title = { Text("Delete ${day.name}?") },
            text = { Text("This will remove the workout day and all its exercises. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDay(day)
                        showDeleteDialog = false
                        pendingDeleteDay = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; pendingDeleteDay = null }) { Text("Cancel") }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
        )
    }

    if (showAddDayDialog && pendingDayOfWeek != null) {
        val dowLabel = pendingDayOfWeek!!.name.lowercase().replaceFirstChar { it.uppercase() }
        AlertDialog(
            onDismissRequest = { showAddDayDialog = false; newDayName = "" },
            title = { Text("Add $dowLabel Workout") },
            text = {
                OutlinedTextField(
                    value = newDayName,
                    onValueChange = { newDayName = it },
                    label = { Text("Day name (e.g. Push Day)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addDay(
                        pendingDayOfWeek!!,
                        newDayName.trim().ifEmpty { "$dowLabel Workout" },
                    )
                    showAddDayDialog = false
                    newDayName = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDayDialog = false; newDayName = "" }) { Text("Cancel") }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
        )
    }

    if (state.showNewPlanDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewPlanDialog,
            title = { Text("New Routine") },
            text = {
                OutlinedTextField(
                    value = state.newPlanName,
                    onValueChange = viewModel::onNewPlanNameChange,
                    label = { Text("Routine name (e.g. PPL)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                Button(onClick = viewModel::createPlan, enabled = !state.isCreatingPlan) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissNewPlanDialog) { Text("Cancel") } },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
}

@Composable
private fun DayCard(
    dayOfWeek: DayOfWeek,
    workoutDay: WorkoutDay?,
    exerciseCount: Int,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val dayLabel = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (workoutDay != null) Surface else Surface.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(if (workoutDay != null) 4.dp else 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = Violet, fontWeight = FontWeight.Bold)
                Text(
                    workoutDay?.name ?: "Rest Day",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (workoutDay != null) OnSurface else OnSurfaceMuted,
                )
                if (workoutDay != null && exerciseCount > 0) {
                    Text(
                        "$exerciseCount exercise${if (exerciseCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMuted,
                    )
                }
            }
            if (workoutDay != null) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete day",
                            tint = OnSurfaceMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet)
            }
        }
    }
}

@Composable
private fun EmptyPlanState(onCreatePlan: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No routine yet", style = MaterialTheme.typography.titleLarge, color = OnSurfaceMuted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCreatePlan, shape = RoundedCornerShape(16.dp)) {
                Text("Create My Routine")
            }
        }
    }
}
