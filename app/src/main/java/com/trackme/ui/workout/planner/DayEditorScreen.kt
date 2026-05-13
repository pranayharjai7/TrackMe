package com.trackme.ui.workout.planner

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.ui.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(
    dayId: String,
    onAddExercise: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DayEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.reorderExercises(from.index, to.index)
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Day") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddExercise) {
                        Icon(Icons.Default.Add, "Add exercise", tint = Violet)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Violet)
                }
            }
            state.plannedExercises.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No exercises yet", color = OnSurfaceMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAddExercise, shape = RoundedCornerShape(16.dp)) {
                            Text("Add Exercise")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    items(
                        items = state.plannedExercises,
                        key = { it.first.id },
                    ) { (pe, exercise) ->
                        ReorderableItem(reorderState, key = pe.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
                            PlannedExerciseItem(
                                pe = pe,
                                exercise = exercise,
                                onExerciseClick = { exercise?.let { onExerciseClick(it.id) } },
                                onEdit = { viewModel.startEditExercise(pe) },
                                onRemove = { viewModel.removeExercise(pe) },
                                dragModifier = Modifier.draggableHandle(),
                                modifier = Modifier.shadow(elevation, RoundedCornerShape(20.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedExerciseItem(
    pe: PlannedExercise,
    exercise: Exercise?,
    onExerciseClick: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = OnSurfaceMuted,
                modifier = dragModifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = exercise != null, onClick = onExerciseClick)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    exercise?.name ?: pe.exerciseId,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                exercise?.let {
                    Text(
                        it.primaryMuscles.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Violet,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit params", tint = Violet)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Coral)
            }
        }
    }
}
