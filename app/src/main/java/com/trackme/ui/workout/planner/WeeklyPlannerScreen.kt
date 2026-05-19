package com.trackme.ui.workout.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.ui.components.GlassmorphicCard
import com.trackme.ui.components.PlanningMeshGradient
import com.trackme.ui.components.rememberDeviceTilt
import com.trackme.ui.components.parallaxTilt
import com.trackme.ui.theme.*

@Composable
fun WeeklyPlannerScreen(
    onEditDay: (dayId: String) -> Unit,
    viewModel: WeeklyPlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = 96.dp + navBarPadding + 24.dp

    var showAddDayDialog by remember { mutableStateOf(false) }
    var pendingDayOfWeek by remember { mutableStateOf<DayOfWeek?>(null) }
    var newDayName by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteDay by remember { mutableStateOf<WorkoutDay?>(null) }

    val context = LocalContext.current
    LaunchedEffect(state.routineTextToShare) {
        state.routineTextToShare?.let { text ->
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)
            viewModel.onRoutineShared()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlanningMeshGradient()

        if (state.activePlan == null) {
            EmptyPlanState(onCreatePlan = viewModel::showNewPlanDialog)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(top = 24.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    PlannerHeroHeader(
                        planName = state.activePlan?.name ?: "My Routine",
                        activeDaysCount = state.days.size,
                        onShare = viewModel::prepareRoutineForSharing,
                        onCreatePlan = viewModel::showNewPlanDialog
                    )
                }

                // Mon & Tue
                item {
                    DayPairRow(
                        dow1 = DayOfWeek.MON,
                        dow2 = DayOfWeek.TUE,
                        state = state,
                        onEditDay = onEditDay,
                        onAddDay = { dow, defaultName ->
                            pendingDayOfWeek = dow
                            newDayName = defaultName
                            showAddDayDialog = true
                        },
                        onDeleteDay = { day ->
                            pendingDeleteDay = day
                            showDeleteDialog = true
                        }
                    )
                }

                // Wed & Thu
                item {
                    DayPairRow(
                        dow1 = DayOfWeek.WED,
                        dow2 = DayOfWeek.THU,
                        state = state,
                        onEditDay = onEditDay,
                        onAddDay = { dow, defaultName ->
                            pendingDayOfWeek = dow
                            newDayName = defaultName
                            showAddDayDialog = true
                        },
                        onDeleteDay = { day ->
                            pendingDeleteDay = day
                            showDeleteDialog = true
                        }
                    )
                }

                // Fri & Sat
                item {
                    DayPairRow(
                        dow1 = DayOfWeek.FRI,
                        dow2 = DayOfWeek.SAT,
                        state = state,
                        onEditDay = onEditDay,
                        onAddDay = { dow, defaultName ->
                            pendingDayOfWeek = dow
                            newDayName = defaultName
                            showAddDayDialog = true
                        },
                        onDeleteDay = { day ->
                            pendingDeleteDay = day
                            showDeleteDialog = true
                        }
                    )
                }

                // Sun & Summary
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val sunDow = DayOfWeek.SUN
                        val sunDay = state.days.find { it.dayOfWeek == sunDow }
                        val sunCount = state.exerciseCounts[sunDay?.id] ?: 0
                        Box(modifier = Modifier.weight(1f)) {
                            GlassDayCard(
                                dayOfWeek = sunDow,
                                workoutDay = sunDay,
                                exerciseCount = sunCount,
                                onClick = {
                                    if (sunDay != null) {
                                        onEditDay(sunDay.id)
                                    } else {
                                        pendingDayOfWeek = sunDow
                                        newDayName = sunDow.name.lowercase().replaceFirstChar { it.uppercase() } + " Workout"
                                        showAddDayDialog = true
                                    }
                                },
                                onDelete = if (sunDay != null) ({
                                    pendingDeleteDay = sunDay
                                    showDeleteDialog = true
                                }) else null
                            )
                        }

                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Dialogs (Moved outside Scaffold to ensure they appear on top)
    if (showDeleteDialog) {
        pendingDeleteDay?.let { day ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; pendingDeleteDay = null },
                title = { Text("Delete ${day.name}?", color = OnSurface) },
                text = { Text("This will remove the workout day and all its exercises. This cannot be undone.", color = OnSurfaceMuted) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteDay(day)
                            showDeleteDialog = false
                            pendingDeleteDay = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    ) { Text("Delete", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; pendingDeleteDay = null }) { Text("Cancel", color = OnSurface) }
                },
                containerColor = Surface,
                shape = RoundedCornerShape(24.dp),
            )
        }
    }

    if (showAddDayDialog) {
        pendingDayOfWeek?.let { dayOfWeek ->
            val dowLabel = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            AlertDialog(
                onDismissRequest = { showAddDayDialog = false; newDayName = "" },
                title = { Text("Add $dowLabel Workout", color = OnSurface) },
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
                    Button(
                        onClick = {
                            viewModel.addDay(
                                dayOfWeek,
                                newDayName.trim().ifEmpty { "$dowLabel Workout" },
                            )
                            showAddDayDialog = false
                            newDayName = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet)
                    ) { Text("Add", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDayDialog = false; newDayName = "" }) { Text("Cancel", color = OnSurface) }
                },
                containerColor = Surface,
                shape = RoundedCornerShape(24.dp),
            )
        }
    }

    if (state.showNewPlanDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewPlanDialog,
            title = { Text("New Routine", color = OnSurface) },
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
                Button(
                    onClick = viewModel::createPlan,
                    enabled = !state.isCreatingPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissNewPlanDialog) { Text("Cancel", color = OnSurface) } },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
}
@Composable
private fun PlannerHeroHeader(
    planName: String,
    activeDaysCount: Int,
    onShare: () -> Unit,
    onCreatePlan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Routine Planner",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Normal,
            )
            Text(
                planName,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.1f),
            ) {
                Text(
                    "$activeDaysCount Workout Days • ${7 - activeDaysCount} Rest Days",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // New Plan / Create Plan Button
            IconButton(
                onClick = onCreatePlan,
                modifier = Modifier
                    .size(48.dp)
                    .background(Violet.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, Violet.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Routine", tint = Color.White)
            }

            // Share Button
            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share Routine", tint = Color.White)
            }
        }
    }
}
@Composable
private fun DayPairRow(
    dow1: DayOfWeek,
    dow2: DayOfWeek,
    state: WeeklyPlannerUiState,
    onEditDay: (String) -> Unit,
    onAddDay: (DayOfWeek, String) -> Unit,
    onDeleteDay: (WorkoutDay) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val day1 = state.days.find { it.dayOfWeek == dow1 }
        val count1 = state.exerciseCounts[day1?.id] ?: 0
        Box(modifier = Modifier.weight(1f)) {
            GlassDayCard(
                dayOfWeek = dow1,
                workoutDay = day1,
                exerciseCount = count1,
                onClick = {
                    if (day1 != null) {
                        onEditDay(day1.id)
                    } else {
                        onAddDay(dow1, dow1.name.lowercase().replaceFirstChar { it.uppercase() } + " Workout")
                    }
                },
                onDelete = if (day1 != null) ({ onDeleteDay(day1) }) else null
            )
        }

        val day2 = state.days.find { it.dayOfWeek == dow2 }
        val count2 = state.exerciseCounts[day2?.id] ?: 0
        Box(modifier = Modifier.weight(1f)) {
            GlassDayCard(
                dayOfWeek = dow2,
                workoutDay = day2,
                exerciseCount = count2,
                onClick = {
                    if (day2 != null) {
                        onEditDay(day2.id)
                    } else {
                        onAddDay(dow2, dow2.name.lowercase().replaceFirstChar { it.uppercase() } + " Workout")
                    }
                },
                onDelete = if (day2 != null) ({ onDeleteDay(day2) }) else null
            )
        }
    }
}

