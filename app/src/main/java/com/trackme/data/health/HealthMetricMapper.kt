package com.trackme.data.health

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.*
import com.trackme.data.local.entity.HealthMetricEntity
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToLong

private data class MetricValue(val value: String, val unit: String? = null) {
    fun asText(): String = if (unit.isNullOrBlank()) value else "$value $unit"
}

/**
 * Maps raw Health Connect records into the app's normalized health metric rows.
 *
 * Architecture Layer: Data mapper
 *
 * Responsibilities:
 * - Preserve Health Connect record details for detailed UI display.
 * - Generate stable Room IDs from record metadata or deterministic record content.
 * - Keep every supported record type in one mapper so SDK upgrades are auditable.
 */
@OptIn(ExperimentalMindfulnessSessionApi::class, ExperimentalPersonalHealthRecordApi::class)
object HealthMetricMapper {
    /**
     * Converts a single Health Connect Record into a HealthMetricEntity.
     *
     * Inputs:
     * - userId: Supabase user ID that owns the metric.
     * - record: Health Connect SDK record read from the device.
     * - updatedAt: sync timestamp applied consistently across a sync batch.
     *
     * Output:
     * - A normalized entity, or null when the record type is intentionally unsupported.
     */
    fun toEntity(userId: String, record: Record, updatedAt: Long): HealthMetricEntity? =
        when (record) {
            is ActiveCaloriesBurnedRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Active calories",
                value = record.energy.kcal(),
                details = listOf("Energy: ${record.energy.kcal().asText()}"),
                updatedAt = updatedAt,
            )
            is BasalBodyTemperatureRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Basal body temperature",
                value = record.temperature.celsius(),
                details = listOf(
                    "Temperature: ${record.temperature.celsius().asText()}",
                    "Measurement location: ${temperatureLocationLabel(record.measurementLocation)}",
                ),
                updatedAt = updatedAt,
            )
            is BasalMetabolicRateRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Basal metabolic rate",
                value = record.basalMetabolicRate.kcalPerDay(),
                details = listOf("Rate: ${record.basalMetabolicRate.kcalPerDay().asText()}"),
                updatedAt = updatedAt,
            )
            is BloodGlucoseRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Blood glucose",
                value = record.level.mmolPerL(),
                details = listOf(
                    "Level: ${record.level.mmolPerL().asText()}",
                    "Level: ${record.level.mgPerDl().asText()}",
                    "Specimen source: ${enumLabel(BloodGlucoseRecord.SPECIMEN_SOURCE_INT_TO_STRING_MAP[record.specimenSource])}",
                    "Meal type: ${mealLabel(record.mealType)}",
                    "Relation to meal: ${enumLabel(BloodGlucoseRecord.RELATION_TO_MEAL_INT_TO_STRING_MAP[record.relationToMeal])}",
                ),
                updatedAt = updatedAt,
            )
            is BloodPressureRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Blood pressure",
                value = MetricValue(
                    "${record.systolic.inMillimetersOfMercury.display(0)}/${record.diastolic.inMillimetersOfMercury.display(0)}",
                    "mmHg",
                ),
                details = listOf(
                    "Systolic: ${record.systolic.mmHg().asText()}",
                    "Diastolic: ${record.diastolic.mmHg().asText()}",
                    "Body position: ${enumLabel(BloodPressureRecord.BODY_POSITION_INT_TO_STRING_MAP[record.bodyPosition])}",
                    "Measurement location: ${enumLabel(BloodPressureRecord.MEASUREMENT_LOCATION_INT_TO_STRING_MAP[record.measurementLocation])}",
                ),
                updatedAt = updatedAt,
            )
            is BodyFatRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Body fat",
                value = record.percentage.percent(),
                details = listOf("Body fat: ${record.percentage.percent().asText()}"),
                updatedAt = updatedAt,
            )
            is BodyTemperatureRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Body temperature",
                value = record.temperature.celsius(),
                details = listOf(
                    "Temperature: ${record.temperature.celsius().asText()}",
                    "Measurement location: ${temperatureLocationLabel(record.measurementLocation)}",
                ),
                updatedAt = updatedAt,
            )
            is BodyWaterMassRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Body water mass",
                value = record.mass.kg(),
                details = listOf("Mass: ${record.mass.kg().asText()}"),
                updatedAt = updatedAt,
            )
            is BoneMassRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Bone mass",
                value = record.mass.kg(),
                details = listOf("Mass: ${record.mass.kg().asText()}"),
                updatedAt = updatedAt,
            )
            is CervicalMucusRecord -> record.entity(
                userId = userId,
                category = "Cycle",
                displayName = "Cervical mucus",
                value = MetricValue(enumLabel(CervicalMucusRecord.APPEARANCE_INT_TO_STRING_MAP[record.appearance])),
                details = listOf(
                    "Appearance: ${enumLabel(CervicalMucusRecord.APPEARANCE_INT_TO_STRING_MAP[record.appearance])}",
                    "Sensation: ${enumLabel(CervicalMucusRecord.SENSATION_INT_TO_STRING_MAP[record.sensation])}",
                ),
                updatedAt = updatedAt,
            )
            is CyclingPedalingCadenceRecord -> record.seriesEntity(
                userId = userId,
                category = "Activity",
                displayName = "Cycling cadence",
                sampleValues = record.samples.map { it.revolutionsPerMinute },
                unit = "rpm",
                sampleLabel = "Samples",
                updatedAt = updatedAt,
            )
            is DistanceRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Distance",
                value = record.distance.km(),
                details = listOf(
                    "Distance: ${record.distance.km().asText()}",
                    "Distance: ${record.distance.meters().asText()}",
                ),
                updatedAt = updatedAt,
            )
            is ElevationGainedRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Elevation gained",
                value = record.elevation.meters(),
                details = listOf("Elevation: ${record.elevation.meters().asText()}"),
                updatedAt = updatedAt,
            )
            is ExerciseSessionRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = record.title?.ifBlank { null } ?: "Exercise session",
                value = durationValue(record.startTime, record.endTime),
                details = listOfNotNull(
                    "Exercise type: ${enumLabel(ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[record.exerciseType])}",
                    "Duration: ${durationText(record.startTime, record.endTime)}",
                    "Segments: ${record.segments.size}",
                    "Laps: ${record.laps.size}",
                    record.notes?.takeIf { it.isNotBlank() }?.let { "Notes: $it" },
                    record.plannedExerciseSessionId?.let { "Planned session ID: $it" },
                    routeDetail(record.exerciseRouteResult),
                ),
                updatedAt = updatedAt,
            )
            is FloorsClimbedRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Floors climbed",
                value = MetricValue(record.floors.display(1), "floors"),
                details = listOf("Floors: ${record.floors.display(1)}"),
                updatedAt = updatedAt,
            )
            is HeartRateRecord -> record.seriesEntity(
                userId = userId,
                category = "Vitals",
                displayName = "Heart rate",
                sampleValues = record.samples.map { it.beatsPerMinute.toDouble() },
                unit = "bpm",
                sampleLabel = "Measurements",
                updatedAt = updatedAt,
            )
            is HeartRateVariabilityRmssdRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Heart rate variability",
                value = MetricValue(record.heartRateVariabilityMillis.display(1), "ms"),
                details = listOf("RMSSD: ${record.heartRateVariabilityMillis.display(1)} ms"),
                updatedAt = updatedAt,
            )
            is HeightRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Height",
                value = MetricValue((record.height.inMeters * 100.0).display(1), "cm"),
                details = listOf(
                    "Height: ${(record.height.inMeters * 100.0).display(1)} cm",
                    "Height: ${record.height.meters().asText()}",
                ),
                updatedAt = updatedAt,
            )
            is HydrationRecord -> record.entity(
                userId = userId,
                category = "Nutrition",
                displayName = "Hydration",
                value = record.volume.liters(),
                details = listOf(
                    "Volume: ${record.volume.liters().asText()}",
                    "Volume: ${record.volume.milliliters().asText()}",
                ),
                updatedAt = updatedAt,
            )
            is IntermenstrualBleedingRecord -> record.entity(
                userId = userId,
                category = "Cycle",
                displayName = "Intermenstrual bleeding",
                value = MetricValue("Recorded"),
                details = listOf("Event: Recorded"),
                updatedAt = updatedAt,
            )
            is LeanBodyMassRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Lean body mass",
                value = record.mass.kg(),
                details = listOf("Mass: ${record.mass.kg().asText()}"),
                updatedAt = updatedAt,
            )
            is MenstruationFlowRecord -> record.entity(
                userId = userId,
                category = "Cycle",
                displayName = "Menstruation flow",
                value = MetricValue(enumLabel(MenstruationFlowRecord.FLOW_TYPE_INT_TO_STRING_MAP[record.flow])),
                details = listOf("Flow: ${enumLabel(MenstruationFlowRecord.FLOW_TYPE_INT_TO_STRING_MAP[record.flow])}"),
                updatedAt = updatedAt,
            )
            is MenstruationPeriodRecord -> record.entity(
                userId = userId,
                category = "Cycle",
                displayName = "Menstruation period",
                value = durationValue(record.startTime, record.endTime),
                details = listOf("Duration: ${durationText(record.startTime, record.endTime)}"),
                updatedAt = updatedAt,
            )
            is MindfulnessSessionRecord -> record.entity(
                userId = userId,
                category = "Wellness",
                displayName = record.title?.ifBlank { null } ?: "Mindfulness session",
                value = durationValue(record.startTime, record.endTime),
                details = listOfNotNull(
                    "Type: ${enumLabel(MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_INT_TO_STRING_MAP[record.mindfulnessSessionType])}",
                    "Duration: ${durationText(record.startTime, record.endTime)}",
                    record.notes?.takeIf { it.isNotBlank() }?.let { "Notes: $it" },
                ),
                updatedAt = updatedAt,
            )
            is NutritionRecord -> record.nutritionEntity(userId, updatedAt)
            is OvulationTestRecord -> record.entity(
                userId = userId,
                category = "Cycle",
                displayName = "Ovulation test",
                value = MetricValue(enumLabel(OvulationTestRecord.RESULT_INT_TO_STRING_MAP[record.result])),
                details = listOf("Result: ${enumLabel(OvulationTestRecord.RESULT_INT_TO_STRING_MAP[record.result])}"),
                updatedAt = updatedAt,
            )
            is OxygenSaturationRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Oxygen saturation",
                value = record.percentage.percent(),
                details = listOf("SpO2: ${record.percentage.percent().asText()}"),
                updatedAt = updatedAt,
            )
            is PlannedExerciseSessionRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = record.title?.ifBlank { null } ?: "Planned exercise",
                value = durationValue(record.startTime, record.endTime),
                details = listOfNotNull(
                    "Exercise type: ${enumLabel(ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[record.exerciseType])}",
                    "Duration: ${durationText(record.startTime, record.endTime)}",
                    "Blocks: ${record.blocks.size}",
                    "Explicit time: ${if (record.hasExplicitTime) "Yes" else "No"}",
                    record.completedExerciseSessionId?.let { "Completed session ID: $it" },
                    record.notes?.takeIf { it.isNotBlank() }?.let { "Notes: $it" },
                ),
                updatedAt = updatedAt,
            )
            is PowerRecord -> record.seriesEntity(
                userId = userId,
                category = "Activity",
                displayName = "Power",
                sampleValues = record.samples.map { it.power.inWatts },
                unit = "W",
                sampleLabel = "Samples",
                updatedAt = updatedAt,
            )
            is RespiratoryRateRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Respiratory rate",
                value = MetricValue(record.rate.display(1), "breaths/min"),
                details = listOf("Rate: ${record.rate.display(1)} breaths/min"),
                updatedAt = updatedAt,
            )
            is RestingHeartRateRecord -> record.entity(
                userId = userId,
                category = "Vitals",
                displayName = "Resting heart rate",
                value = MetricValue(record.beatsPerMinute.toString(), "bpm"),
                details = listOf("Resting rate: ${record.beatsPerMinute} bpm"),
                updatedAt = updatedAt,
            )
            is SexualActivityRecord -> record.entity(
                userId = userId,
                category = "Cycle",
                displayName = "Sexual activity",
                value = MetricValue(enumLabel(SexualActivityRecord.PROTECTION_USED_INT_TO_STRING_MAP[record.protectionUsed])),
                details = listOf("Protection used: ${enumLabel(SexualActivityRecord.PROTECTION_USED_INT_TO_STRING_MAP[record.protectionUsed])}"),
                updatedAt = updatedAt,
            )
            is SkinTemperatureRecord -> record.skinTemperatureEntity(userId, updatedAt)
            is SleepSessionRecord -> record.sleepEntity(userId, updatedAt)
            is SpeedRecord -> record.seriesEntity(
                userId = userId,
                category = "Activity",
                displayName = "Speed",
                sampleValues = record.samples.map { it.speed.inKilometersPerHour },
                unit = "km/h",
                sampleLabel = "Samples",
                updatedAt = updatedAt,
            )
            is StepsCadenceRecord -> record.seriesEntity(
                userId = userId,
                category = "Activity",
                displayName = "Steps cadence",
                sampleValues = record.samples.map { it.rate },
                unit = "steps/min",
                sampleLabel = "Samples",
                updatedAt = updatedAt,
            )
            is StepsRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Steps",
                value = MetricValue(record.count.formatLong(), "steps"),
                details = listOf("Count: ${record.count.formatLong()} steps"),
                updatedAt = updatedAt,
            )
            is TotalCaloriesBurnedRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Total calories",
                value = record.energy.kcal(),
                details = listOf("Energy: ${record.energy.kcal().asText()}"),
                updatedAt = updatedAt,
            )
            is Vo2MaxRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "VO2 max",
                value = MetricValue(record.vo2MillilitersPerMinuteKilogram.display(1), "mL/kg/min"),
                details = listOf(
                    "VO2 max: ${record.vo2MillilitersPerMinuteKilogram.display(1)} mL/kg/min",
                    "Measurement method: ${enumLabel(Vo2MaxRecord.MEASUREMENT_METHOD_INT_TO_STRING_MAP[record.measurementMethod])}",
                ),
                updatedAt = updatedAt,
            )
            is WeightRecord -> record.entity(
                userId = userId,
                category = "Body",
                displayName = "Weight",
                value = record.weight.kg(),
                details = listOf("Weight: ${record.weight.kg().asText()}"),
                updatedAt = updatedAt,
            )
            is WheelchairPushesRecord -> record.entity(
                userId = userId,
                category = "Activity",
                displayName = "Wheelchair pushes",
                value = MetricValue(record.count.formatLong(), "pushes"),
                details = listOf("Count: ${record.count.formatLong()} pushes"),
                updatedAt = updatedAt,
            )
            else -> null
        }

    fun medicalToEntity(userId: String, resource: MedicalResource, updatedAt: Long): HealthMetricEntity {
        val medicalType = medicalResourceTypeLabel(resource.type)
        val fhirType = fhirTypeLabel(resource.fhirResource.type)
        val idSeed = listOf(
            userId,
            "medical",
            resource.type,
            resource.id.dataSourceId,
            resource.id.fhirResourceType,
            resource.id.fhirResourceId,
        ).joinToString(":")
        return HealthMetricEntity(
            id = stableId(idSeed),
            userId = userId,
            category = "Medical",
            recordType = "MedicalResource",
            displayName = medicalType,
            startTime = updatedAt,
            endTime = null,
            primaryValue = fhirType,
            primaryUnit = null,
            details = listOf(
                "Medical resource type: $medicalType",
                "FHIR type: $fhirType",
                "FHIR ID: ${resource.fhirResource.id}",
                "FHIR version: ${resource.fhirVersion}",
                "Data source ID: ${resource.dataSourceId}",
            ).joinToString("\n"),
            sourceApp = null,
            rawData = resource.fhirResource.data,
            updatedAt = updatedAt,
        )
    }

    private fun Record.nutritionEntity(userId: String, updatedAt: Long): HealthMetricEntity {
        val record = this as NutritionRecord
        val nutrientDetails = listOfNotNull(
            record.energy?.let { "Energy: ${it.kcal().asText()}" },
            record.energyFromFat?.let { "Energy from fat: ${it.kcal().asText()}" },
            record.totalFat?.let { "Total fat: ${it.grams().asText()}" },
            record.saturatedFat?.let { "Saturated fat: ${it.grams().asText()}" },
            record.unsaturatedFat?.let { "Unsaturated fat: ${it.grams().asText()}" },
            record.monounsaturatedFat?.let { "Monounsaturated fat: ${it.grams().asText()}" },
            record.polyunsaturatedFat?.let { "Polyunsaturated fat: ${it.grams().asText()}" },
            record.transFat?.let { "Trans fat: ${it.grams().asText()}" },
            record.cholesterol?.let { "Cholesterol: ${it.grams().asText()}" },
            record.totalCarbohydrate?.let { "Total carbohydrate: ${it.grams().asText()}" },
            record.dietaryFiber?.let { "Dietary fiber: ${it.grams().asText()}" },
            record.sugar?.let { "Sugar: ${it.grams().asText()}" },
            record.protein?.let { "Protein: ${it.grams().asText()}" },
            record.sodium?.let { "Sodium: ${it.grams().asText()}" },
            record.potassium?.let { "Potassium: ${it.grams().asText()}" },
            record.calcium?.let { "Calcium: ${it.grams().asText()}" },
            record.iron?.let { "Iron: ${it.grams().asText()}" },
            record.magnesium?.let { "Magnesium: ${it.grams().asText()}" },
            record.phosphorus?.let { "Phosphorus: ${it.grams().asText()}" },
            record.zinc?.let { "Zinc: ${it.grams().asText()}" },
            record.vitaminA?.let { "Vitamin A: ${it.grams().asText()}" },
            record.vitaminB6?.let { "Vitamin B6: ${it.grams().asText()}" },
            record.vitaminB12?.let { "Vitamin B12: ${it.grams().asText()}" },
            record.vitaminC?.let { "Vitamin C: ${it.grams().asText()}" },
            record.vitaminD?.let { "Vitamin D: ${it.grams().asText()}" },
            record.vitaminE?.let { "Vitamin E: ${it.grams().asText()}" },
            record.vitaminK?.let { "Vitamin K: ${it.grams().asText()}" },
            record.caffeine?.let { "Caffeine: ${it.grams().asText()}" },
            record.biotin?.let { "Biotin: ${it.grams().asText()}" },
            record.chloride?.let { "Chloride: ${it.grams().asText()}" },
            record.chromium?.let { "Chromium: ${it.grams().asText()}" },
            record.copper?.let { "Copper: ${it.grams().asText()}" },
            record.folate?.let { "Folate: ${it.grams().asText()}" },
            record.folicAcid?.let { "Folic acid: ${it.grams().asText()}" },
            record.iodine?.let { "Iodine: ${it.grams().asText()}" },
            record.manganese?.let { "Manganese: ${it.grams().asText()}" },
            record.molybdenum?.let { "Molybdenum: ${it.grams().asText()}" },
            record.niacin?.let { "Niacin: ${it.grams().asText()}" },
            record.pantothenicAcid?.let { "Pantothenic acid: ${it.grams().asText()}" },
            record.riboflavin?.let { "Riboflavin: ${it.grams().asText()}" },
            record.selenium?.let { "Selenium: ${it.grams().asText()}" },
            record.thiamin?.let { "Thiamin: ${it.grams().asText()}" },
        )
        val title = record.name?.ifBlank { null } ?: "Nutrition"
        val value = record.energy?.kcal()
            ?: record.protein?.grams()
            ?: record.totalCarbohydrate?.grams()
            ?: MetricValue(mealLabel(record.mealType))
        return record.entity(
            userId = userId,
            category = "Nutrition",
            displayName = title,
            value = value,
            details = listOf("Meal type: ${mealLabel(record.mealType)}") + nutrientDetails,
            updatedAt = updatedAt,
        )
    }

    private fun SkinTemperatureRecord.skinTemperatureEntity(userId: String, updatedAt: Long): HealthMetricEntity {
        val avgDelta = deltas.map { it.delta.inCelsius }.averageOrNull()
        val value = avgDelta?.let { MetricValue(it.display(2), "deg C delta") }
            ?: baseline?.celsius()
            ?: MetricValue("Recorded")
        return entity(
            userId = userId,
            category = "Vitals",
            displayName = "Skin temperature",
            value = value,
            details = listOfNotNull(
                baseline?.let { "Baseline: ${it.celsius().asText()}" },
                avgDelta?.let { "Average delta: ${it.display(2)} deg C" },
                deltas.map { it.delta.inCelsius }.minOrNull()?.let { "Minimum delta: ${it.display(2)} deg C" },
                deltas.map { it.delta.inCelsius }.maxOrNull()?.let { "Maximum delta: ${it.display(2)} deg C" },
                "Deltas: ${deltas.size}",
                "Measurement location: ${enumLabel(SkinTemperatureRecord.MEASUREMENT_LOCATION_INT_TO_STRING_MAP[measurementLocation])}",
            ),
            updatedAt = updatedAt,
        )
    }

    private fun SleepSessionRecord.sleepEntity(userId: String, updatedAt: Long): HealthMetricEntity {
        val stageDurations = stages.groupBy { it.stage }.mapValues { (_, values) ->
            values.fold(Duration.ZERO) { acc, stage -> acc + Duration.between(stage.startTime, stage.endTime) }
        }
        val stageDetails = stageDurations.map { (stage, duration) ->
            "${enumLabel(SleepSessionRecord.STAGE_TYPE_INT_TO_STRING_MAP[stage])}: ${duration.displayDuration()}"
        }
        return entity(
            userId = userId,
            category = "Sleep",
            displayName = title?.ifBlank { null } ?: "Sleep session",
            value = durationValue(startTime, endTime),
            details = listOfNotNull(
                "Duration: ${durationText(startTime, endTime)}",
                "Stages: ${stages.size}",
                notes?.takeIf { it.isNotBlank() }?.let { "Notes: $it" },
            ) + stageDetails,
            updatedAt = updatedAt,
        )
    }

    private fun Record.seriesEntity(
        userId: String,
        category: String,
        displayName: String,
        sampleValues: List<Double>,
        unit: String,
        sampleLabel: String,
        updatedAt: Long,
    ): HealthMetricEntity {
        val avg = sampleValues.averageOrNull()
        val decimals = seriesDecimals(unit)
        val value = avg?.let { MetricValue(it.display(decimals), unit) }
            ?: MetricValue("${sampleValues.size}", "samples")
        return entity(
            userId = userId,
            category = category,
            displayName = displayName,
            value = value,
            details = listOfNotNull(
                avg?.let { "Average: ${it.display(decimals)} $unit" },
                sampleValues.minOrNull()?.let { "Minimum: ${it.display(decimals)} $unit" },
                sampleValues.maxOrNull()?.let { "Maximum: ${it.display(decimals)} $unit" },
                "$sampleLabel: ${sampleValues.size}",
            ),
            updatedAt = updatedAt,
        )
    }

    private fun Record.entity(
        userId: String,
        category: String,
        displayName: String,
        value: MetricValue,
        details: List<String>,
        updatedAt: Long,
    ): HealthMetricEntity {
        val recordType = javaClass.simpleName
        val start = startInstant().toEpochMilli()
        val end = endInstant()?.toEpochMilli()
        val idSeed = metadata.id.takeIf { it.isNotBlank() }
            ?: listOf(userId, recordType, start, end, value.asText(), details.joinToString("|")).joinToString(":")
        return HealthMetricEntity(
            id = stableId("$userId:$recordType:$idSeed"),
            userId = userId,
            category = category,
            recordType = recordType,
            displayName = displayName,
            startTime = start,
            endTime = end,
            primaryValue = value.value,
            primaryUnit = value.unit,
            details = details.filter { it.isNotBlank() }.joinToString("\n"),
            sourceApp = metadata.sourceApp(),
            rawData = toString(),
            updatedAt = updatedAt,
        )
    }

    private fun Record.startInstant(): Instant = when (this) {
        is ActiveCaloriesBurnedRecord -> startTime
        is BasalBodyTemperatureRecord -> time
        is BasalMetabolicRateRecord -> time
        is BloodGlucoseRecord -> time
        is BloodPressureRecord -> time
        is BodyFatRecord -> time
        is BodyTemperatureRecord -> time
        is BodyWaterMassRecord -> time
        is BoneMassRecord -> time
        is CervicalMucusRecord -> time
        is CyclingPedalingCadenceRecord -> startTime
        is DistanceRecord -> startTime
        is ElevationGainedRecord -> startTime
        is ExerciseSessionRecord -> startTime
        is FloorsClimbedRecord -> startTime
        is HeartRateRecord -> startTime
        is HeartRateVariabilityRmssdRecord -> time
        is HeightRecord -> time
        is HydrationRecord -> startTime
        is IntermenstrualBleedingRecord -> time
        is LeanBodyMassRecord -> time
        is MenstruationFlowRecord -> time
        is MenstruationPeriodRecord -> startTime
        is MindfulnessSessionRecord -> startTime
        is NutritionRecord -> startTime
        is OvulationTestRecord -> time
        is OxygenSaturationRecord -> time
        is PlannedExerciseSessionRecord -> startTime
        is PowerRecord -> startTime
        is RespiratoryRateRecord -> time
        is RestingHeartRateRecord -> time
        is SexualActivityRecord -> time
        is SkinTemperatureRecord -> startTime
        is SleepSessionRecord -> startTime
        is SpeedRecord -> startTime
        is StepsCadenceRecord -> startTime
        is StepsRecord -> startTime
        is TotalCaloriesBurnedRecord -> startTime
        is Vo2MaxRecord -> time
        is WeightRecord -> time
        is WheelchairPushesRecord -> startTime
        else -> metadata.lastModifiedTime.takeIf { it != Instant.EPOCH } ?: Instant.ofEpochMilli(0)
    }

    private fun Record.endInstant(): Instant? = when (this) {
        is ActiveCaloriesBurnedRecord -> endTime
        is CyclingPedalingCadenceRecord -> endTime
        is DistanceRecord -> endTime
        is ElevationGainedRecord -> endTime
        is ExerciseSessionRecord -> endTime
        is FloorsClimbedRecord -> endTime
        is HeartRateRecord -> endTime
        is HydrationRecord -> endTime
        is MenstruationPeriodRecord -> endTime
        is MindfulnessSessionRecord -> endTime
        is NutritionRecord -> endTime
        is PlannedExerciseSessionRecord -> endTime
        is PowerRecord -> endTime
        is SkinTemperatureRecord -> endTime
        is SleepSessionRecord -> endTime
        is SpeedRecord -> endTime
        is StepsCadenceRecord -> endTime
        is StepsRecord -> endTime
        is TotalCaloriesBurnedRecord -> endTime
        is WheelchairPushesRecord -> endTime
        else -> null
    }

    private fun Metadata.sourceApp(): String? = dataOrigin.packageName.takeIf { it.isNotBlank() }

    private fun routeDetail(routeResult: ExerciseRouteResult): String? = when (routeResult) {
        is ExerciseRouteResult.Data -> "Route points: ${routeResult.exerciseRoute.route.size}"
        is ExerciseRouteResult.ConsentRequired -> "Route: Consent required"
        is ExerciseRouteResult.NoData -> null
        else -> null
    }

    private fun Mass.kg() = MetricValue(inKilograms.display(1), "kg")
    private fun Mass.grams() = MetricValue(inGrams.display(2), "g")
    private fun Length.km() = MetricValue(inKilometers.display(2), "km")
    private fun Length.meters() = MetricValue(inMeters.display(1), "m")
    private fun Energy.kcal() = MetricValue(inKilocalories.display(0), "kcal")
    private fun Volume.liters() = MetricValue(inLiters.display(2), "L")
    private fun Volume.milliliters() = MetricValue(inMilliliters.display(0), "mL")
    private fun Percentage.percent() = MetricValue(value.display(1), "%")
    private fun Temperature.celsius() = MetricValue(inCelsius.display(1), "deg C")
    private fun Pressure.mmHg() = MetricValue(inMillimetersOfMercury.display(0), "mmHg")
    private fun Power.kcalPerDay() = MetricValue(inKilocaloriesPerDay.display(0), "kcal/day")
    private fun BloodGlucose.mmolPerL() = MetricValue(inMillimolesPerLiter.display(1), "mmol/L")
    private fun BloodGlucose.mgPerDl() = MetricValue(inMilligramsPerDeciliter.display(0), "mg/dL")

    private fun durationValue(start: Instant, end: Instant): MetricValue {
        val duration = Duration.between(start, end).coerceAtLeast(Duration.ZERO)
        val minutes = duration.toMinutes()
        return if (minutes >= 90) {
            MetricValue((minutes / 60.0).display(1), "hr")
        } else {
            MetricValue(minutes.coerceAtLeast(0).toString(), "min")
        }
    }

    private fun durationText(start: Instant, end: Instant): String =
        Duration.between(start, end).coerceAtLeast(Duration.ZERO).displayDuration()

    private fun Duration.displayDuration(): String {
        val minutes = toMinutes().coerceAtLeast(0)
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (remainder == 0L) "$hours hr" else "$hours hr $remainder min"
    }

    private fun Duration.coerceAtLeast(minimum: Duration): Duration =
        if (this < minimum) minimum else this

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()

    private fun Double.display(decimals: Int = 1): String {
        val rounded = "%.${decimals}f".format(Locale.US, this)
        return rounded.trimEnd('0').trimEnd('.')
    }

    private fun Long.formatLong(): String = "%,d".format(Locale.US, this)

    private fun seriesDecimals(unit: String): Int = if (unit == "bpm") 0 else 1

    private fun enumLabel(value: String?): String =
        value?.split("_")?.joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        } ?: "Unknown"

    private fun mealLabel(mealType: Int): String =
        enumLabel(MealType.MEAL_TYPE_INT_TO_STRING_MAP[mealType])

    private fun temperatureLocationLabel(location: Int): String =
        enumLabel(BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_INT_TO_STRING_MAP[location])

    private fun stableId(seed: String): String =
        "health_${seed.hashCode().toLong().let { if (it < 0) -it else it }}"

    private fun medicalResourceTypeLabel(type: Int): String = when (type) {
        MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES -> "Allergies and intolerances"
        MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS -> "Conditions"
        MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS -> "Laboratory results"
        MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS -> "Medications"
        MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS -> "Personal details"
        MedicalResource.MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS -> "Practitioner details"
        MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY -> "Pregnancy"
        MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES -> "Procedures"
        MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY -> "Social history"
        MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES -> "Vaccines"
        MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS -> "Visits"
        MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS -> "Vital signs"
        else -> "Medical resource"
    }

    private fun fhirTypeLabel(type: Int): String = when (type) {
        FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE -> "Allergy intolerance"
        FhirResource.FHIR_RESOURCE_TYPE_CONDITION -> "Condition"
        FhirResource.FHIR_RESOURCE_TYPE_ENCOUNTER -> "Encounter"
        FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION -> "Immunization"
        FhirResource.FHIR_RESOURCE_TYPE_LOCATION -> "Location"
        FhirResource.FHIR_RESOURCE_TYPE_MEDICATION -> "Medication"
        FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_REQUEST -> "Medication request"
        FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT -> "Medication statement"
        FhirResource.FHIR_RESOURCE_TYPE_OBSERVATION -> "Observation"
        FhirResource.FHIR_RESOURCE_TYPE_ORGANIZATION -> "Organization"
        FhirResource.FHIR_RESOURCE_TYPE_PATIENT -> "Patient"
        FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER -> "Practitioner"
        FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE -> "Practitioner role"
        FhirResource.FHIR_RESOURCE_TYPE_PROCEDURE -> "Procedure"
        else -> "FHIR resource"
    }
}
