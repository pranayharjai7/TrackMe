package com.trackme.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.trackme.data.local.entity.HealthSnapshotEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean {
        val c = client ?: return false
        return c.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }

    suspend fun readLast30Days(userId: String): List<HealthSnapshotEntity> {
        val c = client ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(30, ChronoUnit.DAYS)
        val timeRange = TimeRangeFilter.between(start, end)

        val weights = c.readRecords(ReadRecordsRequest(WeightRecord::class, timeRange))
            .records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, records) -> records.maxByOrNull { it.time }?.weight?.inKilograms?.toFloat() }

        val latestHeight = c.readRecords(ReadRecordsRequest(HeightRecord::class, timeRange))
            .records.maxByOrNull { it.time }?.height?.inMeters?.times(100)?.toFloat()

        val stepsByDay = c.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange))
            .records.groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, records) -> records.sumOf { it.count } }

        val caloriesByDay = c.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange))
            .records.groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, records) -> records.sumOf { it.energy.inKilocalories }.toFloat() }

        val allDays = (weights.keys + stepsByDay.keys + caloriesByDay.keys).toSet()
        return allDays.map { day ->
            val weightKg = weights[day]
            val bmi = if (weightKg != null && latestHeight != null && latestHeight > 0) {
                val heightM = latestHeight / 100f
                weightKg / (heightM * heightM)
            } else null

            HealthSnapshotEntity(
                id = "${userId}_${day}",
                userId = userId,
                date = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                weightKg = weightKg,
                heightCm = latestHeight,
                bmi = bmi,
                steps = stepsByDay[day],
                activeCaloriesBurned = caloriesByDay[day],
                updatedAt = System.currentTimeMillis(),
                isSynced = false,
            )
        }
    }
}
