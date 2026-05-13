package com.trackme.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.model.HealthMetric
import com.trackme.ui.components.GlassmorphicCard
import com.trackme.ui.components.ProfileMeshGradient
import com.trackme.ui.profile.ProfileDashboardState
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun HealthMetricsScreen(
    onBack: () -> Unit,
    viewModel: HealthMetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        ProfileMeshGradient(state = ProfileDashboardState.COSMOS)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                HealthMetricsHeader(onBack = onBack)
            }

            if (state.isLoading) {
                item { LoadingCard() }
            } else {
                val displayMetrics = metricsWithTodayPlaceholders(state.metrics)
                item { HealthMetricsSummaryCard(state.metrics) }
                displayMetrics.groupBy { it.category }.forEach { (category, metrics) ->
                        item {
                            Text(
                                category,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.88f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(metrics, key = { it.id }) { metric ->
                            HealthMetricCard(metric)
                        }
                }
            }
        }
    }
}

@Composable
private fun HealthMetricsHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Health Metrics",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Today from Health Connect",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    GlassmorphicCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = Violet, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(
                "Loading health metrics",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyMetricsCard() {
    GlassmorphicCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Dataset, contentDescription = null, tint = Violet, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "No Health Connect records found",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Sync again after Health Connect has data for the last 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun HealthMetricsSummaryCard(metrics: List<HealthMetric>) {
    val categoryCount = metrics.map { it.category }.distinct().size
    val sourceCount = metrics.mapNotNull { it.sourceApp }.distinct().size
    val latest = metrics.maxOfOrNull { it.updatedAt }

    GlassmorphicCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryPill(Icons.Default.Dataset, metrics.size.toString(), "Records")
            SummaryPill(Icons.Default.Category, categoryCount.toString(), "Types")
            SummaryPill(Icons.Default.Hub, sourceCount.toString(), "Sources")
        }
        latest?.let {
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Synced ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun RowScope.SummaryPill(icon: ImageVector, value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.48f), maxLines = 1)
    }
}

@Composable
private fun HealthMetricCard(metric: HealthMetric) {
    GlassmorphicCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor(metric.category).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoryIcon(metric.category), contentDescription = null, tint = categoryColor(metric.category), modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        metric.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatMetricTime(metric),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.46f),
                    )
                    metric.sourceApp?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.34f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        metric.primaryValue,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    metric.primaryUnit?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor(metric.category),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (metric.details.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    metric.details.forEach { detail ->
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.68f),
                        )
                    }
                }
            }
        }
    }
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "Activity" -> Icons.AutoMirrored.Filled.DirectionsRun
    "Body" -> Icons.Default.MonitorWeight
    "Vitals" -> Icons.Default.Favorite
    "Cycle" -> Icons.Default.Spa
    "Nutrition" -> Icons.Default.Restaurant
    "Sleep" -> Icons.Default.Bedtime
    "Wellness" -> Icons.Default.SelfImprovement
    "Medical" -> Icons.Default.MedicalInformation
    else -> Icons.Default.Dataset
}

private fun categoryColor(category: String): Color = when (category) {
    "Activity" -> Blue
    "Body" -> Violet
    "Vitals" -> Coral
    "Cycle" -> Color(0xFFF0ABFC)
    "Nutrition" -> Teal
    "Sleep" -> Color(0xFF93C5FD)
    "Wellness" -> Color(0xFFFBBF24)
    "Medical" -> Color(0xFF5EEAD4)
    else -> OnSurfaceMuted
}

private fun formatMetricTime(metric: HealthMetric): String {
    val start = formatDateTime(metric.startTime)
    val end = metric.endTime?.let { formatDateTime(it) }
    return if (end == null || end == start) start else "$start - $end"
}

private fun formatDateTime(time: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(time))

private data class TodayMetricDefinition(
    val recordType: String,
    val category: String,
    val displayName: String,
    val unit: String? = null,
)

private fun metricsWithTodayPlaceholders(metrics: List<HealthMetric>): List<HealthMetric> {
    val presentRecordTypes = metrics.map { it.recordType }.toSet()
    val zone = ZoneId.systemDefault()
    val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val placeholders = todayMetricDefinitions
        .filterNot { it.recordType in presentRecordTypes }
        .map { definition ->
            HealthMetric(
                id = "today_placeholder_${definition.recordType}",
                userId = "",
                category = definition.category,
                recordType = definition.recordType,
                displayName = definition.displayName,
                startTime = todayStart,
                endTime = null,
                primaryValue = "Not recorded",
                primaryUnit = definition.unit,
                details = listOf("No value recorded today"),
                sourceApp = null,
                rawData = null,
                updatedAt = todayStart,
            )
        }
    return (metrics + placeholders).sortedWith(
        compareBy<HealthMetric> { categorySortIndex(it.category) }
            .thenBy { it.displayName }
            .thenByDescending { it.startTime }
    )
}

