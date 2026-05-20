package com.trackme.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.trackme.data.local.entity.HealthMetricEntity
import com.trackme.data.local.entity.HealthSnapshotEntity
import com.trackme.domain.model.HcSdkStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.reflect.KClass

/**
 * Data-layer adapter around Android Health Connect.
 *
 * Architecture Layer: Data
 *
 * Responsibilities:
 * - Translate Health Connect SDK records into Room entities.
 * - Hide optional feature checks and permission details from ViewModels.
 * - Fail softly when a record type is unavailable so sync can continue.
 */
@Singleton
@OptIn(ExperimentalMindfulnessSessionApi::class, ExperimentalPersonalHealthRecordApi::class)
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }

    val requiredPermissions: Set<String>
        get() = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )

    fun getSdkStatus(): HcSdkStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcSdkStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcSdkStatus.NEEDS_UPDATE
        else -> HcSdkStatus.NEEDS_INSTALL
    }

    fun isAvailable(): Boolean = getSdkStatus() == HcSdkStatus.AVAILABLE

    suspend fun hasPermissions(): Boolean {
        val c = client ?: return false
        return c.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }

    suspend fun readLast30Days(userId: String, days: Int = 30): List<HealthSnapshotEntity> {
        val c = client ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        
        val snapshots = coroutineScope {
            (0..days).map { i ->
                async {
                    val day = today.minusDays(i.toLong())
                    val startOfDay = day.atStartOfDay(zone).toInstant()
                    val endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant()
                    val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)

                    val steps = safeAggregate {
                        c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), timeRange))[StepsRecord.COUNT_TOTAL]
                    }

                    val activeCalories = safeAggregate {
                        c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), timeRange))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    }

                    val heartRateAvg = safeAggregate {
                        c.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), timeRange))[HeartRateRecord.BPM_AVG]?.toInt()
                    }

                    val restingHeartRate = safeFetch {
                        val records = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)).records
                        if (records.isNotEmpty()) {
                            records.map { it.beatsPerMinute }.average().toInt()
                        } else null
                    }

                    val sleepDuration = safeFetch {
                        val records = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange)).records
                        if (records.isNotEmpty()) {
                            records.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                        } else null
                    }

                    val hrv = safeFetch {
                        val records = c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)).records
                        if (records.isNotEmpty()) {
                            records.map { it.heartRateVariabilityMillis }.average().toFloat()
                        } else null
                    }

                    val weight = safeFetch {
                        c.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.before(endOfDay), ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.weight?.inKilograms
                    }

                    val height = safeFetch {
                        c.readRecords(ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.before(endOfDay), ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.height?.inMeters
                    }

                    val bmi = if (weight != null && height != null && height > 0) {
                        weight / (height * height)
                    } else null

                    HealthSnapshotEntity(
                        id = "${userId}_${day}",
                        userId = userId,
                        date = startOfDay.toEpochMilli(),
                        weightKg = weight?.toFloat(),
                        heightCm = height?.let { (it * 100.0).toFloat() },
                        bmi = bmi?.toFloat(),
                        steps = steps,
                        activeCaloriesBurned = activeCalories?.toFloat(),
                        heartRateAvg = heartRateAvg,
                        hrvRmssd = hrv,
                        restingHeartRate = restingHeartRate,
                        sleepDurationMinutes = sleepDuration,
                        deepSleepMinutes = null,
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false,
                    )
                }
            }.awaitAll()
        }
        
        return snapshots
    }

    suspend fun readDate(userId: String, date: java.time.LocalDate): HealthSnapshotEntity? {
        val c = client ?: return null
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)

        val steps = safeAggregate {
            c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), timeRange))[StepsRecord.COUNT_TOTAL]
        }

        val activeCalories = safeAggregate {
            c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), timeRange))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        }

        val heartRateAvg = safeAggregate {
            c.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), timeRange))[HeartRateRecord.BPM_AVG]?.toInt()
        }

        val restingHeartRate = safeFetch {
            val records = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)).records
            if (records.isNotEmpty()) {
                records.map { it.beatsPerMinute }.average().toInt()
            } else null
        }

        val sleepDuration = safeFetch {
            val records = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange)).records
            if (records.isNotEmpty()) {
                records.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
            } else null
        }

        val hrv = safeFetch {
            val records = c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)).records
            if (records.isNotEmpty()) {
                records.map { it.heartRateVariabilityMillis }.average().toFloat()
            } else null
        }

        val weight = safeFetch {
            c.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.before(endOfDay), ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.weight?.inKilograms
        }

        val height = safeFetch {
            c.readRecords(ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.before(endOfDay), ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.height?.inMeters
        }

        val bmi = if (weight != null && height != null && height > 0) {
            weight / (height * height)
        } else null

        return HealthSnapshotEntity(
            id = "${userId}_${date}",
            userId = userId,
            date = startOfDay.toEpochMilli(),
            weightKg = weight?.toFloat(),
            heightCm = height?.let { (it * 100.0).toFloat() },
            bmi = bmi?.toFloat(),
            steps = steps,
            activeCaloriesBurned = activeCalories?.toFloat(),
            heartRateAvg = heartRateAvg,
            hrvRmssd = hrv,
            restingHeartRate = restingHeartRate,
            sleepDurationMinutes = sleepDuration,
            deepSleepMinutes = null,
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
        )
    }

    suspend fun readDetailedMetricsForDate(userId: String, date: java.time.LocalDate): List<HealthMetricEntity> {
        val c = client ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(start, end)
        val updatedAt = System.currentTimeMillis()

        val healthMetrics = mutableListOf<HealthMetricEntity>()
        
        availableRecordTypes().forEach { recordType ->
            runCatching {
                val records = c.readRecordsSafely(recordType, timeRange)
                healthMetrics += records.mapNotNull { record -> 
                    HealthMetricMapper.toEntity(userId, record, updatedAt) 
                }
            }
        }

        return healthMetrics
    }

    suspend fun readDetailedMetricsLast30Days(userId: String, days: Int = 30): List<HealthMetricEntity> {
        val c = client ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val today = Instant.now().atZone(zone).toLocalDate()
        val start = today.minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(start, end)
        val updatedAt = System.currentTimeMillis()

        val healthMetrics = mutableListOf<HealthMetricEntity>()
        
        availableRecordTypes().forEach { recordType ->
            runCatching {
                val records = c.readRecordsSafely(recordType, timeRange)
                healthMetrics += records.mapNotNull { record -> 
                    HealthMetricMapper.toEntity(userId, record, updatedAt) 
                }
            }
        }

        val medicalResources = runCatching { readMedicalResourcesSafely(c, userId, updatedAt) }.getOrElse { emptyList() }
        
        return healthMetrics + medicalResources
    }

    private suspend fun <T> safeAggregate(block: suspend () -> T): T? = runCatching { block() }.getOrNull()
    private suspend fun <T> safeFetch(block: suspend () -> T): T? = runCatching { block() }.getOrNull()

    private suspend fun HealthConnectClient.readRecordsSafely(
        recordType: KClass<out Record>,
        timeRange: TimeRangeFilter,
    ): List<Record> = runCatching {
        val records = mutableListOf<Record>()
        var pageToken: String? = null
        do {
            val response = readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = timeRange,
                    pageSize = 1000,
                    pageToken = pageToken,
                )
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        records
    }.getOrElse { 
        // Log error if needed, but return empty list as fallback
        emptyList()
    }

    private suspend fun readMedicalResourcesSafely(
        c: HealthConnectClient,
        userId: String,
        updatedAt: Long,
    ): List<HealthMetricEntity> {
        if (!c.isFeatureAvailable(HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD)) {
            return emptyList()
        }
        val resourceTypes = listOf(
            MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
            MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS,
            MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS,
            MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS,
            MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS,
            MedicalResource.MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS,
            MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY,
            MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES,
            MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY,
            MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES,
            MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS,
            MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS,
        )
        return resourceTypes.flatMap { type ->
            runCatching {
                val metrics = mutableListOf<HealthMetricEntity>()
                var response = c.readMedicalResources(
                    ReadMedicalResourcesInitialRequest(
                        medicalResourceType = type,
                        medicalDataSourceIds = emptySet(),
                    )
                )
                while (true) {
                    metrics += response.medicalResources.map {
                        HealthMetricMapper.medicalToEntity(userId, it, updatedAt)
                    }
                    val token = response.nextPageToken ?: break
                    response = c.readMedicalResources(ReadMedicalResourcesPageRequest(token))
                }
                metrics
            }.getOrElse { emptyList() }
        }
    }

    private fun availableRecordTypes(): List<KClass<out Record>> {
        val c = client
        return buildList {
            addAll(alwaysAvailableRecordTypes)
            if (c?.isFeatureAvailable(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) == true) {
                add(SkinTemperatureRecord::class)
            }
            if (c?.isFeatureAvailable(HealthConnectFeatures.FEATURE_PLANNED_EXERCISE) == true) {
                add(PlannedExerciseSessionRecord::class)
            }
            if (c?.isFeatureAvailable(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) == true) {
                add(MindfulnessSessionRecord::class)
            }
        }
    }

    private fun availableMedicalReadPermissions(): Set<String> {
        val c = client ?: return emptySet()
        if (!c.isFeatureAvailable(HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD)) {
            return emptySet()
        }
        return setOf(
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_CONDITIONS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_LABORATORY_RESULTS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_MEDICATIONS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_PERSONAL_DETAILS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_PRACTITIONER_DETAILS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_PREGNANCY,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_PROCEDURES,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_SOCIAL_HISTORY,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_VACCINES,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_VISITS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_VITAL_SIGNS,
        )
    }

    private fun HealthConnectClient.isFeatureAvailable(feature: Int): Boolean =
        features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    private companion object {
        val alwaysAvailableRecordTypes = listOf<KClass<out Record>>(
            ActiveCaloriesBurnedRecord::class,
            BasalBodyTemperatureRecord::class,
            BasalMetabolicRateRecord::class,
            BloodGlucoseRecord::class,
            BloodPressureRecord::class,
            BodyFatRecord::class,
            BodyTemperatureRecord::class,
            BodyWaterMassRecord::class,
            BoneMassRecord::class,
            CervicalMucusRecord::class,
            CyclingPedalingCadenceRecord::class,
            DistanceRecord::class,
            ElevationGainedRecord::class,
            ExerciseSessionRecord::class,
            FloorsClimbedRecord::class,
            HeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            HeightRecord::class,
            HydrationRecord::class,
            IntermenstrualBleedingRecord::class,
            LeanBodyMassRecord::class,
            MenstruationFlowRecord::class,
            MenstruationPeriodRecord::class,
            NutritionRecord::class,
            OvulationTestRecord::class,
            OxygenSaturationRecord::class,
            PowerRecord::class,
            RespiratoryRateRecord::class,
            RestingHeartRateRecord::class,
            SexualActivityRecord::class,
            SleepSessionRecord::class,
            SpeedRecord::class,
            StepsCadenceRecord::class,
            StepsRecord::class,
            TotalCaloriesBurnedRecord::class,
            Vo2MaxRecord::class,
            WeightRecord::class,
            WheelchairPushesRecord::class,
        )
    }
}
