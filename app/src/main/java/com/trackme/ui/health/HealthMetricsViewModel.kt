package com.trackme.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.HealthMetric
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.repository.HealthRepository
import com.trackme.domain.usecase.GetHealthMetricsUseCase
import com.trackme.utils.startOfTodayMillis
import com.trackme.utils.todayBoundsMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthMetricsUiState(
    val isLoading: Boolean = true,
    val metrics: List<HealthMetric> = emptyList(),
)

/**
 * ViewModel responsible for today's Health Connect metric list.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Trigger a best-effort Health Connect sync for the current user.
 * - Merge detailed HealthMetric rows with daily HealthSnapshot totals.
 * - Emit UI-ready health metrics without exposing Room entities to the UI.
 */
@HiltViewModel
class HealthMetricsViewModel @Inject constructor(
    private val getHealthMetrics: GetHealthMetricsUseCase,
    private val healthRepository: HealthRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthMetricsUiState())
    val uiState: StateFlow<HealthMetricsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            runCatching { healthRepository.syncFromHealthConnect(userId) }
            val todayStart = startOfTodayMillis()
            combine(
                getHealthMetrics(userId),
                healthRepository.getSnapshots(userId, todayStart),
            ) { metrics, snapshots ->
                val todayMetrics = metrics.filter { it.overlapsToday() }
                todayMetrics + snapshots.flatMap { it.toTodayMetrics() }
            }.collect { metrics ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        metrics = metrics.distinctBy { it.recordType to it.id },
                    )
                }
            }
        }
    }

    private fun HealthMetric.overlapsToday(): Boolean {
        val todayBounds = todayBoundsMillis()
        val metricEnd = endTime ?: startTime
        return startTime < todayBounds.endExclusiveMillis && metricEnd >= todayBounds.startMillis
    }

    /**
     * Converts the daily snapshot row into individual display metrics.
     *
     * Side effects: none. The generated IDs are deterministic derivatives of the
     * snapshot ID so Compose list diffing and duplicate filtering remain stable.
     */
    private fun HealthSnapshot.toTodayMetrics(): List<HealthMetric> {
        val todayStart = startOfTodayMillis()
        if (date != todayStart) return emptyList()
        return listOfNotNull(
            steps?.let {
                HealthMetric(
                    id = "${id}_steps_snapshot",
                    userId = userId,
                    category = "Activity",
                    recordType = "StepsRecord",
                    displayName = "Steps",
                    startTime = date,
                    endTime = null,
                    primaryValue = "%,d".format(it),
                    primaryUnit = "steps",
                    details = listOf("Total today: %,d steps".format(it)),
                    sourceApp = "Health Connect daily total",
                    rawData = null,
                    updatedAt = updatedAt,
                )
            },
            heartRateAvg?.let {
                HealthMetric(
                    id = "${id}_hr_snapshot",
                    userId = userId,
                    recordType = "HeartRateRecord",
                    category = "Vitals",
                    displayName = "Heart rate (Daily Avg)",
                    startTime = date,
                    endTime = null,
                    primaryValue = it.toString(),
                    primaryUnit = "bpm",
                    details = emptyList(),
                    sourceApp = "Health Connect daily snapshot",
                    rawData = null,
                    updatedAt = updatedAt,
                )
            },
            activeCaloriesBurned?.let {
                HealthMetric(
                    id = "${id}_active_calories_snapshot",
                    userId = userId,
                    category = "Activity",
                    recordType = "ActiveCaloriesBurnedRecord",
                    displayName = "Active calories",
                    startTime = date,
                    endTime = null,
                    primaryValue = "%.0f".format(it),
                    primaryUnit = "kcal",
                    details = listOf("Total today: %.0f kcal".format(it)),
                    sourceApp = "Health Connect daily total",
                    rawData = null,
                    updatedAt = updatedAt,
                )
            },
            weightKg?.let {
                HealthMetric(
                    id = "${id}_weight_snapshot",
                    userId = userId,
                    category = "Body",
                    recordType = "WeightRecord",
                    displayName = "Weight",
                    startTime = date,
                    endTime = null,
                    primaryValue = "%.1f".format(it),
                    primaryUnit = "kg",
                    details = listOf("Latest today: %.1f kg".format(it)),
                    sourceApp = "Health Connect daily snapshot",
                    rawData = null,
                    updatedAt = updatedAt,
                )
            },
            heightCm?.let {
                HealthMetric(
                    id = "${id}_height_snapshot",
                    userId = userId,
                    category = "Body",
                    recordType = "HeightRecord",
                    displayName = "Height",
                    startTime = date,
                    endTime = null,
                    primaryValue = "%.1f".format(it),
                    primaryUnit = "cm",
                    details = listOf("Latest height: %.1f cm".format(it)),
                    sourceApp = "Health Connect daily snapshot",
                    rawData = null,
                    updatedAt = updatedAt,
                )
            },
            bmi?.let {
                HealthMetric(
                    id = "${id}_bmi_snapshot",
                    userId = userId,
                    category = "Body",
                    recordType = "BmiRecord",
                    displayName = "BMI",
                    startTime = date,
                    endTime = null,
                    primaryValue = "%.1f".format(it),
                    primaryUnit = null,
                    details = listOf("Calculated from Health Connect height and weight"),
                    sourceApp = "TrackMe calculation",
                    rawData = null,
                    updatedAt = updatedAt,
                )
            },
        )
    }
}
