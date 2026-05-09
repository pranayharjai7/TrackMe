package com.trackme.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.WorkoutDay
import com.trackme.ui.theme.*
import java.util.Calendar

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun HomeScreen(
    onStartSession: (dayId: String) -> Unit,
    onResumeSession: (dayId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Violet.copy(alpha = 0.08f), Background)))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnBackground,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (state.streakDays > 0) {
                        StreakBadge(state.streakDays)
                    }
                }
            }

            if (state.weekStrip.isNotEmpty()) {
                item { WeekStrip(state.weekStrip) }
            }

            state.activeSessionDayId?.let { dayId ->
                item {
                    Card(
                        onClick = { onResumeSession(dayId) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Violet.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Violet, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Session in progress — tap to resume",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Violet,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Violet)
                        }
                    }
                }
            }

            item {
                val todayDay = state.todayWorkoutDay
                val isActiveForToday = state.activeSessionDayId != null &&
                        state.activeSessionDayId == todayDay?.id
                TodayWorkoutCard(
                    day = todayDay,
                    isInProgress = isActiveForToday,
                    isFinished = state.isTodaySessionFinished,
                    onStart = { todayDay?.let { onStartSession(it.id) } },
                )
            }

            state.latestSnapshot?.let { snapshot ->
                item { HealthConnectCard(snapshot) }
            }

            if (state.recentPRs.isNotEmpty()) {
                item {
                    Text(
                        "Recent Personal Records",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                    )
                }
                items(state.recentPRs, key = { it.id }) { pr ->
                    PersonalRecordCard(pr)
                }
            }
        }
    }
}

@Composable
private fun StreakBadge(days: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Coral.copy(alpha = 0.15f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("🔥", style = MaterialTheme.typography.bodyMedium)
            Text(
                "$days day streak",
                style = MaterialTheme.typography.labelMedium,
                color = Coral,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WeekStrip(weekDays: List<WorkoutDay?>) {
    val todayIndex = remember {
        val cal = Calendar.getInstance()
        ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            weekDays.forEachIndexed { index, day ->
                val isToday = index == todayIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DAY_LABELS[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) Violet else OnSurfaceMuted,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isToday && day != null -> Violet.copy(alpha = 0.9f)
                            isToday -> Violet.copy(alpha = 0.25f)
                            day != null -> Violet.copy(alpha = 0.2f)
                            else -> Surface
                        },
                        border = if (isToday) androidx.compose.foundation.BorderStroke(1.5.dp, Violet) else null,
                        modifier = Modifier.size(32.dp),
                    ) {
                        if (day != null) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = day.name,
                                    tint = if (isToday) androidx.compose.ui.graphics.Color.White else Violet,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthConnectCard(snapshot: HealthSnapshot) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Health Today", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    snapshot.steps?.let {
                        Text("${"%,d".format(it)} steps", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    }
                    snapshot.weightKg?.let {
                        Text("%.1f kg".format(it), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    }
                    snapshot.activeCaloriesBurned?.let {
                        Text("${it.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(day: WorkoutDay?, isInProgress: Boolean, isFinished: Boolean, onStart: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (day == null) {
                Text("Rest Day 🛌", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                Text(
                    "No workout scheduled today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted,
                )
            } else {
                Text(
                    day.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = Violet,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    isFinished -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Workout complete",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Teal,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = onStart,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restart")
                            }
                        }
                    }
                    isInProgress -> {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Continue Workout", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Workout", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordCard(pr: PersonalRecord) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(pr.exerciseId, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Text("${pr.maxWeightKg}kg", style = MaterialTheme.typography.titleMedium, color = Teal, fontWeight = FontWeight.Bold)
        }
    }
}