private fun categorySortIndex(category: String): Int = when (category) {
    "Activity" -> 0
    "Body" -> 1
    "Vitals" -> 2
    "Nutrition" -> 3
    "Sleep" -> 4
    "Wellness" -> 5
    "Cycle" -> 6
    "Medical" -> 7
    else -> 8
}

private val todayMetricDefinitions = listOf(
    TodayMetricDefinition("ActiveCaloriesBurnedRecord", "Activity", "Active calories", "kcal"),
    TodayMetricDefinition("CyclingPedalingCadenceRecord", "Activity", "Cycling cadence", "rpm"),
    TodayMetricDefinition("DistanceRecord", "Activity", "Distance", "km"),
    TodayMetricDefinition("ElevationGainedRecord", "Activity", "Elevation gained", "m"),
    TodayMetricDefinition("ExerciseSessionRecord", "Activity", "Exercise session", "min"),
    TodayMetricDefinition("FloorsClimbedRecord", "Activity", "Floors climbed", "floors"),
    TodayMetricDefinition("PlannedExerciseSessionRecord", "Activity", "Planned exercise", "min"),
    TodayMetricDefinition("PowerRecord", "Activity", "Power", "W"),
    TodayMetricDefinition("SpeedRecord", "Activity", "Speed", "km/h"),
    TodayMetricDefinition("StepsCadenceRecord", "Activity", "Steps cadence", "steps/min"),
    TodayMetricDefinition("StepsRecord", "Activity", "Steps", "steps"),
    TodayMetricDefinition("TotalCaloriesBurnedRecord", "Activity", "Total calories", "kcal"),
    TodayMetricDefinition("Vo2MaxRecord", "Activity", "VO2 max", "mL/kg/min"),
    TodayMetricDefinition("WheelchairPushesRecord", "Activity", "Wheelchair pushes", "pushes"),
    TodayMetricDefinition("BasalMetabolicRateRecord", "Body", "Basal metabolic rate", "kcal/day"),
    TodayMetricDefinition("BodyFatRecord", "Body", "Body fat", "%"),
    TodayMetricDefinition("BodyWaterMassRecord", "Body", "Body water mass", "kg"),
    TodayMetricDefinition("BoneMassRecord", "Body", "Bone mass", "kg"),
    TodayMetricDefinition("HeightRecord", "Body", "Height", "cm"),
    TodayMetricDefinition("LeanBodyMassRecord", "Body", "Lean body mass", "kg"),
    TodayMetricDefinition("WeightRecord", "Body", "Weight", "kg"),
    TodayMetricDefinition("BmiRecord", "Body", "BMI"),
    TodayMetricDefinition("BasalBodyTemperatureRecord", "Vitals", "Basal body temperature", "deg C"),
    TodayMetricDefinition("BloodGlucoseRecord", "Vitals", "Blood glucose", "mmol/L"),
    TodayMetricDefinition("BloodPressureRecord", "Vitals", "Blood pressure", "mmHg"),
    TodayMetricDefinition("BodyTemperatureRecord", "Vitals", "Body temperature", "deg C"),
    TodayMetricDefinition("HeartRateRecord", "Vitals", "Heart rate", "bpm"),
    TodayMetricDefinition("HeartRateVariabilityRmssdRecord", "Vitals", "Heart rate variability", "ms"),
    TodayMetricDefinition("OxygenSaturationRecord", "Vitals", "Oxygen saturation", "%"),
    TodayMetricDefinition("RespiratoryRateRecord", "Vitals", "Respiratory rate", "breaths/min"),
    TodayMetricDefinition("RestingHeartRateRecord", "Vitals", "Resting heart rate", "bpm"),
    TodayMetricDefinition("SkinTemperatureRecord", "Vitals", "Skin temperature", "deg C delta"),
    TodayMetricDefinition("HydrationRecord", "Nutrition", "Hydration", "L"),
    TodayMetricDefinition("NutritionRecord", "Nutrition", "Nutrition", "kcal"),
    TodayMetricDefinition("SleepSessionRecord", "Sleep", "Sleep session", "min"),
    TodayMetricDefinition("MindfulnessSessionRecord", "Wellness", "Mindfulness session", "min"),
    TodayMetricDefinition("CervicalMucusRecord", "Cycle", "Cervical mucus"),
    TodayMetricDefinition("IntermenstrualBleedingRecord", "Cycle", "Intermenstrual bleeding"),
    TodayMetricDefinition("MenstruationFlowRecord", "Cycle", "Menstruation flow"),
    TodayMetricDefinition("MenstruationPeriodRecord", "Cycle", "Menstruation period", "min"),
    TodayMetricDefinition("OvulationTestRecord", "Cycle", "Ovulation test"),
    TodayMetricDefinition("SexualActivityRecord", "Cycle", "Sexual activity"),
    TodayMetricDefinition("MedicalResource", "Medical", "Medical resources"),
)
