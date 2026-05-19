package com.trackme.ui.progress

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.analytics.models.*
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.formatExerciseName
import com.trackme.ui.components.*
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strengthHistory by viewModel.strengthHistory.collectAsStateWithLifecycle()
    
    // Tab selector for Progress Screen categories to keep it neat and professional
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Muscle Development", "Performance & Forecast")

    Box(modifier = Modifier.fillMaxSize()) {
        ProgressMeshGradient(readinessStatus = state.readinessScore?.status)

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

            // High-End Tab bar
            item {
                TabSelector(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> { // Overview Tab
                        state.readinessScore?.let {
                            item { ReadinessScoreCard(it) }
                        }
                        
                        state.fullAnalytics?.let {
                            item { ConsistencyAndTrainingSummaryCard(it) }
                            item { EnergyExpenditureBreakdownCard(it.energyExpenditure) }
                            item { AdvancedMetricsSummaryCard(it) }
                        }
                    }
                    1 -> { // Muscle Development Tab
                        state.fullAnalytics?.let {
                            item { MuscleBalanceCard(it.muscleBalance) }
                            item { MuscleDevelopmentListCard(it.muscleDevelopment) }
                            item { MuscleStimulusHeatmapCard(it.stimulusHeatmap) }
                            item { MuscleFatigueHeatmapCard(state.muscleFatigueMap) }
                        }
                    }
                    2 -> { // Projections Tab
                        state.fullAnalytics?.let {
                            item {
                                StrengthForecastCard(
                                    history = strengthHistory,
                                    selectedExerciseId = state.selectedExerciseId,
                                    exerciseOptions = state.exerciseOptions,
                                    onSelectExercise = viewModel::selectExercise,
                                    projections = it.strengthProjections
                                )
                            }
                            item { PhysiqueForecastCard(it.physiquePrediction) }
                            item { WeeklyVolumeTimelineCard(it.weeklyVolumeHistory) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(state: ProgressUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Fitness Intelligence Dashboard",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            "TrackMe Progress",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
fun TabSelector(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val animatedBackground by animateColorAsState(
                targetValue = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                animationSpec = tween(300)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(animatedBackground)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ReadinessScoreCard(readinessScore: ReadinessScore) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Teal.copy(alpha = 0.2f))
                        .border(1.dp, Teal.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = Teal)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Daily Recovery & Readiness",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        "${readinessScore.score}% Score • ${readinessScore.status}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ConsistencyAndTrainingSummaryCard(analytics: FullProgressAnalytics) {
    val consistencyPercent = (analytics.consistencyScore * 100).toInt()
    
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Consistency Circle Gauge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { analytics.consistencyScore },
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    color = Violet,
                    strokeWidth = 5.dp,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
                Text(
                    "$consistencyPercent%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(Modifier.width(20.dp))
            
            Column {
                Text(
                    "30-Day Training Consistency",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "You achieved an optimal consistency index based on your expected 4x weekly training structure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EnergyExpenditureBreakdownCard(energy: EnergyBreakdown) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Daily Energy Expenditure (TDEE)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Combines metabolic rate, stepping activity, and lifting volume expenditure.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${energy.totalTDEE.toInt()} kcal",
                        style = MaterialTheme.typography.displaySmall,
                        color = Teal,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Total Expended Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${energy.steps} Steps Today",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))
            
            // Expenditure Breakdown Items
            val items = listOf(
                Triple("Basal Metabolic Rate (BMR)", "${energy.bmr.toInt()} kcal", "Mifflin-St Jeor Equation"),
                Triple("Active Steps Burn", "${energy.stepsCalories.toInt()} kcal", "steps × weight × 0.0005"),
                Triple("Lifting Active Volume Burn", "${energy.caloriesLifting.toInt()} kcal", "volume × 0.04"),
                Triple("Active Health Connect Burn", "${energy.activeCalories.toInt()} kcal", "Sync direct readings")
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { (label, value, formula) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(formula, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                        Text(value, color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedMetricsSummaryCard(analytics: FullProgressAnalytics) {
    val ranking = analytics.muscleRankings
    
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Advanced Insights & Rankings",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Star Badge for top muscles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = Violet)
                    Spacer(Modifier.width(8.dp))
                    Text("Top Trained Muscles", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    ranking.mostTrained.joinToString(", "),
                    color = Violet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.05f))
            Spacer(Modifier.height(12.dp))
            
            // Growth Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Teal)
                    Spacer(Modifier.width(8.dp))
                    Text("Fastest Developing", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    ranking.fastestGrowing.joinToString(", "),
                    color = Teal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.05f))
            Spacer(Modifier.height(12.dp))

            // Undertrained alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Coral)
                    Spacer(Modifier.width(8.dp))
                    Text("Needs Attention", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    ranking.leastTrained.joinToString(", "),
                    color = Coral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Plateau alerts if any exist
            val plateauAlerts = analytics.plateaus.filter { it.isPlateaued }
            if (plateauAlerts.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Coral.copy(alpha = 0.1f))
                        .border(1.dp, Coral.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Coral)
                            Spacer(Modifier.width(8.dp))
                            Text("Adaptation Plateau Detected!", color = Coral, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        plateauAlerts.forEach {
                            Text(
                                "• ${it.exerciseName.formatExerciseName()} (stalled over 6 weeks). Tip: de-load 10% volume or alternate angles.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleBalanceCard(balance: MuscleBalanceInfo) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Symmetry & Muscle Balance",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Analyzes indexes to detect agonist-antagonist strength imbalances.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Ratios Bars
            val balances = listOf(
                Triple("Push vs Pull Symmetry", balance.pushPullRatio, balance.pushPullStatus),
                Triple("Quad vs Hamstring Symmetry", balance.quadHamRatio, balance.quadHamStatus),
                Triple("Upper vs Lower Symmetry", balance.upperLowerRatio, balance.upperLowerStatus)
            )
            
            balances.forEachIndexed { idx, (title, ratio, status) ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(status, color = if (status == "Balanced") Teal else Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    // Simple symmetry visual indicator bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        val alignment = ratio / 2.0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(alignment.coerceIn(0.1f, 1.0f))
                                .clip(CircleShape)
                                .background(if (status == "Balanced") Teal else Coral)
                        )
                    }
                    
                    if (idx < balances.size - 1) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleDevelopmentListCard(muscles: List<MuscleDevelopment>) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Muscle Development Index (MDI)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "MDI starts at 100. Growth saturation limits absolute MDI ceiling to 200.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                muscles.forEach { muscle ->
                    val progressValue = (muscle.growthIndex - 100f) / 100f // starts at 100, max is 200.
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                muscle.muscleGroup.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                String.format(Locale.US, "MDI %.1f (+%.1f%%)", muscle.growthIndex, muscle.percentageGrowth),
                                color = Teal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressValue.coerceIn(0f, 1f))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(listOf(Teal, Violet))
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleStimulusHeatmapCard(heatmap: List<MuscleStimulusHeatmap>) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Weekly Muscle Stimulus Heatmap",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Compares calculated volume stimulus against targets to prevent overtraining.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                heatmap.forEach { item ->
                    val color = when (item.status) {
                        "Optimal" -> Teal
                        "Overtrained" -> Coral
                        else -> Color(0xFFFBBF24)
                    }
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(item.muscleGroup, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Target: ${item.minRecommended.toInt()} - ${item.maxRecommended.toInt()}",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${item.stimulus.toInt()} units",
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(item.status, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(item.ratio.coerceIn(0f, 1f))
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleFatigueHeatmapCard(fatigueMap: Map<String, MuscleFatigue>) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Muscle Fatigue & Recovery Heatmap",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Exponential physiological decay countdown to prevent acute injury.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                fatigueMap.forEach { (muscle, fatigue) ->
                    val pct = fatigue.fatiguePercentage
                    val color = when {
                        pct > 50 -> Coral
                        pct > 20 -> Color(0xFFFBBF24)
                        else -> Teal
                    }
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(muscle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$pct% fatigue",
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (fatigue.recoveryTimeRemainingHours > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(color.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "${fatigue.recoveryTimeRemainingHours}h left",
                                            color = color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Teal.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "READY",
                                            color = Teal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrengthForecastCard(
    history: List<SessionSet>,
    selectedExerciseId: String?,
    exerciseOptions: List<String>,
    onSelectExercise: (String) -> Unit,
    projections: List<StrengthProjectionPoint>
) {
    val activeProjection = projections.firstOrNull { it.exerciseId == selectedExerciseId }
        ?: projections.firstOrNull()

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Strength Trajectory Forecast",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Least-squares linear regression on historical weekly 1RM peaks.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Selector dropdown list
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        (selectedExerciseId ?: activeProjection?.exerciseId ?: "Select Exercise").formatExerciseName(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Surface)
                ) {
                    exerciseOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.formatExerciseName(), color = Color.White) },
                            onClick = {
                                onSelectExercise(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            if (activeProjection != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            String.format(Locale.US, "%.1f kg", activeProjection.current1RM),
                            style = MaterialTheme.typography.displaySmall,
                            color = Teal,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("Current Estimated 1RM (Epley)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(16.dp))
                
                // Forecast Items
                val items = listOf(
                    Pair("30 Days Proj", activeProjection.projected30Days),
                    Pair("90 Days Proj", activeProjection.projected90Days),
                    Pair("365 Days Proj", activeProjection.projected365Days)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.forEach { (label, value) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                value,
                                color = if (value.contains("kg")) Teal else Coral,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (label != "365 Days Proj") {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            } else {
                Text(
                    "Select an exercise to generate a linear trajectory forecast.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
fun PhysiqueForecastCard(predictions: List<PhysiqueProjectionPoint>) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Physique Projection Forecast",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Applies saturation model: future = max_index - (max_index - current) * e^(-rate * weeks)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                predictions.forEach { item ->
                    Column {
                        Text(
                            item.muscleGroup.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Current", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                Text(String.format(Locale.US, "%.1f", item.currentMuscleIndex), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("4 Weeks", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                Text(String.format(Locale.US, "%.1f", item.projectedIndex4Weeks), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("12 Weeks", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                Text(String.format(Locale.US, "%.1f", item.projectedIndex12Weeks), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("52 Weeks", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                Text(String.format(Locale.US, "%.1f", item.projectedIndex52Weeks), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyVolumeTimelineCard(weeklyVolume: List<Pair<String, Float>>) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Weekly Training Volume Timeline",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Consistency tracking across the previous 8 weeks.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxVolume = weeklyVolume.maxOfOrNull { it.second } ?: 1f
                
                weeklyVolume.forEach { (week, vol) ->
                    val barHeightRatio = if (maxVolume > 0) vol / maxVolume else 0f
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (vol > 0) "${(vol/1000).toInt()}k" else "-",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(barHeightRatio.coerceIn(0.05f, 1f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(Brush.verticalGradient(listOf(Teal, Violet)))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = week,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressMeshGradient(readinessStatus: String?) {
    val gradientColor1 = when (readinessStatus) {
        "Optimal" -> Teal.copy(alpha = 0.1f)
        "Fatigued" -> Coral.copy(alpha = 0.1f)
        else -> Violet.copy(alpha = 0.1f)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(gradientColor1, Color.Transparent),
                    radius = 1200f
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(Background, Color(0xFF0D0D12))
                )
            )
    )
}