@Composable
private fun GlassDayCard(
    dayOfWeek: DayOfWeek,
    workoutDay: WorkoutDay?,
    exerciseCount: Int,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val dayLabel = dayOfWeek.name.substring(0, 3).uppercase()
    
    GlassmorphicCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: Day abbreviation (MON, TUE) & Action (Delete icon / status icon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (workoutDay != null) Violet else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                
                if (workoutDay != null && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete day",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (workoutDay == null) {
                    Text("🛌", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Middle section: Workout Name
            Text(
                text = workoutDay?.name ?: "Rest Day",
                style = MaterialTheme.typography.titleMedium,
                color = if (workoutDay != null) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.height(44.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Bottom section: Stats or Action Call
            if (workoutDay != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (exerciseCount > 0) Violet.copy(alpha = 0.15f) else Coral.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (exerciseCount > 0) {
                            "$exerciseCount Exercise${if (exerciseCount == 1) "" else "s"}"
                        } else {
                            "Add Exercises"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (exerciseCount > 0) Color.White.copy(alpha = 0.9f) else Coral,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = "Recover",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun EmptyPlanState(onCreatePlan: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No routine yet", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCreatePlan, 
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 32.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            ) {
                Text("Create My Routine", fontWeight = FontWeight.Bold)
            }
        }
    }
}
