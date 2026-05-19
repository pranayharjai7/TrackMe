package com.trackme.ui.workout.planner

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.ui.components.GlassmorphicCard
import com.trackme.ui.components.PlanningMeshGradient
import com.trackme.ui.components.rememberDeviceTilt
import com.trackme.ui.components.parallaxTilt
import com.trackme.ui.theme.*
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(
    onAddExercise: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DayEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tilt = rememberDeviceTilt()
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Index 0 is the Hero Header, skip reordering if trying to move above it
        if (to.index == 0) return@rememberReorderableLazyListState
        viewModel.reorderExercises(from.index - 1, to.index - 1)
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBarPadding + 24.dp

    state.editingExercise?.let { (pe, exercise) ->
        exercise?.let {
            TargetParamSheet(
                exercise = it,
                initial = pe,
                inputStyle = state.inputStyle,
                onConfirm = { sets, reps, weight, duration, distance, speed, incline ->
                    viewModel.saveEditedParams(pe, sets, reps, weight, duration, distance, speed, incline)
                },
                onDismiss = viewModel::dismissEdit,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlanningMeshGradient()

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 24.dp, bottom = bottomPadding, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    Modifier
                        .padding(bottom = 24.dp)
                        .parallaxTilt(tilt, intensity = 5f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text("Edit Routine", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "${state.plannedExercises.size} Exercises • ~${state.estimatedDurationMinutes} mins",
                            style = MaterialTheme.typography.labelLarge,
                            color = Coral,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            when {
                state.isLoading -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Violet)
                        }
                    }
                }
                state.plannedExercises.isEmpty() -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No exercises yet", color = Color.White.copy(alpha = 0.7f))
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = onAddExercise, 
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                                ) {
                                    Text("Add Exercise", color = Color.White)
                                }
                            }
                        }
                    }
                }
                else -> {
                    items(
                        items = state.plannedExercises,
                        key = { it.first.id },
                    ) { (pe, exercise) ->
                        ReorderableItem(reorderState, key = pe.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elev")
                            val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")
                            
                            LaunchedEffect(isDragging) {
                                if (isDragging) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                            
                            GlassmorphicCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .scale(scale)
                                    .parallaxTilt(tilt, intensity = 8f)
                                    .shadow(elevation, RoundedCornerShape(24.dp), clip = false),
                                containerColor = if (isDragging) Surface else Color.White.copy(alpha = 0.05f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .draggableHandle()
                                            .size(24.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    
                                    // Thumbnail / Icon
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (exercise != null && exercise.gifUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = exercise.gifUrl,
                                                contentDescription = exercise.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Text(
                                                text = getCategoryFallbackSymbol(exercise?.category),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(enabled = exercise != null, onClick = { exercise?.let { onExerciseClick(it.id) } })
                                            .padding(vertical = 4.dp),
                                    ) {
                                        Text(
                                            exercise?.name ?: pe.exerciseId,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        
                                        Spacer(Modifier.height(6.dp))
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Primary muscle tag
                                            if (exercise != null && exercise.primaryMuscles.isNotEmpty()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Violet.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = exercise.primaryMuscles.first().replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Violet,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            
                                            // Planned targets tag
                                            val targetsStr = formatPlannedTargets(pe)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.White.copy(alpha = 0.08f)
                                            ) {
                                                Text(
                                                    text = targetsStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.startEditExercise(pe) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit params", tint = Violet, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.removeExercise(pe) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Coral, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onAddExercise,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Violet.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Exercise", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryFallbackSymbol(category: String?): String {
    if (category == null) return "🏋️"
    return when (category.lowercase(Locale.ROOT)) {
        "chest" -> "💪"
        "back" -> "✈️"
        "legs", "quads", "hamstrings", "calves" -> "🦵"
        "shoulders" -> "🛡️"
        "arms", "biceps", "triceps", "forearms" -> "🦾"
        "abs", "core" -> "🧘"
        "cardio" -> "🏃"
        else -> "🏋️"
    }
}

private fun formatPlannedTargets(pe: PlannedExercise): String {
    val builder = mutableListOf<String>()
    
    if (pe.targetReps != null) {
        builder.add("${pe.targetSets} × ${pe.targetReps}")
    } else {
        builder.add("${pe.targetSets} sets")
    }

    if (pe.targetWeightKg != null && pe.targetWeightKg > 0) {
        builder.add("${pe.targetWeightKg} kg")
    }
    
    if (pe.targetDurationSeconds != null && pe.targetDurationSeconds > 0) {
        val mins = pe.targetDurationSeconds / 60
        val secs = pe.targetDurationSeconds % 60
        if (mins > 0 && secs > 0) {
            builder.add("${mins}m ${secs}s")
        } else if (mins > 0) {
            builder.add("${mins} mins")
        } else {
            builder.add("${secs} secs")
        }
    }
    
    if (pe.targetDistanceKm != null && pe.targetDistanceKm > 0) {
        builder.add("${pe.targetDistanceKm} km")
    }
    
    if (pe.targetSpeedKmh != null && pe.targetSpeedKmh > 0) {
        builder.add("${pe.targetSpeedKmh} km/h")
    }

    if (pe.targetIncline != null && pe.targetIncline > 0) {
        builder.add("${pe.targetIncline}% inc")
    }

    return builder.joinToString(" • ")
}
