package com.trackme.domain.analytics

import com.trackme.data.local.dao.MuscleWeeklyAnalyticsDao
import com.trackme.data.local.dao.ExerciseProgressSnapshotDao
import com.trackme.data.local.dao.DailyHealthAnalyticsDao
import com.trackme.data.local.dao.BodyStateSnapshotDao
import com.trackme.data.local.entity.MuscleWeeklyAnalyticsEntity
import com.trackme.data.local.entity.ExerciseProgressSnapshotEntity
import com.trackme.data.local.entity.DailyHealthAnalyticsEntity
import com.trackme.data.local.entity.BodyStateSnapshotEntity
import com.trackme.domain.analytics.models.*
import com.trackme.domain.analytics.muscle.MuscleFatigueCalculator
import com.trackme.domain.analytics.performance.OneRMProjectionEngine
import com.trackme.domain.analytics.performance.PlateauDetector
import com.trackme.domain.analytics.recovery.ReadinessScoreCalculator
import com.trackme.domain.analytics.trends.ConsistencyMatrixGenerator
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.utils.startOfLocalDayMillis
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic and highly scientifically calibrated Body Analytics Progression Engine.
 * 
 * Orchestrates 6 core body development and performance projection modules:
 * 1. Muscle Stimulus Model: Maps exercises to muscles with custom weights and Epley-augmented volume.
 * 2. Muscle Growth saturation Index: Logarithmic adaptation with saturation limit of 200 and weekly growth clamps.
 * 3. Exponential Fatigue Model: Accumulates set-level strain with explicit exponential decay and countdown.
 * 4. Robust Strength Projection: Trimmed linear regression (removing top/bottom 10% outliers) with sufficiency guard (N>=4).
 * 5. Daily Energy Expenditure (TDEE): Combines Mifflin-St Jeor BMR, weight-calibrated steps, active calories, and volume-calibrated lifting calories.
 * 6. Physique Prediction: Saturation-respecting long-term compound growth projections.
 * 
 * Also incorporates 5 advanced analytics modules:
 * - Weekly Stimulus Heatmap with recommended targets and under/optimal/overtrained tags.
 * - Muscle Balance Analysis (Push/Pull, Quad/Ham, Upper/Lower ratios).
 * - Training Consistency Score based on training target expectations.
 * - Plateau Detection (exercises with <1% improvement over 6 weeks).
 * - Muscle Rankings (most trained, least trained, fastest growing).
 */
