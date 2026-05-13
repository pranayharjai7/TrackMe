package com.trackme.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.WorkoutDay
import com.trackme.ui.components.GlassmorphicCard
import com.trackme.ui.components.ReactiveMeshGradient
import com.trackme.ui.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(
    onStartSession: (dayId: String) -> Unit,
    onResumeSession: (dayId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic Background
        ReactiveMeshGradient(
            state = state.dashboardState,
            modifier = Modifier
                .matchParentSize()
                .zIndex(0f),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        ) {
            if (state.isLoading) {
                item {
                    HomeLoadingCard()
                }
                return@LazyColumn
            }

            item {
                GreetingHeader(state)
            }

            item {
                HeroActionCard(state, onStartSession, onResumeSession)
            }

            if (state.weekStrip.isNotEmpty()) {
                item {
                    LiquidWeekTimeline(state.weekStrip)
                }
            }

            state.healthInsight?.let { insight ->
                item {
                    RadialHealthSnapshotCard(insight)
                }
            }

            if (state.recentPRs.isNotEmpty()) {
                item {
                    HallOfFameCarousel(state.recentPRs)
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingCard() {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = Teal, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Column {
                Text(
                    "Loading your dashboard",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Restoring your session and workout plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
        }
    }
}

@Composable
private fun GreetingHeader(state: HomeUiState) {
    val contextAwareGreeting = when (state.dashboardState) {
        HomeDashboardState.REST_RECOVERY -> "Time to recover,"
        HomeDashboardState.PRE_WORKOUT -> "Ready to crush it,"
        HomeDashboardState.ACTIVE_SESSION -> "Keep pushing,"
        HomeDashboardState.TRIUMPH -> "Great job today,"
    }
    
    val firstName = state.displayName.trim().substringBefore(" ")
    val name = if (firstName.isEmpty()) "Champion" else firstName

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                contextAwareGreeting,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Normal,
            )
            Text(
                name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (state.streakDays > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🔥", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.streakDays} days",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroActionCard(
    state: HomeUiState,
    onStartSession: (dayId: String) -> Unit,
    onResumeSession: (dayId: String) -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            when (state.dashboardState) {
                HomeDashboardState.REST_RECOVERY -> {
                    Text("Rest Day 🛌", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("No workout scheduled today. Focus on your nutrition, mobility, and recovery.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
                HomeDashboardState.PRE_WORKOUT -> {
                    val day = state.todayWorkoutDay
                    Text(day?.name ?: "Workout", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(day?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Today", style = MaterialTheme.typography.bodyMedium, color = Coral)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { day?.let { onStartSession(it.id) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Background)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Workout", fontWeight = FontWeight.ExtraBold)
                    }
                }
                HomeDashboardState.ACTIVE_SESSION -> {
                    Text("Session in Progress", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Tap below to jump back in", style = MaterialTheme.typography.bodyMedium, color = Coral)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { state.activeSessionDayId?.let { onResumeSession(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Resume Session", fontWeight = FontWeight.ExtraBold)
                    }
                }
                HomeDashboardState.TRIUMPH -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Workout Complete", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Amazing job today. Your muscles are growing as we speak. See you tomorrow!", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun LiquidWeekTimeline(weekDays: List<WorkoutDay?>) {
    val todayIndex = remember {
        val cal = Calendar.getInstance()
        ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "This Week",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            weekDays.forEachIndexed { index, day ->
                val isToday = index == todayIndex
                val isPast = index < todayIndex
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DAY_LABELS[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    val bgColor = when {
                        isToday -> Color.White.copy(alpha = 0.25f)
                        isPast && day != null -> Teal.copy(alpha = 0.3f)
                        day != null -> Violet.copy(alpha = 0.2f)
                        else -> Color.Transparent
                    }
                    val borderColor = if (isToday) Color.White else Color.White.copy(alpha = 0.1f)
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(if (isToday) 2.dp else 1.dp, borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            if (isPast) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = if (isToday) Color.White else Violet, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadialHealthSnapshotCard(insight: HealthInsight) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated Radial Progress
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(4.dp, if (insight.score > 70) Teal else if (insight.score > 30) Color(0xFFFFD700) else Coral, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${insight.score}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(insight.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(insight.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun HallOfFameCarousel(prs: List<PersonalRecord>) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Hall of Fame",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(prs, key = { it.id }) { pr ->
                GlassmorphicCard(modifier = Modifier.width(200.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(pr.exerciseId, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text("${pr.maxWeightKg} kg", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
