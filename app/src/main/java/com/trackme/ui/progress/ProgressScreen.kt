package com.trackme.ui.progress

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.analytics.models.*
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.formatExerciseName
import com.trackme.ui.components.*
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = 96.dp + navBarPadding + 24.dp
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        Pair("Overview", Icons.Default.Analytics),
        Pair("Muscles", Icons.Default.FitnessCenter),
        Pair("Forecasts", Icons.AutoMirrored.Filled.TrendingUp)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ProgressMeshGradient(readinessStatus = state.readinessScore?.status)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 24.dp, bottom = bottomPadding, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                ProgressHeader()
            }

            stickyHeader(key = "tab_selector") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(vertical = 8.dp)
                ) {
                    TabSelector(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> { // Overview Tab
                        state.readinessScore?.let {
                            item { InteractiveReadinessScoreCard(it) }
                        }
                        
                        state.fullAnalytics?.let {
                            item { GlowingConsistencyCard(it) }
                            item { InteractiveEnergyExpenditureCard(it.energyExpenditure) }
                            item { TabbedAdvancedMetricsCard(it) }
                        }
                    }
                    1 -> { // Muscle Development Tab
                        state.fullAnalytics?.let {
                            item { MuscleSymmetryScaleCard(it.muscleBalance) }
                            item { ExpandableMuscleDevelopmentCard(it.muscleDevelopment) }
                            item { FlippableMuscleStimulusCard(it.stimulusHeatmap) }
                            item { MuscleFatigueCountdownCard(state.muscleFatigueMap) }
                        }
                    }
                    2 -> { // Forecast & Timeline Tab
                        state.fullAnalytics?.let {
                            item {
                                StrengthForecastChartCard(
                                    selectedExerciseId = state.selectedExerciseId,
                                    exerciseOptions = state.exerciseOptions,
                                    onSelectExercise = viewModel::selectExercise,
                                    projections = it.strengthProjections
                                )
                            }
                            item { SaturationScrubPhysiqueCard(it.physiquePrediction) }
                            item { InteractiveWeeklyVolumeCard(it.weeklyVolumeHistory) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "FITNESS INTELLIGENCE ENGINE",
            style = MaterialTheme.typography.labelMedium,
            color = Teal,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Performance Analytics",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Black
        )
    }
}

// ==========================================
// TAB 1: OVERVIEW COMPOSABLES
// ==========================================

@Composable
fun InteractiveReadinessScoreCard(readinessScore: ReadinessScore) {
    var expanded by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "readiness_glow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val readinessColor = when (readinessScore.status) {
        "Optimal" -> Teal
        "Good" -> Blue
        "Needs Recovery" -> Coral
        else -> Violet
    }

    val animatedScore by animateFloatAsState(
        targetValue = readinessScore.score.toFloat(),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "score_anim"
    )

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background glow ring
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = readinessColor.copy(alpha = 0.05f),
                                radius = size.minDimension / 2f
                            )
                            drawArc(
                                color = Color.White.copy(alpha = 0.08f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(readinessColor.copy(alpha = 0.5f), readinessColor)
                                ),
                                startAngle = -90f,
                                sweepAngle = (animatedScore / 100f) * 360f,
                                useCenter = false,
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${readinessScore.score}",
                                fontSize = 20.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "READINESS",
                                fontSize = 8.sp,
                                color = OnSurfaceMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            "Daily Recovery Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(readinessColor.copy(alpha = pulseGlow))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = readinessScore.status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = readinessColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = OnSurfaceMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "Recovery Indicators",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    val debug = readinessScore.debug
                    if (debug != null) {
                        IndicatorRow(
                            label = "HRV Deviation",
                            value = String.format(Locale.US, "%+.1f%%", debug.hrvDeviationPercentage),
                            percentage = (debug.hrvDeviationPercentage + 50f) / 100f,
                            color = if (debug.hrvDeviationPercentage >= -5f) Teal else Coral
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        IndicatorRow(
                            label = "RHR Deviation",
                            value = String.format(Locale.US, "%+.1f%%", debug.rhrDeviationPercentage),
                            percentage = (debug.rhrDeviationPercentage + 50f) / 100f,
                            color = if (debug.rhrDeviationPercentage <= 10f) Teal else Coral
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        IndicatorRow(
                            label = "Sleep Debt Penalty",
                            value = String.format(Locale.US, "-%.1f%%", debug.sleepScorePenalty),
                            percentage = (100f - debug.sleepScorePenalty) / 100f,
                            color = if (debug.sleepScorePenalty <= 10f) Teal else Coral
                        )

                        if (debug.missingDataFlags.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Lacking metrics for complete score: " + debug.missingDataFlags.joinToString(", ").uppercase(),
                                color = OnSurfaceMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            "Ensure Sleep, Heart Rate, and HRV are sync'd daily from Health Connect for accurate recovery index readings.",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndicatorRow(
    label: String,
    value: String,
    percentage: Float,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = OnSurface, fontSize = 12.sp)
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun GlowingConsistencyCard(analytics: FullProgressAnalytics) {
    var showExplanation by remember { mutableStateOf(false) }
    val consistencyPercent = (analytics.consistencyScore * 100).toInt()

    val animatedConsistency by animateFloatAsState(
        targetValue = analytics.consistencyScore,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "const_anim"
    )

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showExplanation = !showExplanation }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Violet.copy(alpha = 0.05f),
                            radius = size.minDimension / 2f
                        )
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx())
                        )
                        drawArc(
                            color = Violet,
                            startAngle = -90f,
                            sweepAngle = animatedConsistency * 360f,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "$consistencyPercent%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "30-Day Consistency",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap to see details about your expected target frequency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
            }

            AnimatedVisibility(
                visible = showExplanation,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Consistency measures how well you stick to your programmed workout routine. Based on a target of 4 training sessions per week (16 workouts a month), your consistency score reflects the proportion of target workouts actually completed. Keeping this above 80% maintains optimal progressive overload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveEnergyExpenditureCard(energy: EnergyBreakdown) {
    var selectedSegment by remember { mutableStateOf(0) }

    val segments = listOf(
        Triple("Basal Metabolic Rate", energy.bmr, Teal),
        Triple("Active Steps Burn", energy.stepsCalories, Blue),
        Triple("Lifting Energy", energy.caloriesLifting, Violet),
        Triple("Health Connect Sync", energy.activeCalories, Coral)
    )
    val totalExpended = energy.totalTDEE

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Energy Expenditure (TDEE)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${energy.steps} Steps",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            
            Text(
                "Click individual color blocks to inspect calculations & formula detail.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${totalExpended.toInt()} kcal",
                    fontSize = 32.sp,
                    color = Teal,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "DAILY EXPENDITURE ESTIMATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // Interactive Stacked Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                segments.forEachIndexed { index, (_, kcal, color) ->
                    val weight = if (totalExpended > 0f) kcal / totalExpended else 0f
                    if (weight > 0f) {
                        val isSelected = selectedSegment == index
                        Box(
                            modifier = Modifier
                                .weight(weight.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(
                                    if (isSelected) color else color.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent
                                )
                                .clickable { selectedSegment = index }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Segment detail card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    val (label, kcal, color) = segments[selectedSegment]
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${kcal.toInt()} kcal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))

                    val formulaDesc = when (selectedSegment) {
                        0 -> "Computed resting metabolism using height, weight, and age (Mifflin-St Jeor formula)."
                        1 -> "Estimated energy expended from walking activity. Formula: steps × weight (kg) × 0.0005."
                        2 -> "Mechanical energy expenditure computed directly from total lifted weight volume. Formula: volume (kg) × 0.04."
                        else -> "Active calories recorded by wearable sensors and fetched directly from Google Health Connect."
                    }

                    Text(
                        text = formulaDesc,
                        color = OnSurface,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TabbedAdvancedMetricsCard(analytics: FullProgressAnalytics) {
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Highlights, 1 = Warnings & Plateaus
    val ranking = analytics.muscleRankings
    val plateaus = analytics.plateaus.filter { it.isPlateaued }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Horizontal mini tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeSubTab == 0) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { activeSubTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Highlights", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeSubTab == 1) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { activeSubTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Alerts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (plateaus.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Coral)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Crossfade(targetState = activeSubTab, label = "subtab_crossfade") { subTab ->
                when (subTab) {
                    0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InsightChipRow(
                                title = "Top Trained Muscles",
                                muscles = ranking.mostTrained,
                                color = Violet,
                                icon = Icons.Default.Stars,
                                advice = "These muscle groups represent your primary drivers of mechanical tension. Keep up the high volume."
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            InsightChipRow(
                                title = "Fastest Developing",
                                muscles = ranking.fastestGrowing,
                                color = Teal,
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                advice = "Showing the steepest progressive overload curves. High neuromuscular efficiency."
                            )
                        }
                    }
                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InsightChipRow(
                                title = "Needs Focus",
                                muscles = ranking.leastTrained,
                                color = Coral,
                                icon = Icons.Default.Warning,
                                advice = "These muscle groups are receiving minimal stimulation. Program more exercises targeting them."
                            )

                            if (plateaus.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Coral.copy(alpha = 0.08f))
                                        .border(1.dp, Coral.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Dangerous, contentDescription = null, tint = Coral)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Plateau Warning",
                                                color = Coral,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        plateaus.forEach {
                                            Text(
                                                "• ${it.exerciseName.formatExerciseName()} progress has stalled for 6+ weeks. We suggest a 10% de-load or exercise angle variation.",
                                                color = OnSurface,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("No lifting adaptation plateaus detected.", color = OnSurfaceMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightChipRow(
    title: String,
    muscles: List<String>,
    color: Color,
    icon: ImageVector,
    advice: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = OnSurfaceMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            muscles.forEach { muscle ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.1f))
                        .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(muscle.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                text = advice,
                color = OnSurfaceMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

// ==========================================
// TAB 2: MUSCLE DEVELOPMENT COMPOSABLES
// ==========================================

@Composable
fun MuscleSymmetryScaleCard(balance: MuscleBalanceInfo) {
    var selectedSymmetry by remember { mutableStateOf(0) }
    
    val symmetries = listOf(
        Triple("Push vs Pull Balance", balance.pushPullRatio, balance.pushPullStatus),
        Triple("Quad vs Hamstring Balance", balance.quadHamRatio, balance.quadHamStatus),
        Triple("Upper vs Lower Balance", balance.upperLowerRatio, balance.upperLowerStatus)
    )

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Symmetry & Muscle Balance",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap balances to see biomechanical tips & injury risk reductions.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )

            Spacer(Modifier.height(24.dp))

            symmetries.forEachIndexed { index, (title, ratio, status) ->
                val isSelected = selectedSymmetry == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSymmetry = index }
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.02f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = status,
                            color = if (status == "Balanced") Teal else Coral,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Dual sided scale drawing
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Mid point indicator mark
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                        // Horizontal track bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        
                        // Glowing pointer bead
                        val fraction = (ratio / 2f).coerceIn(0.05f, 0.95f)
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(fraction)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (status == "Balanced") Teal else Coral)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Agonist Dominant", color = OnSurfaceMuted, fontSize = 9.sp)
                        Text("Antagonist Dominant", color = OnSurfaceMuted, fontSize = 9.sp)
                    }
                }
                
                if (index < symmetries.size - 1) {
                    Spacer(Modifier.height(16.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Explanation box based on selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                val detailText = when (selectedSymmetry) {
                    0 -> "Push vs Pull imbalance (chest/shoulders vs back) can alter shoulder posture and strain rotators. Program horizontal pulls (rows) to match chest press volume."
                    1 -> "Quad vs Hamstring ratio should sit near 1.0 to prevent ACL tears and hamstring pulls during sprints. Add Romanian deadlifts or hamstring curls if hamstrings are lagging."
                    else -> "Upper vs Lower symmetry balances core center of gravity. Ensure leg days receive equal training intensity compared to upper body sessions."
                }
                Text(
                    text = detailText,
                    color = OnSurface,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private data class MusclePath2D(
    val muscleName: String,
    val isLeft: Boolean,
    val hitPolygon: List<Offset>,
    val drawAction: Path.() -> Unit
)


private fun Path.drawBodySilhouette() {
    // Start at head top
    moveTo(0f, -145f)
    // Head left side
    cubicTo(-10f, -145f, -10f, -115f, 0f, -115f)
    // Neck left side
    lineTo(-6f, -115f)
    quadraticTo(-7f, -108f, -9f, -102f)
    // Traps left
    lineTo(-28f, -94f)
    // Shoulder left
    cubicTo(-38f, -92f, -44f, -82f, -40f, -70f)
    // Upper arm outer left
    cubicTo(-44f, -60f, -46f, -50f, -44f, -38f)
    // Forearm outer left
    cubicTo(-48f, -25f, -46f, -10f, -38f, 5f)
    // Hand left
    cubicTo(-40f, 10f, -38f, 18f, -34f, 18f)
    cubicTo(-30f, 18f, -32f, 10f, -34f, 5f)
    // Forearm inner left
    lineTo(-30f, -38f)
    // Torso outer left (lat flare)
    lineTo(-26f, -62f)
    cubicTo(-28f, -48f, -24f, -25f, -17f, 15f)
    // Hip / Outer Quad left
    cubicTo(-21f, 30f, -24f, 60f, -17f, 105f)
    // Calf outer left
    cubicTo(-21f, 125f, -18f, 145f, -10f, 172f)
    // Foot left
    cubicTo(-12f, 176f, -8f, 180f, -4f, 180f)
    cubicTo(-2f, 180f, -4f, 174f, -5f, 172f)
    // Calf inner left
    lineTo(-7f, 105f)
    // Inner thigh left
    lineTo(-2f, 45f)
    // Crotch/Groin
    lineTo(0f, 45f)
    // Inner thigh right
    lineTo(2f, 45f)
    // Calf inner right
    lineTo(7f, 105f)
    // Foot right
    cubicTo(4f, 174f, 2f, 180f, 4f, 180f)
    cubicTo(8f, 180f, 12f, 176f, 10f, 172f)
    // Calf outer right
    cubicTo(18f, 145f, 21f, 125f, 17f, 105f)
    // Hip / Outer Quad right
    cubicTo(24f, 60f, 21f, 30f, 17f, 15f)
    // Torso outer right (lat flare)
    cubicTo(24f, -25f, 28f, -48f, 26f, -62f)
    // Forearm inner right
    lineTo(30f, -38f)
    // Hand right
    lineTo(34f, 5f)
    cubicTo(32f, 10f, 30f, 18f, 34f, 18f)
    cubicTo(38f, 18f, 40f, 10f, 38f, 5f)
    // Forearm outer right
    cubicTo(46f, -10f, 48f, -25f, 44f, -38f)
    // Bicep/Tricep outer right
    cubicTo(46f, -50f, 44f, -60f, 40f, -70f)
    // Shoulder right
    cubicTo(44f, -82f, 38f, -92f, 28f, -94f)
    // Traps right
    lineTo(9f, -102f)
    // Neck right side
    quadraticTo(7f, -108f, 6f, -115f)
    lineTo(0f, -115f)
    // Head right side
    cubicTo(10f, -115f, 10f, -145f, 0f, -145f)
    close()
}

private val frontMusclePaths = listOf(
    // Traps
    MusclePath2D(
        "TRAPS", false,
        listOf(Offset(-8f, -112f), Offset(-28f, -94f), Offset(-8f, -100f)),
        {
            moveTo(-8f, -112f)
            quadraticTo(-18f, -108f, -28f, -94f)
            lineTo(-8f, -100f)
            close()
        }
    ),
    MusclePath2D(
        "TRAPS", true,
        listOf(Offset(8f, -112f), Offset(28f, -94f), Offset(8f, -100f)),
        {
            moveTo(8f, -112f)
            quadraticTo(18f, -108f, 28f, -94f)
            lineTo(8f, -100f)
            close()
        }
    ),
    // Shoulders
    MusclePath2D(
        "SHOULDERS", false,
        listOf(Offset(-28f, -94f), Offset(-40f, -70f), Offset(-26f, -76f)),
        {
            moveTo(-28f, -94f)
            cubicTo(-34f, -92f, -40f, -84f, -40f, -70f)
            cubicTo(-38f, -60f, -32f, -64f, -26f, -76f)
            close()
        }
    ),
    MusclePath2D(
        "SHOULDERS", true,
        listOf(Offset(28f, -94f), Offset(40f, -70f), Offset(26f, -76f)),
        {
            moveTo(28f, -94f)
            cubicTo(34f, -92f, 40f, -84f, 40f, -70f)
            cubicTo(38f, -60f, 32f, -64f, 26f, -76f)
            close()
        }
    ),
    // Chest
    MusclePath2D(
        "CHEST", false,
        listOf(Offset(-2f, -95f), Offset(-27f, -90f), Offset(-26f, -64f), Offset(-2f, -64f)),
        {
            moveTo(-2f, -95f)
            cubicTo(-12f, -95f, -22f, -92f, -27f, -90f)
            cubicTo(-26f, -78f, -28f, -68f, -26f, -64f)
            cubicTo(-18f, -62f, -8f, -62f, -2f, -64f)
            close()
        }
    ),
    MusclePath2D(
        "CHEST", true,
        listOf(Offset(2f, -95f), Offset(27f, -90f), Offset(26f, -64f), Offset(2f, -64f)),
        {
            moveTo(2f, -95f)
            cubicTo(14f, -95f, 24f, -92f, 28f, -90f)
            cubicTo(26f, -78f, 28f, -68f, 26f, -64f)
            cubicTo(18f, -62f, 8f, -62f, 2f, -64f)
            close()
        }
    ),
    // Abs
    MusclePath2D(
        "ABDOMINALS", false,
        listOf(Offset(-2f, -62f), Offset(-15f, -60f), Offset(-14f, 18f), Offset(-2f, 20f)),
        {
            // Upper pack
            moveTo(-3f, -58f)
            lineTo(-14f, -56f)
            lineTo(-13f, -42f)
            lineTo(-3f, -42f)
            close()
            // Middle pack
            moveTo(-3f, -38f)
            lineTo(-13f, -38f)
            lineTo(-13f, -24f)
            lineTo(-3f, -24f)
            close()
            // Lower pack
            moveTo(-3f, -20f)
            lineTo(-13f, -20f)
            lineTo(-12f, 15f)
            lineTo(-3f, 16f)
            close()
        }
    ),
    MusclePath2D(
        "ABDOMINALS", true,
        listOf(Offset(2f, -62f), Offset(15f, -60f), Offset(14f, 18f), Offset(2f, 20f)),
        {
            // Upper pack
            moveTo(3f, -58f)
            lineTo(14f, -56f)
            lineTo(13f, -42f)
            lineTo(3f, -42f)
            close()
            // Middle pack
            moveTo(3f, -38f)
            lineTo(13f, -38f)
            lineTo(13f, -24f)
            lineTo(3f, -24f)
            close()
            // Lower pack
            moveTo(3f, -20f)
            lineTo(13f, -20f)
            lineTo(12f, 15f)
            lineTo(3f, 16f)
            close()
        }
    ),
    // Biceps
    MusclePath2D(
        "BICEPS", false,
        listOf(Offset(-26f, -76f), Offset(-32f, -42f), Offset(-24f, -52f), Offset(-24f, -62f)),
        {
            moveTo(-26f, -76f)
            cubicTo(-30f, -70f, -34f, -55f, -32f, -42f)
            cubicTo(-28f, -44f, -24f, -52f, -24f, -62f)
            close()
        }
    ),
    MusclePath2D(
        "BICEPS", true,
        listOf(Offset(26f, -76f), Offset(32f, -42f), Offset(24f, -52f), Offset(24f, -62f)),
        {
            moveTo(26f, -76f)
            cubicTo(30f, -70f, 34f, -55f, 32f, -42f)
            cubicTo(28f, -44f, 24f, -52f, 24f, -62f)
            close()
        }
    ),
    // Triceps
    MusclePath2D(
        "TRICEPS", false,
        listOf(Offset(-36f, -72f), Offset(-42f, -38f), Offset(-32f, -42f)),
        {
            moveTo(-36f, -72f)
            cubicTo(-42f, -68f, -44f, -52f, -42f, -38f)
            lineTo(-32f, -42f)
            cubicTo(-34f, -55f, -30f, -70f, -26f, -76f)
            close()
        }
    ),
    MusclePath2D(
        "TRICEPS", true,
        listOf(Offset(36f, -72f), Offset(42f, -38f), Offset(32f, -42f)),
        {
            moveTo(36f, -72f)
            cubicTo(42f, -68f, 44f, -52f, 42f, -38f)
            lineTo(32f, -42f)
            cubicTo(34f, -55f, 30f, -70f, 26f, -76f)
            close()
        }
    ),
    // Forearms
    MusclePath2D(
        "FOREARMS", false,
        listOf(Offset(-32f, -42f), Offset(-42f, -38f), Offset(-36f, 3f), Offset(-28f, -5f)),
        {
            moveTo(-32f, -42f)
            lineTo(-42f, -38f)
            cubicTo(-46f, -20f, -42f, -5f, -36f, 3f)
            lineTo(-28f, -5f)
            close()
        }
    ),
    MusclePath2D(
        "FOREARMS", true,
        listOf(Offset(32f, -42f), Offset(42f, -38f), Offset(36f, 3f), Offset(28f, -5f)),
        {
            moveTo(32f, -42f)
            lineTo(42f, -38f)
            cubicTo(46f, -20f, 42f, -5f, 36f, 3f)
            lineTo(28f, -5f)
            close()
        }
    ),
    // Lats
    MusclePath2D(
        "LATS", false,
        listOf(Offset(-26f, -64f), Offset(-28f, -48f), Offset(-16f, -25f), Offset(-15f, -60f)),
        {
            moveTo(-26f, -64f)
            cubicTo(-28f, -48f, -24f, -35f, -16f, -25f)
            lineTo(-15f, -60f)
            close()
        }
    ),
    MusclePath2D(
        "LATS", true,
        listOf(Offset(26f, -64f), Offset(28f, -48f), Offset(16f, -25f), Offset(15f, -60f)),
        {
            moveTo(26f, -64f)
            cubicTo(28f, -48f, 22f, -35f, 16f, -25f)
            lineTo(15f, -60f)
            close()
        }
    ),
    // Quads
    MusclePath2D(
        "QUADRICEPS", false,
        listOf(Offset(-21f, 44f), Offset(-17f, 105f), Offset(-7f, 105f), Offset(-4f, 42f)),
        {
            // Outer quad sweep
            moveTo(-6f, 43f)
            cubicTo(-8f, 65f, -9f, 85f, -7f, 105f)
            lineTo(-17f, 105f)
            cubicTo(-23f, 85f, -25f, 65f, -21f, 44f)
            close()
            // Vastus medialis (Teardrop)
            moveTo(-3f, 45f)
            lineTo(-5f, 85f)
            lineTo(-7f, 105f)
            lineTo(-3f, 105f)
            close()
        }
    ),
    MusclePath2D(
        "QUADRICEPS", true,
        listOf(Offset(21f, 44f), Offset(17f, 105f), Offset(7f, 105f), Offset(4f, 42f)),
        {
            // Outer quad sweep
            moveTo(6f, 43f)
            cubicTo(8f, 65f, 9f, 85f, 7f, 105f)
            lineTo(17f, 105f)
            cubicTo(23f, 85f, 25f, 65f, 21f, 44f)
            close()
            // Vastus medialis (Teardrop)
            moveTo(3f, 45f)
            lineTo(5f, 85f)
            lineTo(7f, 105f)
            lineTo(3f, 105f)
            close()
        }
    ),
    // Calves
    MusclePath2D(
        "CALVES", false,
        listOf(Offset(-17f, 105f), Offset(-21f, 130f), Offset(-10f, 172f), Offset(-7f, 105f)),
        {
            moveTo(-17f, 105f)
            cubicTo(-21f, 125f, -18f, 145f, -10f, 172f)
            lineTo(-5f, 172f)
            cubicTo(-7f, 145f, -8f, 125f, -7f, 105f)
            close()
        }
    ),
    MusclePath2D(
        "CALVES", true,
        listOf(Offset(17f, 105f), Offset(21f, 130f), Offset(10f, 172f), Offset(7f, 105f)),
        {
            moveTo(16f, 105f)
            cubicTo(21f, 125f, 18f, 145f, 10f, 172f)
            lineTo(5f, 172f)
            cubicTo(7f, 145f, 8f, 125f, 7f, 105f)
            close()
        }
    )
)

private val backMusclePaths = listOf(
    // Traps (Back)
    MusclePath2D(
        "TRAPS", false,
        listOf(Offset(-8f, -112f), Offset(-28f, -94f), Offset(-2f, -50f), Offset(-2f, -100f)),
        {
            moveTo(-8f, -112f)
            quadraticTo(-18f, -108f, -28f, -94f)
            lineTo(-2f, -50f)
            lineTo(-2f, -100f)
            close()
        }
    ),
    MusclePath2D(
        "TRAPS", true,
        listOf(Offset(8f, -112f), Offset(28f, -94f), Offset(2f, -50f), Offset(2f, -100f)),
        {
            moveTo(8f, -112f)
            quadraticTo(18f, -108f, 28f, -94f)
            lineTo(2f, -50f)
            lineTo(2f, -100f)
            close()
        }
    ),
    // Shoulders (Back)
    MusclePath2D(
        "SHOULDERS", false,
        listOf(Offset(-28f, -94f), Offset(-40f, -70f), Offset(-26f, -76f)),
        {
            moveTo(-28f, -94f)
            cubicTo(-34f, -92f, -40f, -84f, -40f, -70f)
            cubicTo(-38f, -60f, -32f, -64f, -26f, -76f)
            close()
        }
    ),
    MusclePath2D(
        "SHOULDERS", true,
        listOf(Offset(28f, -94f), Offset(40f, -70f), Offset(26f, -76f)),
        {
            moveTo(28f, -94f)
            cubicTo(34f, -92f, 40f, -84f, 40f, -70f)
            cubicTo(38f, -60f, 32f, -64f, 26f, -76f)
            close()
        }
    ),
    // Triceps (Back)
    MusclePath2D(
        "TRICEPS", false,
        listOf(Offset(-26f, -76f), Offset(-32f, -42f), Offset(-42f, -38f), Offset(-38f, -70f)),
        {
            moveTo(-26f, -76f)
            cubicTo(-30f, -70f, -34f, -55f, -32f, -42f)
            cubicTo(-40f, -38f, -42f, -50f, -38f, -70f)
            close()
        }
    ),
    MusclePath2D(
        "TRICEPS", true,
        listOf(Offset(26f, -76f), Offset(32f, -42f), Offset(42f, -38f), Offset(38f, -70f)),
        {
            moveTo(26f, -76f)
            cubicTo(30f, -70f, 34f, -55f, 32f, -42f)
            cubicTo(40f, -38f, 42f, -50f, 38f, -70f)
            close()
        }
    ),
    // Forearms (Back)
    MusclePath2D(
        "FOREARMS", false,
        listOf(Offset(-32f, -42f), Offset(-42f, -38f), Offset(-36f, 3f), Offset(-28f, -5f)),
        {
            moveTo(-32f, -42f)
            lineTo(-42f, -38f)
            cubicTo(-46f, -20f, -42f, -5f, -36f, 3f)
            lineTo(-28f, -5f)
            close()
        }
    ),
    MusclePath2D(
        "FOREARMS", true,
        listOf(Offset(32f, -42f), Offset(42f, -38f), Offset(36f, 3f), Offset(28f, -5f)),
        {
            moveTo(32f, -42f)
            lineTo(42f, -38f)
            cubicTo(46f, -20f, 42f, -5f, 36f, 3f)
            lineTo(28f, -5f)
            close()
        }
    ),
    // Middle Back
    MusclePath2D(
        "MIDDLE BACK", false,
        listOf(Offset(-2f, -90f), Offset(-26f, -78f), Offset(-15f, -40f), Offset(-2f, -40f)),
        {
            moveTo(-2f, -90f)
            lineTo(-26f, -78f)
            lineTo(-15f, -40f)
            lineTo(-2f, -40f)
            close()
        }
    ),
    MusclePath2D(
        "MIDDLE BACK", true,
        listOf(Offset(2f, -90f), Offset(26f, -78f), Offset(15f, -40f), Offset(2f, -40f)),
        {
            moveTo(2f, -90f)
            lineTo(26f, -78f)
            lineTo(15f, -40f)
            lineTo(2f, -40f)
            close()
        }
    ),
    // Lats (Back)
    MusclePath2D(
        "LATS", false,
        listOf(Offset(-26f, -78f), Offset(-20f, -35f), Offset(-12f, -22f), Offset(-4f, -58f)),
        {
            moveTo(-26f, -78f)
            cubicTo(-28f, -55f, -24f, -35f, -16f, -22f)
            lineTo(-2f, -40f)
            lineTo(-15f, -40f)
            close()
        }
    ),
    MusclePath2D(
        "LATS", true,
        listOf(Offset(25f, -78f), Offset(20f, -35f), Offset(12f, -22f), Offset(4f, -58f)),
        {
            moveTo(26f, -78f)
            cubicTo(28f, -55f, 24f, -35f, 16f, -22f)
            lineTo(2f, -40f)
            lineTo(15f, -40f)
            close()
        }
    ),
    // Lower Back
    MusclePath2D(
        "LOWER BACK", false,
        listOf(Offset(-2f, -40f), Offset(-15f, -40f), Offset(-12f, 12f), Offset(-2f, 12f)),
        {
            moveTo(-2f, -40f)
            lineTo(-15f, -40f)
            lineTo(-12f, 12f)
            lineTo(-2f, 12f)
            close()
        }
    ),
    MusclePath2D(
        "LOWER BACK", true,
        listOf(Offset(2f, -40f), Offset(15f, -40f), Offset(12f, 12f), Offset(2f, 12f)),
        {
            moveTo(2f, -40f)
            lineTo(15f, -40f)
            lineTo(12f, 12f)
            lineTo(2f, 12f)
            close()
        }
    ),
    // Glutes (Back)
    MusclePath2D(
        "GLUTES", false,
        listOf(Offset(-2f, 12f), Offset(-21f, 25f), Offset(-19f, 44f), Offset(-2f, 42f)),
        {
            moveTo(-2f, 12f)
            cubicTo(-14f, 15f, -21f, 25f, -19f, 44f)
            cubicTo(-15f, 48f, -8f, 48f, -2f, 42f)
            close()
        }
    ),
    MusclePath2D(
        "GLUTES", true,
        listOf(Offset(2f, 12f), Offset(21f, 25f), Offset(19f, 44f), Offset(2f, 42f)),
        {
            moveTo(2f, 12f)
            cubicTo(14f, 15f, 21f, 25f, 19f, 44f)
            cubicTo(16f, 48f, 8f, 48f, 2f, 42f)
            close()
        }
    ),
    // Hamstrings (Back)
    MusclePath2D(
        "HAMSTRINGS", false,
        listOf(Offset(-19f, 44f), Offset(-16f, 105f), Offset(-7f, 105f), Offset(-2f, 42f)),
        {
            moveTo(-19f, 44f)
            cubicTo(-22f, 75f, -18f, 95f, -16f, 105f)
            lineTo(-7f, 105f)
            cubicTo(-8f, 75f, -7f, 50f, -2f, 42f)
            close()
        }
    ),
    MusclePath2D(
        "HAMSTRINGS", true,
        listOf(Offset(19f, 44f), Offset(16f, 105f), Offset(7f, 105f), Offset(2f, 42f)),
        {
            moveTo(19f, 44f)
            cubicTo(22f, 75f, 18f, 95f, 16f, 105f)
            lineTo(7f, 105f)
            cubicTo(8f, 75f, 7f, 50f, 2f, 42f)
            close()
        }
    ),
    // Calves (Back)
    MusclePath2D(
        "CALVES", false,
        listOf(Offset(-16f, 105f), Offset(-21f, 130f), Offset(-10f, 172f), Offset(-7f, 105f)),
        {
            moveTo(-16f, 105f)
            cubicTo(-21f, 125f, -18f, 145f, -10f, 172f)
            lineTo(-5f, 172f)
            cubicTo(-7f, 145f, -8f, 125f, -7f, 105f)
            close()
        }
    ),
    MusclePath2D(
        "CALVES", true,
        listOf(Offset(16f, 105f), Offset(21f, 130f), Offset(10f, 172f), Offset(7f, 105f)),
        {
            moveTo(16f, 105f)
            cubicTo(21f, 125f, 18f, 145f, 10f, 172f)
            lineTo(5f, 172f)
            cubicTo(7f, 145f, 8f, 125f, 7f, 105f)
            close()
        }
    )
)

private fun isPointInPolygon(px: Float, py: Float, polygonPoints: List<Offset>): Boolean {
    var isInside = false
    var j = polygonPoints.size - 1
    for (i in polygonPoints.indices) {
        val xi = polygonPoints[i].x
        val yi = polygonPoints[i].y
        val xj = polygonPoints[j].x
        val yj = polygonPoints[j].y
        
        val intersect = ((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / (yj - yi + 1e-6f) + xi)
        if (intersect) {
            isInside = !isInside
        }
        j = i
    }
    return isInside
}

private fun getMuscleDevelopmentAdvice(muscleGroup: String): String {
    return when (muscleGroup.lowercase()) {
        "chest" -> "Target: 8,000 - 12,000 weekly stimulus units. Recovery half-life: 72 hours. Focus on chest press and dumbbell flies for optimal progressive mechanical tension."
        "lats" -> "Target: 9,000 - 13,000 weekly stimulus units. Recovery half-life: 72 hours. Focus on wide-grip pull-ups and lat pulldowns to maximize lateral width."
        "middle back" -> "Target: 9,000 - 13,000 weekly stimulus units. Recovery half-life: 72 hours. Program horizontal rows to develop mid-back thickness and improve shoulder posture."
        "lower back" -> "Target: 9,000 - 13,000 weekly stimulus units. Recovery half-life: 72 hours. Focus on deadlifts and hyperextensions to strengthen the erector spinae."
        "shoulders" -> "Target: 5,000 - 9,000 weekly stimulus units. Recovery half-life: 60 hours. Train lateral raises and overhead presses to emphasize the medial and anterior deltoids."
        "biceps" -> "Target: 4,000 - 8,000 weekly stimulus units. Recovery half-life: 60 hours. Focus on incline dumbbell curls and hammer curls for high neurological activation."
        "triceps" -> "Target: 4,000 - 8,000 weekly stimulus units. Recovery half-life: 60 hours. Emphasize overhead extensions and cable pressdowns to stimulate the lateral and long heads."
        "forearms" -> "Target: 4,000 - 8,000 weekly stimulus units. Recovery half-life: 36 hours. Program reverse wrist curls and plate pinches to develop grip strength."
        "traps" -> "Target: 9,000 - 13,000 weekly stimulus units. Recovery half-life: 36 hours. Use heavy dumbbell shrugs and face pulls to target the upper and middle fibers."
        "quadriceps" -> "Target: 10,000 - 15,000 weekly stimulus units. Recovery half-life: 72 hours. Emphasize squats, leg presses, and leg extensions for high-threshold motor unit recruitment."
        "hamstrings" -> "Target: 10,000 - 15,000 weekly stimulus units. Recovery half-life: 72 hours. Program Romanian deadlifts and lying leg curls to match quadriceps agonist volume."
        "glutes" -> "Target: 10,000 - 15,000 weekly stimulus units. Recovery half-life: 60 hours. Focus on hip thrusts and lunges to isolate the gluteus maximus."
        "calves" -> "Target: 3,000 - 6,000 weekly stimulus units. Recovery half-life: 36 hours. Perform standing and seated calf raises to target the gastrocnemius and soleus."
        "abdominals" -> "Target: 3,000 - 6,000 weekly stimulus units. Recovery half-life: 60 hours. Focus on weighted crunches and hanging leg raises to overload the rectus abdominis."
        else -> "Recommended stimulus: 4,000 - 8,000 units. Ensure consistent volume loading with proper recovery intervals."
    }
}

@Composable
fun ExpandableMuscleDevelopmentCard(muscles: List<MuscleDevelopment>) {
    var expanded by remember { mutableStateOf(false) }
    var selectedMuscleName by remember { mutableStateOf<String?>("CHEST") }
    var rotationAngle by remember { mutableStateOf(0f) }
    
    val activeMuscle = muscles.firstOrNull { it.muscleGroup.uppercase() == selectedMuscleName }
    val progressValue = activeMuscle?.let { (it.growthIndex - 100f) / 100f } ?: 0f
    
    val sortedMuscles = remember(muscles) { muscles.sortedByDescending { it.growthIndex } }
    
    val displayedMuscles = if (expanded) {
        sortedMuscles
    } else {
        if (sortedMuscles.size > 6) {
            sortedMuscles.take(3) + sortedMuscles.takeLast(3)
        } else {
            sortedMuscles
        }
    }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Muscle Development Index",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Drag horizontally to rotate body. Tap muscles to inspect details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 3D Organic Muscular Body Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                rotationAngle += dragAmount.x * 0.012f
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { tapOffset ->
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f + 10.dp.toPx()
                                val scale = Math.min(size.width / 160f, size.height / 360f) * 0.9f
                                
                                val cosT = Math.cos(rotationAngle.toDouble()).toFloat()
                                val scaleX = cosT.absoluteValue
                                val isFront = cosT >= 0f
                                val activePaths = if (isFront) frontMusclePaths else backMusclePaths
                                
                                val dx = tapOffset.x - centerX
                                val dy = tapOffset.y - centerY
                                
                                val localX = if (scaleX > 0.05f) dx / (scale * scaleX) else 0f
                                val localY = dy / scale
                                val tappedPoint = Offset(localX, localY)
                                
                                val clickedMuscle = activePaths.firstOrNull { item ->
                                    isPointInPolygon(tappedPoint.x, tappedPoint.y, item.hitPolygon)
                                }
                                
                                if (clickedMuscle != null) {
                                    selectedMuscleName = clickedMuscle.muscleName.uppercase()
                                } else {
                                    // Fallback to closest muscle within reasonable range for easier navigation
                                    var closestMuscle: MusclePath2D? = null
                                    var minDistance = Float.MAX_VALUE
                                    for (item in activePaths) {
                                        for (p in item.hitPolygon) {
                                            val dxp = tappedPoint.x - p.x
                                            val dyp = tappedPoint.y - p.y
                                            val dist = dxp * dxp + dyp * dyp
                                            if (dist < minDistance) {
                                                minDistance = dist
                                                closestMuscle = item
                                            }
                                        }
                                    }
                                    // If within 25 units (in local coordinate space), auto-select it
                                    if (closestMuscle != null && minDistance < 625f) {
                                        selectedMuscleName = closestMuscle.muscleName.uppercase()
                                    }
                                }
                            }
                        }
                ) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f + 10.dp.toPx()
                    val scale = Math.min(size.width / 160f, size.height / 360f) * 0.9f
                    
                    val cosT = Math.cos(rotationAngle.toDouble()).toFloat()
                    val scaleX = cosT.absoluteValue
                    val isFront = cosT >= 0f
                    val activePaths = if (isFront) frontMusclePaths else backMusclePaths
                    
                    withTransform({
                        this.translate(left = centerX, top = centerY)
                        this.scale(scaleX = scaleX, scaleY = 1.0f, pivot = Offset.Zero)
                        this.scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        // Draw organic Body Silhouette Outline
                        val silhouettePath = Path().apply { drawBodySilhouette() }
                        drawPath(silhouettePath, color = Color(0xFF161626).copy(alpha = 0.5f), style = Fill)
                        drawPath(silhouettePath, color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 1.5f))
                        
                        // Draw subtle anatomical details to enhance realism
                        if (isFront) {
                            // Clavicles
                            val clavicleLeft = Path().apply {
                                moveTo(-2f, -96f)
                                quadraticTo(-15f, -98f, -28f, -94f)
                            }
                            drawPath(clavicleLeft, color = Color.White.copy(alpha = 0.2f), style = Stroke(width = 1f))
                            
                            val clavicleRight = Path().apply {
                                moveTo(2f, -96f)
                                quadraticTo(15f, -98f, 28f, -94f)
                            }
                            drawPath(clavicleRight, color = Color.White.copy(alpha = 0.2f), style = Stroke(width = 1f))
                            
                            // Sternum
                            val sternum = Path().apply {
                                moveTo(0f, -96f)
                                lineTo(0f, -64f)
                            }
                            drawPath(sternum, color = Color.White.copy(alpha = 0.2f), style = Stroke(width = 1f))
                            
                            // Linea Alba
                            val lineaAlba = Path().apply {
                                moveTo(0f, -64f)
                                lineTo(0f, 20f)
                            }
                            drawPath(lineaAlba, color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 1f))
                            
                            // Rib cage accents
                            val ribsLeft = Path().apply {
                                moveTo(-14f, -54f)
                                quadraticTo(-20f, -48f, -24f, -52f)
                                moveTo(-14f, -44f)
                                quadraticTo(-20f, -38f, -22f, -42f)
                            }
                            drawPath(ribsLeft, color = Color.White.copy(alpha = 0.12f), style = Stroke(width = 1f))
                            
                            val ribsRight = Path().apply {
                                moveTo(14f, -54f)
                                quadraticTo(20f, -48f, 24f, -52f)
                                moveTo(14f, -44f)
                                quadraticTo(20f, -38f, 22f, -42f)
                            }
                            drawPath(ribsRight, color = Color.White.copy(alpha = 0.12f), style = Stroke(width = 1f))
                        } else {
                            // Spine
                            val spine = Path().apply {
                                moveTo(0f, -100f)
                                lineTo(0f, 40f)
                            }
                            drawPath(spine, color = Color.White.copy(alpha = 0.2f), style = Stroke(width = 1f))
                            
                            // Scapulae (Shoulder blades)
                            val scapulaLeft = Path().apply {
                                moveTo(-6f, -85f)
                                lineTo(-18f, -80f)
                                lineTo(-12f, -68f)
                            }
                            drawPath(scapulaLeft, color = Color.White.copy(alpha = 0.12f), style = Stroke(width = 1f))
                            
                            // Scapulae Right
                            val scapulaRight = Path().apply {
                                moveTo(6f, -85f)
                                lineTo(18f, -80f)
                                lineTo(12f, -68f)
                            }
                            drawPath(scapulaRight, color = Color.White.copy(alpha = 0.12f), style = Stroke(width = 1f))
                        }
                        
                        // Draw organic, highly realistic sculpted muscle pathways
                        activePaths.forEach { item ->
                            val isSelected = item.muscleName.uppercase() == selectedMuscleName
                            val muscle = muscles.firstOrNull { it.muscleGroup.uppercase() == item.muscleName.uppercase() }
                            val mdiProgress = muscle?.let { (it.growthIndex - 100f) / 100f } ?: 0f
                            
                            val baseFillColor = if (isSelected) Teal else {
                                val startColor = Color(0xFF282846).copy(alpha = 0.6f)
                                val endColor = Teal.copy(alpha = 0.9f)
                                val fraction = mdiProgress.coerceIn(0f, 1f)
                                Color(
                                    red = (startColor.red + (endColor.red - startColor.red) * fraction).coerceIn(0f, 1f),
                                    green = (startColor.green + (endColor.green - startColor.green) * fraction).coerceIn(0f, 1f),
                                    blue = (startColor.blue + (endColor.blue - startColor.blue) * fraction).coerceIn(0f, 1f),
                                    alpha = (startColor.alpha + (endColor.alpha - startColor.alpha) * fraction).coerceIn(0f, 1f)
                                )
                            }
                            
                            val path = Path().apply { item.drawAction(this) }
                            
                            // 3D Spherical/Volumetric Highlight Lighting Gradient
                            val bounds = item.hitPolygon
                            val centerXLocal = if (bounds.isNotEmpty()) bounds.map { it.x }.average().toFloat() else 0f
                            val centerYLocal = if (bounds.isNotEmpty()) bounds.map { it.y }.average().toFloat() else 0f
                            val maxRadius = if (bounds.isNotEmpty()) {
                                bounds.map { Math.sqrt(((it.x - centerXLocal) * (it.x - centerXLocal) + (it.y - centerYLocal) * (it.y - centerYLocal)).toDouble()).toFloat() }.maxOrNull() ?: 20f
                            } else 20f
                            
                            // Highlighting shifts light center slightly to simulate 3D curvature
                            val lightingCenter = Offset(
                                x = centerXLocal + if (item.isLeft) -maxRadius * 0.15f else maxRadius * 0.15f,
                                y = centerYLocal - maxRadius * 0.15f
                            )
                            
                            val depthIntensity = if (item.isLeft) 0.82f else 1.0f
                            val darkerBase = baseFillColor.copy(
                                red = (baseFillColor.red * depthIntensity * 0.5f).coerceIn(0f, 1f),
                                green = (baseFillColor.green * depthIntensity * 0.5f).coerceIn(0f, 1f),
                                blue = (baseFillColor.blue * depthIntensity * 0.5f).coerceIn(0f, 1f)
                            )
                            val lighterBase = baseFillColor.copy(
                                red = (baseFillColor.red + (1f - baseFillColor.red) * 0.2f).coerceIn(0f, 1f),
                                green = (baseFillColor.green + (1f - baseFillColor.green) * 0.2f).coerceIn(0f, 1f),
                                blue = (baseFillColor.blue + (1f - baseFillColor.blue) * 0.2f).coerceIn(0f, 1f)
                            )
                            
                            val volumetricBrush = Brush.radialGradient(
                                colors = listOf(lighterBase, baseFillColor, darkerBase),
                                center = lightingCenter,
                                radius = maxRadius.coerceAtLeast(10f)
                            )
                            
                            drawPath(path, brush = volumetricBrush, style = Fill)
                            drawPath(
                                path, 
                                color = if (isSelected) Teal else Color.White.copy(alpha = 0.25f), 
                                style = Stroke(width = if (isSelected) 1.8f else 0.6f)
                            )
                        }    
                    }
                }
            }

            // Selected Muscle Info Panel
            AnimatedVisibility(
                visible = activeMuscle != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                activeMuscle?.let { muscle ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = muscle.muscleGroup.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format(Locale.US, "MDI %.1f", muscle.growthIndex),
                                        color = Teal,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = String.format(Locale.US, "(+%.1f%%)", muscle.percentageGrowth),
                                        color = Violet,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.06f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressValue.coerceIn(0f, 1f))
                                        .clip(CircleShape)
                                        .background(Brush.horizontalGradient(listOf(Teal, Violet)))
                                )
                            }
                            
                            Spacer(Modifier.height(14.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Weekly Stimulus", color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${muscle.stimulusThisWeek.toInt()} units", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Physique Saturation", color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${(progressValue * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(Modifier.height(10.dp))
                            
                            val advice = getMuscleDevelopmentAdvice(muscle.muscleGroup)
                            Text(
                                text = advice,
                                color = OnSurface,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                displayedMuscles.forEach { muscle ->
                    val isSelected = muscle.muscleGroup.uppercase() == selectedMuscleName
                    val progressVal = (muscle.growthIndex - 100f) / 100f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMuscleName = muscle.muscleGroup.uppercase() }
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.03f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                muscle.muscleGroup.uppercase(),
                                color = if (isSelected) Teal else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    String.format(Locale.US, "MDI %.1f", muscle.growthIndex),
                                    color = Teal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    String.format(Locale.US, "(+%.1f%%)", muscle.percentageGrowth),
                                    color = Violet,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressVal.coerceIn(0f, 1f))
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(Teal, Violet)))
                            )
                        }
                    }
                }
            }

            if (sortedMuscles.size > 6) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (expanded) "Show Strengths & Weaknesses Only" else "Expand All Muscles (+${sortedMuscles.size - 6})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FlippableMuscleStimulusCard(heatmap: List<MuscleStimulusHeatmap>) {
    var selectedMuscle by remember { mutableStateOf<String?>(null) }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Weekly Muscle Stimulus Units",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Click muscle cards to check volume sufficiency advice.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                heatmap.forEach { item ->
                    val color = when (item.status) {
                        "Optimal" -> Teal
                        "Overtrained" -> Coral
                        else -> Color(0xFFFBBF24)
                    }
                    val isSelected = selectedMuscle == item.muscleGroup

                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) color else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedMuscle = if (isSelected) null else item.muscleGroup
                            }
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = item.muscleGroup.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${item.stimulus.toInt()}",
                                color = color,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "UNITS",
                                color = color.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((item.ratio).coerceIn(0.05f, 1f))
                                        .background(color)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.status,
                                color = color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Detail advice window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val activeItem = heatmap.firstOrNull { it.muscleGroup == selectedMuscle }
                if (activeItem != null) {
                    val statusText = when (activeItem.status) {
                        "Optimal" -> "Optimal volume achieved. Biomechanic recovery matches stimulus. Recommended: target range ${activeItem.minRecommended.toInt()} - ${activeItem.maxRecommended.toInt()} units."
                        "Overtrained" -> "Systemic threshold exceeded. Continued overtraining reduces growth and increases connective tear risk. Deload sets next week."
                        else -> "Under-stimulated. Growth stimulus is minimal. Program 2-3 additional sets of isolation or compound exercises."
                    }
                    Column {
                        Text(activeItem.muscleGroup.uppercase(), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(statusText, color = OnSurface, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tap any muscle card above to view custom training advice.", color = OnSurfaceMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleFatigueCountdownCard(fatigueMap: Map<String, MuscleFatigue>) {
    var selectedMuscle by remember { mutableStateOf<String?>(null) }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Muscle Fatigue & Recovery Status",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Dynamic physiological countdown checks to avoid acute injury.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                fatigueMap.forEach { (muscle, fatigue) ->
                    val isSelected = selectedMuscle == muscle
                    val pct = fatigue.fatiguePercentage
                    val color = when {
                        pct > 50 -> Coral
                        pct > 20 -> Color(0xFFFBBF24)
                        else -> Teal
                    }
                    val isFatigued = fatigue.recoveryTimeRemainingHours > 0
                    val cardBg = when {
                        isFatigued -> if (isSelected) Coral.copy(alpha = 0.2f) else Coral.copy(alpha = 0.1f)
                        else -> if (isSelected) Teal.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                    }
                    val cardBorderColor = when {
                        isFatigued -> if (isSelected) Coral else Coral.copy(alpha = 0.4f)
                        else -> if (isSelected) Teal else Color.White.copy(alpha = 0.08f)
                    }

                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(
                                width = 1.dp,
                                color = cardBorderColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedMuscle = if (isSelected) null else muscle
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = muscle.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(12.dp))
                            
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.05f),
                                        radius = size.minDimension / 2f
                                    )
                                    drawArc(
                                        color = color,
                                        startAngle = -90f,
                                        sweepAngle = (pct / 100f) * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                
                                if (pct == 0) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(
                                        text = "$pct%",
                                        color = color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = if (fatigue.recoveryTimeRemainingHours > 0) "${fatigue.recoveryTimeRemainingHours}h Left" else "READY",
                                color = color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Detail advice box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (selectedMuscle != null) {
                    val fatigueObj = fatigueMap[selectedMuscle]
                    if (fatigueObj != null && fatigueObj.recoveryTimeRemainingHours > 0) {
                        Column {
                            Text(selectedMuscle!!.uppercase(), color = Coral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Highly fatigued. Connective tissues are still recovering. Avoid heavy target lifts or exercises straining $selectedMuscle for the next ${fatigueObj.recoveryTimeRemainingHours} hours to reduce tear risk.",
                                color = OnSurface,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    } else {
                        Column {
                            Text(selectedMuscle!!.uppercase(), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Fully recovered and ready for loading. Glycogen stores and nervous system pathways are cleared.",
                                color = OnSurface,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tap any muscle recovery gauge above to view lift restrictions.", color = OnSurfaceMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: FORECAST & TIMELINE COMPOSABLES
// ==========================================

@Composable
fun StrengthForecastChartCard(
    selectedExerciseId: String?,
    exerciseOptions: List<String>,
    onSelectExercise: (String) -> Unit,
    projections: List<StrengthProjectionPoint>
) {
    val activeProjection = projections.firstOrNull { it.exerciseId == selectedExerciseId }
        ?: projections.firstOrNull()

    var expanded by remember { mutableStateOf(false) }

    val blurRadius by animateDpAsState(
        targetValue = if (expanded) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "DropdownBlur"
    )

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.blur(blurRadius)) {
                Text(
                    "Strength Forecast",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Interactive regression chart plotting estimated 1RM (Epley).",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
            }

            Spacer(Modifier.height(20.dp))

            // Premium Selector Dropdown
            Box {
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = (selectedExerciseId ?: activeProjection?.exerciseId ?: "Select Exercise").formatExerciseName(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = SurfaceVariant,
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(12.dp),
                            clip = true,
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        )
                        .background(SurfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                ) {
                    if (exerciseOptions.size > 4) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 6.dp, start = 14.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = OnSurfaceMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Scroll for more exercises",
                                color = OnSurfaceMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 4.dp),
                            color = Color.White.copy(alpha = 0.05f)
                        )
                    }

                    exerciseOptions.forEach { option ->
                        val isSelected = option == selectedExerciseId || (selectedExerciseId == null && option == activeProjection?.exerciseId)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.formatExerciseName(),
                                    color = if (isSelected) Teal else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = if (isSelected) {
                                Modifier.background(Teal.copy(alpha = 0.08f))
                            } else {
                                Modifier
                            },
                            onClick = {
                                onSelectExercise(option)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(blurRadius)
            ) {
                if (activeProjection != null) {
                    InteractiveLineChart(historyPoints = activeProjection.historyPoints)

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = String.format(Locale.US, "%.1f kg", activeProjection.current1RM),
                                fontSize = 28.sp,
                                color = Teal,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "CURRENT ESTIMATED 1RM",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceMuted,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(16.dp))

                    val projectionsList = listOf(
                        Pair("30 Days Forecast", activeProjection.projected30Days),
                        Pair("90 Days Forecast", activeProjection.projected90Days),
                        Pair("365 Days Forecast", activeProjection.projected365Days)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        projectionsList.forEach { (label, value) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(label, color = OnSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = value,
                                    color = if (value.contains("kg")) Teal else Coral,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (label != "365 Days Forecast") {
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Log lifting sets for this exercise to compute trajectory forecasts.",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveLineChart(historyPoints: List<Pair<Long, Float>>) {
    if (historyPoints.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Not enough lifting history to render trajectory line.",
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    val minTime = historyPoints.minOf { it.first }
    val maxTime = historyPoints.maxOf { it.first }
    val timeDiff = (maxTime - minTime).coerceAtLeast(1L)
    
    val minWeight = historyPoints.minOf { it.second } * 0.9f
    val maxWeight = historyPoints.maxOf { it.second } * 1.1f
    val weightDiff = (maxWeight - minWeight).coerceAtLeast(1f)

    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(historyPoints) {
                        detectTapGestures { tapOffset ->
                            val width = size.width
                            val height = size.height
                            
                            var closestIdx = -1
                            var minDist = Float.MAX_VALUE
                            
                            historyPoints.forEachIndexed { idx, (time, weight) ->
                                val x = ((time - minTime).toFloat() / timeDiff) * (width - 48.dp.toPx()) + 24.dp.toPx()
                                val y = height - (((weight - minWeight) / weightDiff) * (height - 48.dp.toPx()) + 24.dp.toPx())
                                
                                val dist = (tapOffset - Offset(x, y)).getDistance()
                                if (dist < minDist && dist < 30.dp.toPx()) {
                                    minDist = dist
                                    closestIdx = idx
                                }
                            }
                            selectedIndex = if (closestIdx != -1) closestIdx else null
                        }
                    }
            ) {
                val width = size.width
                val height = size.height

                val points = historyPoints.map { (time, weight) ->
                    val x = ((time - minTime).toFloat() / timeDiff) * (width - 48.dp.toPx()) + 24.dp.toPx()
                    val y = height - (((weight - minWeight) / weightDiff) * (height - 48.dp.toPx()) + 24.dp.toPx())
                    Offset(x, y)
                }

                for (i in 0..4) {
                    val gridY = height - (i * (height - 48.dp.toPx()) / 4f + 24.dp.toPx())
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(24.dp.toPx(), gridY),
                        end = Offset(width - 24.dp.toPx(), gridY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val strokePath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val cp1 = Offset(prev.x + (curr.x - prev.x) / 2f, prev.y)
                            val cp2 = Offset(prev.x + (curr.x - prev.x) / 2f, curr.y)
                            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, curr.x, curr.y)
                        }
                    }
                }
                drawPath(
                    path = strokePath,
                    color = Teal,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val fillPath = android.graphics.Path()
                if (points.isNotEmpty()) {
                    fillPath.moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        fillPath.cubicTo(
                            prev.x + (curr.x - prev.x) / 2f, prev.y,
                            prev.x + (curr.x - prev.x) / 2f, curr.y,
                            curr.x, curr.y
                        )
                    }
                    fillPath.lineTo(points.last().x, height - 24.dp.toPx())
                    fillPath.lineTo(points.first().x, height - 24.dp.toPx())
                    fillPath.close()
                    val nativePaint = android.graphics.Paint().apply {
                        shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, height,
                            Teal.copy(alpha = 0.25f).toArgb(),
                            Color.Transparent.toArgb(),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(
                            fillPath,
                            nativePaint
                        )
                    }
                }

                points.forEachIndexed { idx, offset ->
                    val isSelected = selectedIndex == idx
                    drawCircle(
                        color = if (isSelected) Color.White else Teal,
                        radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                        center = offset
                    )
                    if (isSelected) {
                        drawCircle(
                            color = Teal.copy(alpha = 0.3f),
                            radius = 12.dp.toPx(),
                            center = offset
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.03f)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedIndex != null && selectedIndex!! < historyPoints.size) {
                val point = historyPoints[selectedIndex!!]
                val dateStr = dateFormat.format(Date(point.first))
                Text(
                    text = String.format(Locale.US, "Point %d: %s • %.1f kg 1RM", selectedIndex!! + 1, dateStr, point.second),
                    color = Teal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Tap on chart points to inspect historical data readings.",
                        color = OnSurfaceMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SaturationScrubPhysiqueCard(predictions: List<PhysiqueProjectionPoint>) {
    var timelineStep by remember { mutableStateOf(0) }
    
    val steps = listOf("Current", "4 Weeks", "12 Weeks", "1 Year")

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Physique Projection Forecast",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Physique Saturation Model predictions. Drag slider to scrub time.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )

            Spacer(Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                predictions.forEach { item ->
                    val projectedMDI = when (timelineStep) {
                        0 -> item.currentMuscleIndex
                        1 -> item.projectedIndex4Weeks
                        2 -> item.projectedIndex12Weeks
                        else -> item.projectedIndex52Weeks
                    }
                    val growthPercent = if (item.currentMuscleIndex > 0) {
                        ((projectedMDI - item.currentMuscleIndex) / item.currentMuscleIndex) * 100
                    } else 0f

                    val animatedMdi by animateFloatAsState(
                        targetValue = projectedMDI,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "mdi_anim"
                    )

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.muscleGroup.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Row {
                                Text(
                                    String.format(Locale.US, "MDI %.1f", animatedMdi),
                                    color = Teal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (timelineStep > 0 && growthPercent > 0.05f) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        String.format(Locale.US, "(+%.1f%%)", growthPercent),
                                        color = Violet,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))

                        val progressFraction = ((animatedMdi - 100f) / 100f).coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction)
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(Teal, Violet)))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    steps.forEachIndexed { idx, step ->
                        Text(
                            text = step,
                            color = if (timelineStep == idx) Teal else OnSurfaceMuted,
                            fontWeight = if (timelineStep == idx) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
                
                Slider(
                    value = timelineStep.toFloat(),
                    onValueChange = { timelineStep = it.toInt().coerceIn(0, 3) },
                    valueRange = 0f..3f,
                    steps = 2,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Teal,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                        thumbColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun InteractiveWeeklyVolumeCard(weeklyVolume: List<Pair<String, Float>>) {
    var selectedWeekIdx by remember { mutableStateOf<Int?>(null) }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Weekly Training Volume Timeline",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Interactive timeline tracker across previous 8 training weeks.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )

            Spacer(Modifier.height(30.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxVolume = weeklyVolume.maxOfOrNull { it.second } ?: 1f

                weeklyVolume.forEachIndexed { index, (week, vol) ->
                    val isSelected = selectedWeekIdx == index
                    val barHeightRatio = if (maxVolume > 0) vol / maxVolume else 0f
                    
                    val animatedHeightRatio by animateFloatAsState(
                        targetValue = barHeightRatio,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "height_anim"
                    )

                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "scale_anim"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedWeekIdx = if (isSelected) null else index
                            }
                    ) {
                        Text(
                            text = if (vol > 0) "${(vol / 1000).toInt()}k" else "-",
                            color = if (isSelected) Teal else Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.scale(animatedScale)
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .height(80.dp)
                                .fillMaxWidth()
                                .scale(animatedScale),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .fillMaxHeight(animatedHeightRatio.coerceIn(0.05f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            if (isSelected) listOf(Teal, Color.White) else listOf(Teal, Violet)
                                        )
                                    )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = week,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selectedWeekIdx != null && selectedWeekIdx!! < weeklyVolume.size) {
                    val (week, vol) = weeklyVolume[selectedWeekIdx!!]
                    Text(
                        text = String.format(Locale.US, "%s Total Volume: %,.0f kg lifted", week, vol),
                        color = Teal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tap any bar in the timeline to view exact tonnage values.", color = OnSurfaceMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TabSelector(
    tabs: List<Pair<String, ImageVector>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(Surface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEachIndexed { index, pair ->
            val isSelected = selectedTab == index
            val animatedBackground by animateColorAsState(
                targetValue = if (isSelected) SurfaceVariant else Color.Transparent,
                animationSpec = tween(300),
                label = "tab_bg"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "tab_scale"
            )
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) Teal else Color.White.copy(alpha = 0.6f),
                animationSpec = tween(300),
                label = "tab_color"
            )
            
            val borderModifier = if (isSelected) {
                Modifier.border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(Teal, Violet)),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                Modifier
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(animatedBackground)
                    .then(borderModifier)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp)
                    .scale(scale),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = pair.second,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.titleSmall,
                    color = animatedColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

