package com.trackme.ui.workout.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.domain.model.Exercise
import com.trackme.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSearchScreen(
    onExerciseClick: (exerciseId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var queryText by remember { mutableStateOf("") }
    val addingForDay = viewModel.dayId.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.addedEvent.collect { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (addingForDay) "Add Exercise" else "Browse Exercises") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    viewModel.onQueryChange(it)
                },
                placeholder = { Text("Search exercises, muscles…", color = OnSurfaceMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.id }) { exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        addingForDay = addingForDay,
                        onAdd = { viewModel.addExercise(exercise.id) },
                        onInfo = { onExerciseClick(exercise.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseListItem(
    exercise: Exercise,
    addingForDay: Boolean,
    onAdd: () -> Unit,
    onInfo: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    exercise.primaryMuscles.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Violet,
                )
                Text(exercise.equipment, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            }
            if (addingForDay) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add to day", tint = Violet)
                }
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Default.Info, contentDescription = "View details", tint = OnSurfaceMuted)
            }
        }
    }
}
