package com.trackme.wearable.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        runCatching {
            HealthConnectClient.getOrCreate(context.applicationContext)
        }.getOrNull()
    }

    /** Returns true if Health Connect is available on this device. */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /**
     * Writes a completed workout session to Health Connect.
     * Called after WORKOUT_COMPLETE.
     */
    suspend fun writeWorkoutSession(
        startEpochMs: Long,
        endEpochMs: Long,
        sessionTitle: String,
        heartRateSamples: List<Pair<Long, Int>>,   // epochMs to bpm
        totalCaloriesKcal: Double?,
        activeCaloriesKcal: Double? = null,
        steps: Double? = null,
    ) {
        val hc = client ?: return
        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(Instant.now())
        val startInstant = Instant.ofEpochMilli(startEpochMs)
        val endInstant = Instant.ofEpochMilli(endEpochMs)
        val watchDevice = Device(type = Device.TYPE_WATCH)

        val records = buildList {
            add(
                ExerciseSessionRecord(
                    startTime = startInstant,
                    startZoneOffset = zoneOffset,
                    endTime = endInstant,
                    endZoneOffset = zoneOffset,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
                    title = sessionTitle,
                    metadata = Metadata.autoRecorded(device = watchDevice),
                )
            )
            if (heartRateSamples.isNotEmpty()) {
                add(
                    HeartRateRecord(
                        startTime = startInstant,
                        startZoneOffset = zoneOffset,
                        endTime = endInstant,
                        endZoneOffset = zoneOffset,
                        samples = heartRateSamples.map { (epochMs, bpm) ->
                            HeartRateRecord.Sample(
                                time = Instant.ofEpochMilli(epochMs),
                                beatsPerMinute = bpm.toLong(),
                            )
                        },
                        metadata = Metadata.autoRecorded(device = watchDevice),
                    )
                )
            }
            totalCaloriesKcal?.let { kcal ->
                add(
                    TotalCaloriesBurnedRecord(
                        startTime = startInstant,
                        startZoneOffset = zoneOffset,
                        endTime = endInstant,
                        endZoneOffset = zoneOffset,
                        energy = Energy.kilocalories(kcal),
                        metadata = Metadata.autoRecorded(device = watchDevice),
                    )
                )
            }
            activeCaloriesKcal?.let { kcal ->
                add(
                    ActiveCaloriesBurnedRecord(
                        startTime = startInstant,
                        startZoneOffset = zoneOffset,
                        endTime = endInstant,
                        endZoneOffset = zoneOffset,
                        energy = Energy.kilocalories(kcal),
                        metadata = Metadata.autoRecorded(device = watchDevice),
                    )
                )
            }
            steps?.let { s ->
                add(
                    StepsRecord(
                        startTime = startInstant,
                        startZoneOffset = zoneOffset,
                        endTime = endInstant,
                        endZoneOffset = zoneOffset,
                        count = s.toLong().coerceAtLeast(0L),
                        metadata = Metadata.autoRecorded(device = watchDevice),
                    )
                )
            }
        }

        runCatching {
            hc.insertRecords(records)
            Log.d("HealthConnect", "Wrote ${records.size} records for session")
        }.onFailure {
            Log.w("HealthConnect", "Failed to write Health Connect records", it)
        }
    }
}
