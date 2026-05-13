package com.trackme.ui.progress

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.SessionSet
import com.trackme.domain.usecase.MuscleVolume
import com.trackme.ui.components.*
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val strengthHistory by viewModel.strengthHistory.collectAsState()
    val tilt by rememberDeviceTilt()

    Box(modifier = Modifier.fillMaxSize()) {
        ProgressMeshGradient(state = state.dashboardState)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                ProgressHeader(state)
            }

            item {
                FitnessEngineCard(state, tilt)
            }

            if (state.sessionVolumes.isNotEmpty()) {
                item {
                    InteractiveHeatmapCard(state.sessionVolumes, tilt)
                }
            }

            if (state.exerciseOptions.isNotEmpty()) {
                item {
                    InteractiveStrengthChartCard(
                        history = strengthHistory,
                        exerciseOptions = state.exerciseOptions,
                        selectedExerciseId = state.selectedExerciseId,
                        onSelectExercise = viewModel::selectExercise,
                        tilt = tilt
                    )
                }
            }

            if (state.weightHistory.count { it.weightKg != null } >= 2) {
                item {
                    InteractiveWeightChartCard(state.weightHistory, tilt)
                }
            }

            if (state.muscleVolume.isNotEmpty()) {
                item {
                    InteractiveMuscleVolumeCard(state.muscleVolume, tilt)
                }
            }

            if (state.personalRecords.isNotEmpty()) {
                item {
                    Text(
                        "Hall of Fame",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(state.personalRecords, key = { it.id }) { pr ->
                    InteractivePRCard(pr, tilt)
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(state: ProgressUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Progress Insights",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            when (state.dashboardState) {
                ProgressState.MOMENTUM -> "Momentum"
                ProgressState.MAINTENANCE -> "Consistency"
                ProgressState.RECOVERY -> "Recovery"
                ProgressState.UNCHARTED -> "Discovery"
            },
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun FitnessEngineCard(state: ProgressUiState, tilt: Tilt) {
    var expanded by remember { mutableStateOf(false) }
    
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .parallaxTilt(tilt, 8f)
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (state.dashboardState) {
                    ProgressState.MOMENTUM -> Icons.AutoMirrored.Filled.TrendingUp
                    ProgressState.MAINTENANCE -> Icons.Default.LinearScale
                    ProgressState.RECOVERY -> Icons.Default.SelfImprovement
                    ProgressState.UNCHARTED -> Icons.Default.Explore
                }
                val tint = when (state.dashboardState) {
                    ProgressState.MOMENTUM -> Teal
                    ProgressState.MAINTENANCE -> Violet
                    ProgressState.RECOVERY -> Coral
                    ProgressState.UNCHARTED -> Blue
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.2f))
                        .border(1.dp, tint.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column {
                    Text(
                        state.primaryInsight?.title ?: "Analyzing Trends...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap for deep dive",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                state.primaryInsight?.description ?: "We're crunching your latest workout data to see how you're trending.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )
            
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "Metric Breakdown",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricSmall(label = "Consistency", value = "High", icon = Icons.Default.CheckCircle, tint = Teal)
                        MetricSmall(label = "Intensity", value = "+12%", icon = Icons.Default.Speed, tint = Violet)
                        MetricSmall(label = "Volume", value = "Stable", icon = Icons.Default.StackedLineChart, tint = Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSmall(label: String, value: String, icon: ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun InteractiveHeatmapCard(sessionVolumes: Map<Long, Int>, tilt: Tilt) {
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    
    val days = remember {
        (29 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
    }

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .parallaxTilt(tilt, 5f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Activity Pulse", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Last 30 days", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }
                
                selectedDay?.let { dayMs ->
                    val sets = sessionVolumes[dayMs] ?: 0
                    val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dayMs))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            "$dateStr: $sets sets",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                days.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { dayMs ->
                            val sets = sessionVolumes[dayMs] ?: 0
                            val isSelected = selectedDay == dayMs
                            val alpha = when {
                                sets == 0 -> 0.05f
                                sets <= 3 -> 0.3f
                                sets <= 7 -> 0.6f
                                else -> 1.0f
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (sets == 0) Color.White.copy(alpha = alpha)
                                        else Teal.copy(alpha = alpha)
                                    )
                                    .border(
                                        if (isSelected) 2.dp else 0.dp,
                                        if (isSelected) Color.White else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedDay = if (isSelected) null else dayMs }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractiveStrengthChartCard(
    history: List<SessionSet>,
    exerciseOptions: List<String>,
    selectedExerciseId: String?,
    onSelectExercise: (String) -> Unit,
    tilt: Tilt
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val sortedSets = remember(history) { 
        history.filter { it.completed && it.weightKg > 0f }.sortedBy { it.updatedAt }
    }

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .parallaxTilt(tilt, 6f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Strength Trajectory", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
            ) {
                Surface(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            selectedExerciseId?.replaceFirstChar { it.uppercase() } ?: "Select exercise",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                }
                
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(SurfaceVariant)
                ) {
                    exerciseOptions.forEach { exerciseId ->
                        DropdownMenuItem(
                            text = { Text(exerciseId.replaceFirstChar { it.uppercase() }, color = Color.White) },
                            onClick = {
                                onSelectExercise(exerciseId)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))

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
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                
                Spacer(Modifier.height(12.dp))
                Text(
                    "Showing max weight progression for ${selectedExerciseId ?: "selected exercise"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Log more sets to visualize trajectory", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun InteractiveWeightChartCard(snapshots: List<HealthSnapshot>, tilt: Tilt) {
    val weightsWithData = remember(snapshots) {
        snapshots.filter { it.weightKg != null }.sortedBy { it.date }
    }
    
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .parallaxTilt(tilt, 4f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Body Composition", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            val currentWeight = weightsWithData.lastOrNull()?.weightKg
            val startWeight = weightsWithData.firstOrNull()?.weightKg
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    currentWeight?.let { "%.1f".format(it) } ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Text("kg", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                
                if (currentWeight != null && startWeight != null) {
                    val diff = currentWeight - startWeight
                    val color = if (diff <= 0) Teal else Coral
                    val arrow = if (diff <= 0) "↓" else "↑"
                    
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "$arrow %.1f kg".format(Math.abs(diff)),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))

            val producer = remember(weightsWithData) { ChartEntryModelProducer() }
            LaunchedEffect(weightsWithData) {
                producer.setEntries(
                    weightsWithData.mapIndexed { index, snapshot ->
                        FloatEntry(x = index.toFloat(), y = snapshot.weightKg!!)
                    }
                )
            }

            Chart(
                chart = lineChart(),
                chartModelProducer = producer,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
        }
    }
}

@Composable
private fun InteractiveMuscleVolumeCard(muscleVolume: List<MuscleVolume>, tilt: Tilt) {
    var selectedMuscle by remember { mutableStateOf<String?>(null) }
    val maxSets = remember(muscleVolume) { muscleVolume.maxOfOrNull { it.totalSets }?.toFloat() ?: 1f }

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .parallaxTilt(tilt, 7f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Muscle Distribution", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Total sets per group (28 days)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                muscleVolume.forEach { mv ->
                    val isSelected = selectedMuscle == mv.muscle
                    val fraction = mv.totalSets / maxSets
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                        label = "bar"
                    )

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedMuscle = if (isSelected) null else mv.muscle }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                mv.muscle.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(90.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedFraction)
                                        .background(
                                            Brush.horizontalGradient(listOf(Teal.copy(alpha = 0.6f), Teal))
                                        )
                                )
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${mv.totalSets}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Teal else Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        AnimatedVisibility(visible = isSelected) {
                            Text(
                                "You're prioritizing ${mv.muscle} which accounts for ${(fraction * 100).toInt()}% of your relative volume.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 8.dp, start = 90.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractivePRCard(pr: PersonalRecord, tilt: Tilt) {
    val dateStr = remember(pr.achievedAt) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(pr.achievedAt))
    }
    
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .parallaxTilt(tilt, 3f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD700).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(pr.exerciseId.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Achieved $dateStr", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${pr.maxWeightKg}kg",
                    style = MaterialTheme.typography.titleLarge,
                    color = Teal,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${pr.maxReps} reps",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
