package com.trackme.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.SessionSet
import com.trackme.domain.usecase.MuscleVolume
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val strengthHistory by viewModel.strengthHistory.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blue.copy(alpha = 0.06f), Background)))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Progress",
                    style = MaterialTheme.typography.displaySmall,
                    color = OnBackground,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            state.latestSnapshot?.let { snapshot ->
                item { BodyStatsCard(snapshot) }
            }

            if (state.weightHistory.count { it.weightKg != null } >= 2) {
                item { WeightChartCard(state.weightHistory) }
            }

            if (state.sessionVolumes.isNotEmpty()) {
                item { WorkoutCalendarCard(state.sessionVolumes) }
            }

            if (state.exerciseOptions.isNotEmpty()) {
                item {
                    StrengthChartCard(
                        history = strengthHistory,
                        exerciseOptions = state.exerciseOptions,
                        selectedExerciseId = state.selectedExerciseId,
                        onSelectExercise = viewModel::selectExercise,
                    )
                }
            }

            if (state.muscleVolume.isNotEmpty()) {
                item { MuscleVolumeCard(state.muscleVolume) }
            }

            if (state.personalRecords.isNotEmpty()) {
                item {
                    Text(
                        "Personal Records",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.personalRecords, key = { it.id }) { pr ->
                    PersonalRecordItem(pr)
                }
            }
        }
    }
}

@Composable
private fun WorkoutCalendarCard(sessionVolumes: Map<Long, Int>) {
    val days = (29 downTo 0).map { daysAgo ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Workout History", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text("Last 30 days  ·  darker = more sets", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { dayMs ->
                            val sets = sessionVolumes[dayMs] ?: 0
                            val alpha = when {
                                sets == 0 -> 0f
                                sets <= 3 -> 0.3f
                                sets <= 7 -> 0.6f
                                else -> 1.0f
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = if (alpha == 0f) OnSurfaceMuted.copy(alpha = 0.1f)
                                                else Violet.copy(alpha = alpha),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                            )
                        }
                        repeat(7 - week.size) {
                            Box(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                listOf(0.1f, 0.3f, 0.6f, 1.0f).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Violet.copy(alpha = alpha), RoundedCornerShape(3.dp))
                    )
                }
                Text("More", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrengthChartCard(
    history: List<SessionSet>,
    exerciseOptions: List<String>,
    selectedExerciseId: String?,
    onSelectExercise: (String) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val sortedSets = history.filter { it.completed && it.weightKg > 0f }.sortedBy { it.updatedAt }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Strength Trend", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            if (exerciseOptions.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedExerciseId?.replaceFirstChar { it.uppercase() } ?: "Select exercise",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exercise") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        exerciseOptions.forEach { exerciseId ->
                            DropdownMenuItem(
                                text = { Text(exerciseId.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onSelectExercise(exerciseId)
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (sortedSets.size >= 2) {
                val producer = remember(sortedSets) { ChartEntryModelProducer() }
                LaunchedEffect(sortedSets) {
                    producer.setEntries(
                        sortedSets.mapIndexed { index, set ->
                            FloatEntry(x = index.toFloat(), y = set.weightKg)
                        }
                    )
                }
                Chart(
                    chart = lineChart(),
                    chartModelProducer = producer,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Log more sets to see your trend", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                }
            }
        }
    }
}

@Composable
private fun MuscleVolumeCard(muscleVolume: List<MuscleVolume>) {
    if (muscleVolume.isEmpty()) return
    val maxSets = muscleVolume.maxOf { it.totalSets }.toFloat()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Muscle Volume", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text("Sets per muscle (last 4 weeks)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                muscleVolume.forEach { mv ->
                    val fraction = if (maxSets > 0f) mv.totalSets / maxSets else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            mv.muscle.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                            modifier = Modifier.width(80.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(OnSurfaceMuted.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .background(Teal.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${mv.totalSets}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyStatsCard(snapshot: HealthSnapshot) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Body Stats", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem(label = "Weight", value = snapshot.weightKg?.let { "%.1f kg".format(it) } ?: "—")
                StatItem(label = "BMI", value = snapshot.bmi?.let { "%.1f".format(it) } ?: "—")
                StatItem(label = "Steps", value = snapshot.steps?.let { "%,d".format(it) } ?: "—")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Violet, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
    }
}

@Composable
private fun WeightChartCard(snapshots: List<HealthSnapshot>) {
    val weightsWithData = snapshots.filter { it.weightKg != null }.sortedBy { it.date }
    if (weightsWithData.size < 2) return

    val producer = remember { ChartEntryModelProducer() }
    LaunchedEffect(weightsWithData) {
        producer.setEntries(
            weightsWithData.mapIndexed { index, snapshot ->
                FloatEntry(x = index.toFloat(), y = snapshot.weightKg!!)
            }
        )
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Weight (30 days)", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Chart(
                chart = lineChart(),
                chartModelProducer = producer,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }
}

@Composable
private fun PersonalRecordItem(pr: PersonalRecord) {
    val dateStr = remember(pr.achievedAt) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(pr.achievedAt))
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(pr.exerciseId, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            }
            Text(
                "${pr.maxWeightKg}kg × ${pr.maxReps}",
                style = MaterialTheme.typography.titleMedium,
                color = Teal,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