@Singleton
class ProgressAnalyticsEngine @Inject constructor(
    private val readinessCalculator: ReadinessScoreCalculator,
    private val oneRMEngine: OneRMProjectionEngine,
    private val fatigueCalculator: MuscleFatigueCalculator,
    private val plateauDetector: PlateauDetector,
    private val matrixGenerator: ConsistencyMatrixGenerator,
    private val exerciseRepository: ExerciseRepository,
    private val dataStore: DataStore<Preferences>,
    private val muscleWeeklyAnalyticsDao: MuscleWeeklyAnalyticsDao,
    private val exerciseProgressSnapshotDao: ExerciseProgressSnapshotDao,
    private val dailyHealthAnalyticsDao: DailyHealthAnalyticsDao,
    private val bodyStateSnapshotDao: BodyStateSnapshotDao,
) {
    
    // User-scoped cache key
    data class CacheKey(
        val userId: String,
        val latestWorkoutTimestamp: Long,
        val latestHealthTimestamp: Long,
        val workoutSize: Int,
        val healthSize: Int,
        val latestDataUpdateTime: Long
    )

    private var lastCacheKey: CacheKey? = null
    private var cachedResult: EngineAnalyticsResult? = null

    // Constant parameters for scientific calibrations
    companion object {
        const val MAX_INDEX = 200.0f
        const val ADAPTATION_CONSTANT = 10000.0f
        const val MAX_WEEKLY_GROWTH = 0.02f
        const val FATIGUE_FACTOR = 0.002f // 1000 kg volume produces 2.0% fatigue
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        const val MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY
        const val EXPECTED_WEEKLY_FREQUENCY = 4f // default expected workouts per week
        
        // List of core muscles tracked by our engine
        val ALL_MUSCLES = listOf(
            "chest", "lats", "middle back", "lower back", "shoulders",
            "biceps", "triceps", "forearms", "traps",
            "quadriceps", "hamstrings", "glutes", "calves", "abdominals"
        )
    }

    /**
     * Recomputes full analytics ONLY if data size or timestamps change.
     * Runs strictly on Dispatchers.Default for CPU performance.
     */
    suspend fun computeAnalytics(
        userId: String,
        workoutHistory: List<WorkoutSessionAnalyticsData>,
        healthHistory: List<HealthMetricsData>,
        latestDataUpdateTime: Long = 0L,
        forceRecompute: Boolean = false
    ): EngineAnalyticsResult = withContext(Dispatchers.Default) {
        
        // 1. Filter out invalid workouts
        val validatedWorkouts = workoutHistory.filter { it.dateMillis > 0 && it.exercises.isNotEmpty() }
        val validatedHealth = healthHistory.filter { it.dateMillis > 0 }

        val latestWorkoutTime = validatedWorkouts.maxOfOrNull { it.dateMillis } ?: 0L
        val latestHealthTime = validatedHealth.maxOfOrNull { it.dateMillis } ?: 0L

        val currentKey = CacheKey(
            userId = userId,
            latestWorkoutTimestamp = latestWorkoutTime,
            latestHealthTimestamp = latestHealthTime,
            workoutSize = validatedWorkouts.size,
            healthSize = validatedHealth.size,
            latestDataUpdateTime = latestDataUpdateTime
        )

        if (!forceRecompute && cachedResult != null && currentKey == lastCacheKey) {
            return@withContext cachedResult!!
        }

        val dbSnapshot = bodyStateSnapshotDao.getForUserSync(userId)
        val cacheIsValid = dbSnapshot != null && 
                dbSnapshot.updatedAt >= latestDataUpdateTime && 
                dbSnapshot.updatedAt >= maxOf(latestWorkoutTime, latestHealthTime)

        if (!forceRecompute && cacheIsValid) {
            val cachedWeekly = muscleWeeklyAnalyticsDao.getAllForSync(userId)
            val cachedExercises = exerciseProgressSnapshotDao.getAllForSync(userId)
            val cachedDaily = dailyHealthAnalyticsDao.getAllForSync(userId)
            
            if (cachedWeekly.isNotEmpty() && cachedExercises.isNotEmpty() && cachedDaily.isNotEmpty()) {
                val reconstructed = reconstructFromCache(
                    userId,
                    dbSnapshot!!,
                    cachedWeekly,
                    cachedExercises,
                    cachedDaily
                )
                if (reconstructed != null) {
                    cachedResult = reconstructed
                    lastCacheKey = currentKey
                    return@withContext reconstructed
                }
            }
        }

        // 2. Fetch full exercise details from ExerciseRepository to resolve targetMuscles and primary name maps
        val uniqueExerciseIds = validatedWorkouts.flatMap { s -> s.exercises.map { e -> e.exerciseId } }.distinct()
        val exerciseDetailMap = exerciseRepository.getByIds(uniqueExerciseIds)

        // Map raw session sets to correct exercise target muscles and names
        val allMappedExercises = validatedWorkouts.flatMap { session ->
            session.exercises.map { exercise ->
                val detail = exerciseDetailMap[exercise.exerciseId]
                exercise.copy(
                    name = detail?.name ?: exercise.name,
                    targetMuscles = detail?.primaryMuscles ?: emptyList()
                )
            }
        }

        val allMappedSets = allMappedExercises.filter { it.weightKg > 0 && it.reps > 0 }

        // --- MODULE 1: Deterministic Exercise Muscle Map Mapping & Weekly Stimulus ---
        val now = System.currentTimeMillis()
        val weeklyStimulusHistory = mutableMapOf<Int, MutableMap<String, Float>>() // Week Offset (0=this week, 11=12 weeks ago) -> Muscle -> Stimulus
        
        for (w in 0..11) {
            weeklyStimulusHistory[w] = ALL_MUSCLES.associateWith { 0.0f }.toMutableMap()
        }

        for (set in allMappedSets) {
            val weekOffset = ((now - set.dateMillis) / MILLIS_PER_WEEK).toInt()
            if (weekOffset in 0..11) {
                val detail = exerciseDetailMap[set.exerciseId]
                val primary = detail?.primaryMuscles ?: emptyList()
                val secondary = detail?.secondaryMuscles ?: emptyList()
                
                // Get deterministic custom muscle weights for compound movements or use fallbacks
                val muscleWeights = getMuscleWeightsForExercise(set.exerciseId, primary, secondary)
                
                for ((muscle, weight) in muscleWeights) {
                    val normalizedMuscle = muscle.lowercase().trim()
                    if (normalizedMuscle in ALL_MUSCLES) {
                        // Calibrated Stimulus Formula: stimulus = weight * reps * muscle_weight * (1 + reps / 30)
                        val setStimulus = set.weightKg * set.reps * weight * (1.0f + set.reps / 30.0f)
                        val currentStim = weeklyStimulusHistory[weekOffset]?.get(normalizedMuscle) ?: 0.0f
                        weeklyStimulusHistory[weekOffset]?.put(normalizedMuscle, currentStim + setStimulus)
                    }
                }
            }
        }

        // --- MODULE 2: Muscle Growth Saturation Index ---
        // Chronological calculation from week 11 down to 0 starting at baseline index loaded from DataStore (defaulting to 100.0)
        val muscleGrowthIndices = ALL_MUSCLES.associateWith { 100.0f }.toMutableMap()
        try {
            val prefs = dataStore.data.first()
            for (muscle in ALL_MUSCLES) {
                val savedValue = prefs[floatPreferencesKey("muscle_index_${muscle.lowercase()}")]
                if (savedValue != null) {
                    muscleGrowthIndices[muscle] = savedValue
                }
            }
        } catch (e: Exception) {
            // Fallback to baseline of 100.0f
        }

        for (w in (0..11).reversed()) {
            for (muscle in ALL_MUSCLES) {
                val stimulus = weeklyStimulusHistory[w]?.get(muscle) ?: 0.0f
                val currentIndex = muscleGrowthIndices[muscle] ?: 100.0f
                
                // growth_rate = ln(1 + stimulus / 10000.0)
                val growthRate = Math.log(1.0 + stimulus.toDouble() / ADAPTATION_CONSTANT.toDouble()).toFloat()
                
                // growth_rate_adjusted = growth_rate * (1 - index_current / max_index)
                val growthRateAdjusted = (growthRate * (1.0f - currentIndex / MAX_INDEX)).coerceIn(0.0f, MAX_WEEKLY_GROWTH)
                
                // index_next = index_current * (1 + growth_rate_adjusted)
                val nextIndex = currentIndex * (1.0f + growthRateAdjusted)
                muscleGrowthIndices[muscle] = nextIndex
            }
        }

        // --- MODULE 3: Exponential Muscle Fatigue Model & Cap ---
        val muscleFatigueMap = mutableMapOf<String, MuscleFatigue>()
        val sevenDaysAgo = now - (7 * MILLIS_PER_DAY)
        val recentSets = allMappedSets.filter { it.dateMillis >= sevenDaysAgo }

        for (muscle in ALL_MUSCLES) {
            var fatigueTotal = 0.0f
            val tau = getMuscleRecoveryTau(muscle) // Recovery tau in hours

            for (set in recentSets) {
                val detail = exerciseDetailMap[set.exerciseId]
                val muscleWeights = getMuscleWeightsForExercise(set.exerciseId, detail?.primaryMuscles ?: emptyList(), detail?.secondaryMuscles ?: emptyList())
                val muscleWeight = muscleWeights[muscle] ?: 0.0f
                
                if (muscleWeight > 0.0f) {
                    val dtHours = (now - set.dateMillis).toFloat() / (60.0f * 60.0f * 1000.0f)
                    // set_fatigue = weight * reps * fatigue_factor
                    val setFatigue = set.weightKg * set.reps * FATIGUE_FACTOR * muscleWeight
                    // fatigue_remaining = fatigue_set * e^(-t / tau)
                    val fatigueRemaining = setFatigue * Math.exp(-dtHours.toDouble() / tau.toDouble()).toFloat()
                    fatigueTotal += fatigueRemaining
                }
            }

            // Clamp total fatigue to physiological limit of 100%
            fatigueTotal = Math.min(100.0f, fatigueTotal)
            
            // Explicit exponential recovery countdown (hours remaining to reach < 5% recovered state)
            val remainingHours = if (fatigueTotal > 5.0f) {
                (tau * Math.log(fatigueTotal.toDouble() / 5.0)).toInt().coerceAtLeast(0)
            } else 0

            muscleFatigueMap[muscle.uppercase()] = MuscleFatigue(
                muscleGroup = muscle.uppercase(),
                fatiguePercentage = fatigueTotal.toInt(),
                recoveryTimeRemainingHours = remainingHours
            )
        }

        // --- MODULE 4: Robust Strength Projections with trim filters ---
        val strengthProjectionsList = mutableListOf<StrengthProjectionPoint>()
        val exerciseGroupedSets = allMappedSets.groupBy { it.exerciseId }
        
        for ((exerciseId, sets) in exerciseGroupedSets) {
            val detail = exerciseDetailMap[exerciseId]
            val exerciseName = detail?.name ?: exerciseId.replaceFirstChar { it.uppercase() }
            
            // Group by calendar week index over the 90 days lookback window
            val ninetyDaysAgo = now - (90 * MILLIS_PER_DAY)
            val filteredSets = sets.filter { it.dateMillis >= ninetyDaysAgo }
            
            val weeklyMaxes = filteredSets
                .groupBy { ((now - it.dateMillis) / MILLIS_PER_WEEK).toInt() }
                .filter { it.key in 0..11 }
                .mapNotNull { (weekOffset, weekSets) ->
                    // Epley: 1RM = weight * (1 + reps / 30)
                    val max1RM = weekSets.maxOfOrNull { s -> s.weightKg * (1.0f + s.reps.toFloat() / 30.0f) }
                    if (max1RM != null) {
                        val midWeekTime = now - (weekOffset.toLong() * MILLIS_PER_WEEK) - (3.5 * MILLIS_PER_DAY).toLong()
                        Pair(midWeekTime, max1RM)
                    } else null
                }
                .sortedBy { it.first }

            val current1RM = weeklyMaxes.lastOrNull()?.second ?: 0.0f
            
            if (weeklyMaxes.size < 4) {
                // Safeguard: Linear regression only runs if minimum 4 historical data points exist
                strengthProjectionsList.add(
                    StrengthProjectionPoint(
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        current1RM = current1RM,
                        projected30Days = "Not enough data for prediction",
                        projected90Days = "Not enough data for prediction",
                        projected365Days = "Not enough data for prediction",
                        historyPoints = weeklyMaxes
                    )
                )
            } else {
                // Sort 1RMs and trim top 10% and bottom 10% outliers
                val sortedBy1RM = weeklyMaxes.sortedBy { it.second }
                val trimCount = Math.max(1, (weeklyMaxes.size * 0.10).toInt())
                val trimmedPoints = if (sortedBy1RM.size > 2 * trimCount) {
                    sortedBy1RM.subList(trimCount, sortedBy1RM.size - trimCount)
                } else sortedBy1RM
                
                // Re-sort remaining points chronologically for least-squares regression
                val chronologicalPoints = trimmedPoints.sortedBy { it.first }
                
                // y = mx + c least-squares linear regression solver
                val n = chronologicalPoints.size
                val xValues = chronologicalPoints.map { (it.first - chronologicalPoints.first().first).toDouble() / MILLIS_PER_DAY.toDouble() }
                val yValues = chronologicalPoints.map { it.second.toDouble() }
                
                val sumX = xValues.sum()
                val sumY = yValues.sum()
                val sumXY = xValues.zip(yValues) { x, y -> x * y }.sum()
                val sumX2 = xValues.sumOf { it * it }
                
                val denominator = (n * sumX2) - (sumX * sumX)
                val m = if (denominator != 0.0) ((n * sumXY) - (sumX * sumY)) / denominator else 0.0
                val c = (sumY - (m * sumX)) / n
                
                val currentDayOffset = (now - chronologicalPoints.first().first).toDouble() / MILLIS_PER_DAY.toDouble()
                
                // Project and clamp for stability (prevent wild negative values or infinite progression)
                val p30 = Math.max(current1RM * 0.5f, Math.min(current1RM * 3.0f, (m * (currentDayOffset + 30.0) + c).toFloat()))
                val p90 = Math.max(current1RM * 0.5f, Math.min(current1RM * 3.0f, (m * (currentDayOffset + 90.0) + c).toFloat()))
                val p365 = Math.max(current1RM * 0.5f, Math.min(current1RM * 3.0f, (m * (currentDayOffset + 365.0) + c).toFloat()))
                
                strengthProjectionsList.add(
                    StrengthProjectionPoint(
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        current1RM = current1RM,
                        projected30Days = String.format(Locale.US, "%.1f kg", p30),
                        projected90Days = String.format(Locale.US, "%.1f kg", p90),
                        projected365Days = String.format(Locale.US, "%.1f kg", p365),
                        historyPoints = weeklyMaxes
                    )
                )
            }
        }

        // --- MODULE 5: Daily Energy Expenditure (TDEE) ---
        val latestHealth = validatedHealth.maxByOrNull { it.dateMillis }
        val userWeight = latestHealth?.weightKg ?: 70.0f // Fallback standard
        val userHeight = latestHealth?.heightCm ?: 175.0f // Fallback standard
        
        // Mifflin-St Jeor BMR: BMR = 10 * weight + 6.25 * height - 120 (Assuming age 25, male standard)
        val bmr = 10.0f * userWeight + 6.25f * userHeight - 120.0f
        
        val steps = latestHealth?.steps ?: 0L
        // Step Calories: calories_steps = steps * weight_kg * 0.0005
        val stepsCalories = steps.toFloat() * userWeight * 0.0005f
        val activeCalories = latestHealth?.activeCaloriesBurned ?: 0.0f
        
        // Today's Lifting volume in kg
        val todayStart = startOfLocalDayMillis(now)
        val todaySets = allMappedSets.filter { it.dateMillis >= todayStart }
        val todayVolumeKg = todaySets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
        // Lifting Calories: calories_lifting = volume_kg * 0.04
        val liftingCalories = todayVolumeKg * 0.04f
        
        val totalTDEE = bmr + activeCalories + stepsCalories + liftingCalories
        val energyBreakdown = EnergyBreakdown(
            dateMillis = now,
            bmr = bmr,
            steps = steps,
            stepsCalories = stepsCalories,
            activeCalories = activeCalories,
            caloriesLifting = liftingCalories,
            totalTDEE = totalTDEE
        )

        // --- MODULE 6: Saturation-Respecting Physique Prediction ---
        val physiquePredictions = mutableListOf<PhysiqueProjectionPoint>()
        for (muscle in ALL_MUSCLES) {
            val currentIndex = muscleGrowthIndices[muscle] ?: 100.0f
            
            // Calculate average weekly stimulus over active training history (up to 12 weeks)
            val activeWeeks = (0..11).filter { (weeklyStimulusHistory[it]?.get(muscle) ?: 0.0f) > 0f }
            val avgWeeklyStimulus = if (activeWeeks.isNotEmpty()) {
                activeWeeks.map { weeklyStimulusHistory[it]?.get(muscle) ?: 0.0f }.average().toFloat()
            } else 0.0f
            
            // Growth rate using logarithmic adaptation
            val baseWeeklyGrowthRate = Math.log(1.0 + avgWeeklyStimulus.toDouble() / ADAPTATION_CONSTANT.toDouble()).toFloat()
            val weeklyGrowthRate = Math.min(MAX_WEEKLY_GROWTH, baseWeeklyGrowthRate) // Clamp weekly growth to 2% max
            
            // future_index = max_index - (max_index - current_index) * e^(-growth_rate * weeks)
            val proj4 = MAX_INDEX - (MAX_INDEX - currentIndex) * Math.exp(-weeklyGrowthRate.toDouble() * 4.0).toFloat()
            val proj12 = MAX_INDEX - (MAX_INDEX - currentIndex) * Math.exp(-weeklyGrowthRate.toDouble() * 12.0).toFloat()
            val proj52 = MAX_INDEX - (MAX_INDEX - currentIndex) * Math.exp(-weeklyGrowthRate.toDouble() * 52.0).toFloat()

            physiquePredictions.add(
                PhysiqueProjectionPoint(
                    muscleGroup = muscle,
                    currentMuscleIndex = currentIndex,
                    projectedIndex4Weeks = proj4,
                    projectedIndex12Weeks = proj12,
                    projectedIndex52Weeks = proj52
                )
            )
        }

        // --- MODULE 7: Muscle Balance Analysis ---
        val pushMuscles = listOf("chest", "triceps", "shoulders")
        val pullMuscles = listOf("lats", "middle back", "biceps", "traps", "forearms")
        val quadMuscles = listOf("quadriceps")
        val hamMuscles = listOf("hamstrings")
        val upperMuscles = pushMuscles + pullMuscles + listOf("abdominals")
        val lowerMuscles = quadMuscles + hamMuscles + listOf("glutes", "calves")

        val sumPush = pushMuscles.map { muscleGrowthIndices[it] ?: 100.0f }.sum()
        val sumPull = pullMuscles.map { muscleGrowthIndices[it] ?: 100.0f }.sum()
        val pushPullRatio = sumPush / Math.max(1.0f, sumPull)
        val pushPullStatus = when {
            pushPullRatio > 1.15f -> "Push Dominant"
            pushPullRatio < 0.85f -> "Pull Dominant"
            else -> "Balanced"
        }

        val sumQuad = quadMuscles.map { muscleGrowthIndices[it] ?: 100.0f }.sum()
        val sumHam = hamMuscles.map { muscleGrowthIndices[it] ?: 100.0f }.sum()
        val quadHamRatio = sumQuad / Math.max(1.0f, sumHam)
        val quadHamStatus = when {
            quadHamRatio > 1.15f -> "Quad Dominant"
            quadHamRatio < 0.85f -> "Hamstring Dominant"
            else -> "Balanced"
        }

        val sumUpper = upperMuscles.map { muscleGrowthIndices[it] ?: 100.0f }.sum()
        val sumLower = lowerMuscles.map { muscleGrowthIndices[it] ?: 100.0f }.sum()
        val upperLowerRatio = sumUpper / Math.max(1.0f, sumLower)
        val upperLowerStatus = when {
            upperLowerRatio > 1.25f -> "Upper Body Dominant"
            upperLowerRatio < 0.75f -> "Lower Body Dominant"
            else -> "Balanced"
        }

        val muscleBalanceInfo = MuscleBalanceInfo(
            pushPullRatio = pushPullRatio,
            pushPullStatus = pushPullStatus,
            quadHamRatio = quadHamRatio,
            quadHamStatus = quadHamStatus,
            upperLowerRatio = upperLowerRatio,
            upperLowerStatus = upperLowerStatus
        )

        // --- MODULE 8: Plateau Detection (<1% improvement in max 1RM over 6 weeks) ---
        val plateauInfosList = mutableListOf<PlateauInfo>()
        for ((exerciseId, sets) in exerciseGroupedSets) {
            val detail = exerciseDetailMap[exerciseId]
            val exerciseName = detail?.name ?: exerciseId
            
            // Check max 1RM this week vs 6 weeks ago
            val thisWeekSets = sets.filter { it.dateMillis >= now - MILLIS_PER_WEEK }
            val sixWeeksAgoSets = sets.filter { it.dateMillis in (now - 7 * MILLIS_PER_WEEK)..(now - 5 * MILLIS_PER_WEEK) }
            
            val maxThisWeek = thisWeekSets.maxOfOrNull { s -> s.weightKg * (1.0f + s.reps.toFloat() / 30.0f) }
            val maxSixWeeksAgo = sixWeeksAgoSets.maxOfOrNull { s -> s.weightKg * (1.0f + s.reps.toFloat() / 30.0f) }
            
            if (maxThisWeek != null && maxSixWeeksAgo != null && maxSixWeeksAgo > 0.0f) {
                val improvement = (maxThisWeek - maxSixWeeksAgo) / maxSixWeeksAgo
                val isPlateaued = improvement < 0.01f // < 1% improvement
                plateauInfosList.add(
                    PlateauInfo(
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        improvementPercentage = improvement * 100.0f,
                        isPlateaued = isPlateaued
                    )
                )
            }
        }

        // --- MODULE 9: Muscle Rankings ---
        // Sum total stimulus in the last 4 weeks
        val last4WeeksStimulus = ALL_MUSCLES.associateWith { 0.0f }.toMutableMap()
        for (w in 0..3) {
            for (muscle in ALL_MUSCLES) {
                last4WeeksStimulus[muscle] = (last4WeeksStimulus[muscle] ?: 0.0f) + (weeklyStimulusHistory[w]?.get(muscle) ?: 0.0f)
            }
        }
        
        val sortedByStimulus = last4WeeksStimulus.toList().sortedByDescending { it.second }
        val mostTrained = sortedByStimulus.take(3).map { it.first.uppercase() }
        val leastTrained = sortedByStimulus.takeLast(3).map { it.first.uppercase() }
        
        // Growth Index Delta over last 12 weeks
        val growthDelta = ALL_MUSCLES.associateWith { muscle ->
            val currentIndex = muscleGrowthIndices[muscle] ?: 100.0f
            currentIndex - 100.0f
        }
        val fastestGrowing = growthDelta.toList().sortedByDescending { it.second }.take(3).map { it.first.uppercase() }
        
        val muscleRankingInfo = MuscleRankingInfo(
            mostTrained = mostTrained,
            leastTrained = leastTrained,
            fastestGrowing = fastestGrowing
        )

        // --- MODULE 10: Training Consistency Score ---
        val thirtyDaysAgo = now - (30 * MILLIS_PER_DAY)
        val distinctTrainingDays = allMappedSets
            .filter { it.dateMillis >= thirtyDaysAgo }
            .map { startOfLocalDayMillis(it.dateMillis) }
            .distinct()
            .size
        
        val expectedWorkouts = EXPECTED_WEEKLY_FREQUENCY * 4.0f
        val consistencyScore = Math.min(1.0f, distinctTrainingDays.toFloat() / expectedWorkouts)

        // --- MODULE 11: Muscle Stimulus Heatmap with Targets ---
        val stimulusHeatmapList = mutableListOf<MuscleStimulusHeatmap>()
        val thisWeekStimulus = weeklyStimulusHistory[0] ?: emptyMap()
        
        for (muscle in ALL_MUSCLES) {
            val stim = thisWeekStimulus[muscle] ?: 0.0f
            val (minRecommended, maxRecommended) = getMuscleStimulusRange(muscle)
            val status = when {
                stim < minRecommended -> "Undertrained"
                stim > maxRecommended -> "Overtrained"
                else -> "Optimal"
            }
            val ratio = if (maxRecommended > minRecommended) {
                (stim - minRecommended) / (maxRecommended - minRecommended)
            } else 0.0f

            stimulusHeatmapList.add(
                MuscleStimulusHeatmap(
                    muscleGroup = muscle.uppercase(),
                    stimulus = stim,
                    minRecommended = minRecommended,
                    maxRecommended = maxRecommended,
                    status = status,
                    ratio = ratio.coerceIn(0.0f, 1.0f)
                )
            )
        }

        // --- WEEKLY VOLUME HISTORY (for timeline timeline graph) ---
        val weeklyVolumeList = mutableListOf<Pair<String, Float>>()
        val sdf = SimpleDateFormat("MMM d", Locale.US)
        for (w in (0..7).reversed()) {
            val weekStart = now - ((w + 1) * MILLIS_PER_WEEK)
            val weekEnd = now - (w * MILLIS_PER_WEEK)
            val weekSets = allMappedSets.filter { it.dateMillis in (weekStart + 1)..weekEnd }
            val weekVolume = weekSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
            val labelDate = Date(weekStart)
            weeklyVolumeList.add(Pair(sdf.format(labelDate), weekVolume))
        }

        // 3. Assemble and return full progress state
        val fullProgressAnalytics = FullProgressAnalytics(
            muscleDevelopment = ALL_MUSCLES.map { muscle ->
                val index = muscleGrowthIndices[muscle] ?: 100.0f
                MuscleDevelopment(
                    muscleGroup = muscle.uppercase(),
                    growthIndex = index,
                    percentageGrowth = index - 100.0f,
                    stimulusThisWeek = thisWeekStimulus[muscle] ?: 0.0f
                )
            },
            stimulusHeatmap = stimulusHeatmapList,
            strengthProjections = strengthProjectionsList,
            energyExpenditure = energyBreakdown,
            physiquePrediction = physiquePredictions,
            muscleBalance = muscleBalanceInfo,
            plateaus = plateauInfosList,
            muscleRankings = muscleRankingInfo,
            consistencyScore = consistencyScore,
            weeklyVolumeHistory = weeklyVolumeList
        )

        // Delegate to original simple engines to preserve legacy fields
        val legacyReadiness = readinessCalculator.calculate(healthHistory, healthHistory.firstOrNull { startOfLocalDayMillis(it.dateMillis) == startOfLocalDayMillis(now) })
        val legacyProjections = strengthProjectionsList.take(5).map { proj ->
            OneRMProjection(
                exerciseId = proj.exerciseId,
                exerciseName = proj.exerciseName,
                current1RM = proj.current1RM,
                projected1RM14Days = proj.current1RM * 1.02f, // Simple legacy ratio approximation
                historyPoints = proj.historyPoints
            )
        }
        val legacyPlateaus = plateauInfosList.filter { it.isPlateaued }.map { p ->
            PlateauAlert(
                exerciseId = p.exerciseId,
                exerciseName = p.exerciseName,
                stalledSinceMillis = now - (6 * MILLIS_PER_WEEK),
                recommendation = "De-load 10% volume or alternate grip variations to break adaptation."
            )
        }
        val legacyMatrix = matrixGenerator.generate(validatedWorkouts)

        val result = EngineAnalyticsResult(
            readinessScore = legacyReadiness,
            oneRmProjections = legacyProjections,
            muscleFatigueMap = muscleFatigueMap,
            plateauAlerts = legacyPlateaus,
            matrixPosition = legacyMatrix,
            fullAnalytics = fullProgressAnalytics
        )

        // Cache result for quick subsecond future retrieval
        cachedResult = result
        lastCacheKey = currentKey

        // Persist computed growth indices back to DataStore
        try {
            dataStore.edit { prefs ->
                for (muscle in ALL_MUSCLES) {
                    val currentIndex = muscleGrowthIndices[muscle] ?: 100.0f
                    prefs[floatPreferencesKey("muscle_index_${muscle.lowercase()}")] = currentIndex
                }
            }
        } catch (e: Exception) {
            // Ignore write errors to prevent UI crashes
        }

        // Save computed delta updates to Room cache tables
        try {
            val weeklyEntities = mutableListOf<MuscleWeeklyAnalyticsEntity>()
            for (w in 0..11) {
                for (muscle in ALL_MUSCLES) {
                    val growthIndex = muscleGrowthIndices[muscle] ?: 100.0f
                    val stimulus = weeklyStimulusHistory[w]?.get(muscle) ?: 0.0f
                    val fatigue = if (w == 0) (muscleFatigueMap[muscle.uppercase()]?.fatiguePercentage?.toFloat() ?: 0.0f) else 0.0f

                    weeklyEntities.add(
                        MuscleWeeklyAnalyticsEntity(
                            id = "${userId}_${muscle}_$w",
                            userId = userId,
                            muscleGroup = muscle,
                            weekOffset = w,
                            weeklyStimulus = stimulus,
                            growthIndex = growthIndex,
                            fatigue = fatigue,
                            updatedAt = now,
                            isSynced = false
                        )
                    )
                }
            }
            weeklyEntities.forEach { muscleWeeklyAnalyticsDao.insert(it) }

            val exerciseEntities = strengthProjectionsList.map {
                val isPlateaued = plateauInfosList.firstOrNull { p -> p.exerciseId == it.exerciseId }?.isPlateaued ?: false
                val improvementPercentage = plateauInfosList.firstOrNull { p -> p.exerciseId == it.exerciseId }?.improvementPercentage ?: 0.0f
                val p30Val = it.projected30Days.replace(" kg", "").toFloatOrNull() ?: it.current1RM
                val p90Val = it.projected90Days.replace(" kg", "").toFloatOrNull() ?: it.current1RM
                val p365Val = it.projected365Days.replace(" kg", "").toFloatOrNull() ?: it.current1RM

                ExerciseProgressSnapshotEntity(
                    id = "${userId}_${it.exerciseId}",
                    userId = userId,
                    exerciseId = it.exerciseId,
                    exerciseName = it.exerciseName,
                    current1RM = it.current1RM,
                    projected1RM30Days = p30Val,
                    projected1RM90Days = p90Val,
                    projected1RM365Days = p365Val,
                    isPlateaued = isPlateaued,
                    improvementPercentage = improvementPercentage,
                    updatedAt = now,
                    isSynced = false
                )
            }
            exerciseEntities.forEach { exerciseProgressSnapshotDao.insert(it) }

            val dailyEntities = validatedHealth.map {
                val stepsCaloriesVal = (it.steps ?: 0L).toFloat() * userWeight * 0.0005f
                val liftingCaloriesVal = 0.0f
                val totalTDEEVal = bmr + (it.activeCaloriesBurned ?: 0.0f) + stepsCaloriesVal + liftingCaloriesVal

                DailyHealthAnalyticsEntity(
                    id = "${userId}_${it.dateMillis}",
                    userId = userId,
                    dateMillis = it.dateMillis,
                    bmr = bmr,
                    caloriesSteps = stepsCaloriesVal,
                    caloriesActive = it.activeCaloriesBurned ?: 0.0f,
                    caloriesLifting = liftingCaloriesVal,
                    caloriesTDEE = totalTDEEVal,
                    steps = it.steps ?: 0L,
                    weightKg = it.weightKg ?: userWeight,
                    updatedAt = now,
                    isSynced = false
                )
            }
            dailyEntities.forEach { dailyHealthAnalyticsDao.insert(it) }

            val bodyStateEntity = BodyStateSnapshotEntity(
                id = userId,
                userId = userId,
                consistencyScore = consistencyScore,
                readinessScore = legacyReadiness.score,
                muscleBalancePushPull = pushPullRatio,
                muscleBalanceQuadHam = quadHamRatio,
                muscleBalanceUpperLower = upperLowerRatio,
                updatedAt = now,
                isSynced = false
            )
            bodyStateSnapshotDao.insert(bodyStateEntity)
        } catch (e: Exception) {
            // Ignore database write caching failures to prioritize returning calculated state
        }

        result
    }

    private fun reconstructFromCache(
        userId: String,
        bodySnapshot: BodyStateSnapshotEntity,
        weeklyList: List<MuscleWeeklyAnalyticsEntity>,
        exerciseList: List<ExerciseProgressSnapshotEntity>,
        dailyList: List<DailyHealthAnalyticsEntity>
    ): EngineAnalyticsResult? {
        try {
            val now = System.currentTimeMillis()
            
            // 1. Muscle Fatigue Map
            val muscleFatigueMap = weeklyList.filter { it.weekOffset == 0 }.associate {
                val tau = getMuscleRecoveryTau(it.muscleGroup)
                val remainingHours = if (it.fatigue > 5.0f) {
                    (tau * Math.log(it.fatigue.toDouble() / 5.0)).toInt().coerceAtLeast(0)
                } else 0
                
                it.muscleGroup.uppercase() to MuscleFatigue(
                    muscleGroup = it.muscleGroup.uppercase(),
                    fatiguePercentage = it.fatigue.toInt(),
                    recoveryTimeRemainingHours = remainingHours
                )
            }

            // 2. Strength Projections
            val strengthProjectionsList = exerciseList.map {
                StrengthProjectionPoint(
                    exerciseId = it.exerciseId,
                    exerciseName = it.exerciseName,
                    current1RM = it.current1RM,
                    projected30Days = String.format(Locale.US, "%.1f kg", it.projected1RM30Days),
                    projected90Days = String.format(Locale.US, "%.1f kg", it.projected1RM90Days),
                    projected365Days = String.format(Locale.US, "%.1f kg", it.projected1RM365Days),
                    historyPoints = emptyList()
                )
            }

            // 3. Daily Energy Expenditure (TDEE)
            val todayStart = startOfLocalDayMillis(now)
            val todayDaily = dailyList.firstOrNull { startOfLocalDayMillis(it.dateMillis) == todayStart } ?: dailyList.firstOrNull() ?: return null
            
            val energyBreakdown = EnergyBreakdown(
                dateMillis = now,
                bmr = todayDaily.bmr,
                steps = todayDaily.steps,
                stepsCalories = todayDaily.caloriesSteps,
                activeCalories = todayDaily.caloriesActive,
                caloriesLifting = todayDaily.caloriesLifting,
                totalTDEE = todayDaily.caloriesTDEE
            )

            // 4. Physique Predictions
            val weeklyStimulusHistory = mutableMapOf<Int, MutableMap<String, Float>>()
            for (w in 0..11) {
                weeklyStimulusHistory[w] = ALL_MUSCLES.associateWith { 0.0f }.toMutableMap()
            }
            weeklyList.forEach {
                val m = it.muscleGroup.lowercase()
                if (m in ALL_MUSCLES && it.weekOffset in 0..11) {
                    weeklyStimulusHistory[it.weekOffset]?.put(m, it.weeklyStimulus)
                }
            }

            val muscleGrowthIndices = weeklyList.filter { it.weekOffset == 0 }.associate {
                it.muscleGroup.lowercase() to it.growthIndex
            }

            val physiquePredictions = ALL_MUSCLES.map { muscle ->
                val currentIndex = muscleGrowthIndices[muscle] ?: 100.0f
                val activeWeeks = (0..11).filter { (weeklyStimulusHistory[it]?.get(muscle) ?: 0.0f) > 0f }
                val avgWeeklyStimulus = if (activeWeeks.isNotEmpty()) {
                    activeWeeks.map { weeklyStimulusHistory[it]?.get(muscle) ?: 0.0f }.average().toFloat()
                } else 0.0f
                val baseWeeklyGrowthRate = Math.log(1.0 + avgWeeklyStimulus.toDouble() / ADAPTATION_CONSTANT.toDouble()).toFloat()
                val weeklyGrowthRate = Math.min(MAX_WEEKLY_GROWTH, baseWeeklyGrowthRate)
                val proj4 = MAX_INDEX - (MAX_INDEX - currentIndex) * Math.exp(-weeklyGrowthRate.toDouble() * 4.0).toFloat()
                val proj12 = MAX_INDEX - (MAX_INDEX - currentIndex) * Math.exp(-weeklyGrowthRate.toDouble() * 12.0).toFloat()
                val proj52 = MAX_INDEX - (MAX_INDEX - currentIndex) * Math.exp(-weeklyGrowthRate.toDouble() * 52.0).toFloat()

                PhysiqueProjectionPoint(
                    muscleGroup = muscle,
                    currentMuscleIndex = currentIndex,
                    projectedIndex4Weeks = proj4,
                    projectedIndex12Weeks = proj12,
                    projectedIndex52Weeks = proj52
                )
            }

            // 5. Muscle Balance
            val pushPullStatus = when {
                bodySnapshot.muscleBalancePushPull > 1.15f -> "Push Dominant"
                bodySnapshot.muscleBalancePushPull < 0.85f -> "Pull Dominant"
                else -> "Balanced"
            }
            val quadHamStatus = when {
                bodySnapshot.muscleBalanceQuadHam > 1.15f -> "Quad Dominant"
                bodySnapshot.muscleBalanceQuadHam < 0.85f -> "Hamstring Dominant"
                else -> "Balanced"
            }
            val upperLowerStatus = when {
                bodySnapshot.muscleBalanceUpperLower > 1.25f -> "Upper Body Dominant"
                bodySnapshot.muscleBalanceUpperLower < 0.75f -> "Lower Body Dominant"
                else -> "Balanced"
            }

            val muscleBalanceInfo = MuscleBalanceInfo(
                pushPullRatio = bodySnapshot.muscleBalancePushPull,
                pushPullStatus = pushPullStatus,
                quadHamRatio = bodySnapshot.muscleBalanceQuadHam,
                quadHamStatus = quadHamStatus,
                upperLowerRatio = bodySnapshot.muscleBalanceUpperLower,
                upperLowerStatus = upperLowerStatus
            )

            // 6. Plateaus
            val plateauInfosList = exerciseList.map {
                PlateauInfo(
                    exerciseId = it.exerciseId,
                    exerciseName = it.exerciseName,
                    improvementPercentage = it.improvementPercentage,
                    isPlateaued = it.isPlateaued
                )
            }

            // 7. Muscle Rankings
            val last4WeeksStimulus = ALL_MUSCLES.associateWith { 0.0f }.toMutableMap()
            for (w in 0..3) {
                for (muscle in ALL_MUSCLES) {
                    last4WeeksStimulus[muscle] = (last4WeeksStimulus[muscle] ?: 0.0f) + (weeklyStimulusHistory[w]?.get(muscle) ?: 0.0f)
                }
            }
            val sortedByStimulus = last4WeeksStimulus.toList().sortedByDescending { it.second }
            val mostTrained = sortedByStimulus.take(3).map { it.first.uppercase() }
            val leastTrained = sortedByStimulus.takeLast(3).map { it.first.uppercase() }

            val fastestGrowing = muscleGrowthIndices.toList().sortedByDescending { it.second - 100.0f }.take(3).map { it.first.uppercase() }

            val muscleRankingInfo = MuscleRankingInfo(
                mostTrained = mostTrained,
                leastTrained = leastTrained,
                fastestGrowing = fastestGrowing
            )

            // 8. Weekly Stimulus Heatmap with Targets
            val thisWeekStimulus = weeklyStimulusHistory[0] ?: emptyMap()
            val stimulusHeatmapList = ALL_MUSCLES.map { muscle ->
                val stim = thisWeekStimulus[muscle] ?: 0.0f
                val (minRecommended, maxRecommended) = getMuscleStimulusRange(muscle)
                val status = when {
                    stim < minRecommended -> "Undertrained"
                    stim > maxRecommended -> "Overtrained"
                    else -> "Optimal"
                }
                val ratio = if (maxRecommended > minRecommended) {
                    (stim - minRecommended) / (maxRecommended - minRecommended)
                } else 0.0f

                MuscleStimulusHeatmap(
                    muscleGroup = muscle.uppercase(),
                    stimulus = stim,
                    minRecommended = minRecommended,
                    maxRecommended = maxRecommended,
                    status = status,
                    ratio = ratio.coerceIn(0.0f, 1.0f)
                )
            }

            // 9. Assemble FullProgressAnalytics
            val fullProgressAnalytics = FullProgressAnalytics(
                muscleDevelopment = ALL_MUSCLES.map { muscle ->
                    val index = muscleGrowthIndices[muscle] ?: 100.0f
                    MuscleDevelopment(
                        muscleGroup = muscle.uppercase(),
                        growthIndex = index,
                        percentageGrowth = index - 100.0f,
                        stimulusThisWeek = thisWeekStimulus[muscle] ?: 0.0f
                    )
                },
                stimulusHeatmap = stimulusHeatmapList,
                strengthProjections = strengthProjectionsList,
                energyExpenditure = energyBreakdown,
                physiquePrediction = physiquePredictions,
                muscleBalance = muscleBalanceInfo,
                plateaus = plateauInfosList,
                muscleRankings = muscleRankingInfo,
                consistencyScore = bodySnapshot.consistencyScore,
                weeklyVolumeHistory = emptyList()
            )

            // Assemble EngineAnalyticsResult
            val legacyReadiness = ReadinessScore(
                score = bodySnapshot.readinessScore,
                status = when {
                    bodySnapshot.readinessScore >= 80 -> "Optimal"
                    bodySnapshot.readinessScore >= 50 -> "Good"
                    else -> "Needs Recovery"
                },
                debug = null
            )
            val legacyProjections = strengthProjectionsList.take(5).map { proj ->
                OneRMProjection(
                    exerciseId = proj.exerciseId,
                    exerciseName = proj.exerciseName,
                    current1RM = proj.current1RM,
                    projected1RM14Days = proj.current1RM * 1.02f,
                    historyPoints = emptyList()
                )
            }
            val legacyPlateaus = plateauInfosList.filter { it.isPlateaued }.map { p ->
                PlateauAlert(
                    exerciseId = p.exerciseId,
                    exerciseName = p.exerciseName,
                    stalledSinceMillis = now - (6 * MILLIS_PER_WEEK),
                    recommendation = "De-load 10% volume or alternate grip variations to break adaptation."
                )
            }

            return EngineAnalyticsResult(
                readinessScore = legacyReadiness,
                oneRmProjections = legacyProjections,
                muscleFatigueMap = muscleFatigueMap,
                plateauAlerts = legacyPlateaus,
                matrixPosition = ConsistencyMatrixPosition(0.0f, 0.0f, MatrixQuadrant.BUILDER),
                fullAnalytics = fullProgressAnalytics
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Recommended weekly stimulus targets based on physiology literature.
     */
    private fun getMuscleStimulusRange(muscle: String): Pair<Float, Float> {
        val m = muscle.lowercase()
        return when (m) {
            "chest" -> Pair(8000.0f, 12000.0f)
            "lats", "middle back", "lower back", "traps" -> Pair(9000.0f, 13000.0f)
            "quadriceps", "hamstrings", "glutes" -> Pair(10000.0f, 15000.0f)
            "shoulders" -> Pair(5000.0f, 9000.0f)
            "biceps", "triceps" -> Pair(4000.0f, 8000.0f)
            "abdominals", "calves" -> Pair(3000.0f, 6000.0f)
            else -> Pair(4000.0f, 8000.0f)
        }
    }

    /**
     * Physiological muscle recovery half-life parameters in hours.
     */
    private fun getMuscleRecoveryTau(muscle: String): Float {
        val m = muscle.lowercase()
        return when (m) {
            "chest", "lats", "middle back", "lower back", "quadriceps", "hamstrings" -> 72.0f
            "shoulders", "glutes", "biceps", "triceps", "abdominals" -> 60.0f
            "calves", "forearms", "traps" -> 36.0f
            else -> 48.0f
        }
    }

    /**
     * Scientific distribution weights per muscle for major compound and isolation exercises.
     */
    private fun getMuscleWeightsForExercise(
        exerciseId: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>
    ): Map<String, Float> {
        val idLower = exerciseId.lowercase()
        
        return when {
            // Chest Bench Press variants
            idLower.contains("bench_press") || idLower.contains("bench press") -> mapOf(
                "chest" to 1.0f,
                "triceps" to 0.6f,
                "shoulders" to 0.4f
            )
            // Lower body squat variations
            idLower.contains("squat") -> mapOf(
                "quadriceps" to 1.0f,
                "glutes" to 0.6f,
                "hamstrings" to 0.4f,
                "calves" to 0.2f
            )
            // Heavy hinge / Deadlift compound variants
            idLower.contains("deadlift") -> mapOf(
                "lower back" to 1.0f,
                "hamstrings" to 0.8f,
                "glutes" to 0.8f,
                "traps" to 0.5f,
                "forearms" to 0.4f
            )
            // Overhead Press / Shoulder press variants
            idLower.contains("overhead_press") || idLower.contains("overhead press") || 
            idLower.contains("military_press") || idLower.contains("military press") || 
            idLower.contains("shoulder_press") || idLower.contains("shoulder press") -> mapOf(
                "shoulders" to 1.0f,
                "triceps" to 0.6f,
                "traps" to 0.4f
            )
            // Row variations
            idLower.contains("row") -> mapOf(
                "middle back" to 1.0f,
                "lats" to 0.8f,
                "biceps" to 0.6f,
                "forearms" to 0.4f
            )
            // Pullups / Lats pulldown variants
            idLower.contains("pullup") || idLower.contains("pull_up") || 
            idLower.contains("chinup") || idLower.contains("chin_up") || 
            idLower.contains("pulldown") || idLower.contains("pull_down") -> mapOf(
                "lats" to 1.0f,
                "middle back" to 0.8f,
                "biceps" to 0.6f,
                "forearms" to 0.4f
            )
            // General custom isolation fallback
            else -> {
                val map = mutableMapOf<String, Float>()
                primaryMuscles.forEach { map[it.lowercase().trim()] = 1.0f }
                secondaryMuscles.forEach { map[it.lowercase().trim()] = 0.5f }
                map
            }
        }
    }
}
