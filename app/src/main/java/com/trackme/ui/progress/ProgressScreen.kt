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
import com.trackme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

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
private fun BodyStatsCard(snapshot: HealthSnapshot) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Body Stats",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
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
            Text(
                "Weight (30 days)",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Chart(
                chart = lineChart(),
                chartModelProducer = producer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
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
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pr.exerciseId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
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
