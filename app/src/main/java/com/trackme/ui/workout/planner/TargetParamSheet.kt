package com.trackme.ui.workout.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.LoggingType
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.loggingType
import com.trackme.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetParamSheet(
    exercise: Exercise,
    initial: PlannedExercise? = null,
    onConfirm: (
        targetSets: Int,
        targetReps: Int?,
        targetWeightKg: Float?,
        targetDurationSeconds: Int?,
        targetDistanceKm: Float?,
        targetSpeedKmh: Float?,
        targetIncline: Float?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val loggingType = exercise.loggingType()
    var sets by remember { mutableStateOf(initial?.targetSets?.toString() ?: "3") }
    var reps by remember { mutableStateOf(initial?.targetReps?.toString() ?: "10") }
    var weightKg by remember {
        mutableStateOf(initial?.targetWeightKg?.let { if (it > 0f) it.toString() else "" } ?: "")
    }
    var durationInput by remember {
        mutableStateOf(
            when (loggingType) {
                LoggingType.CARDIO -> initial?.targetDurationSeconds?.let { (it / 60).toString() } ?: "20"
                else -> initial?.targetDurationSeconds?.toString() ?: "30"
            }
        )
    }
    var distanceKm by remember { mutableStateOf(initial?.targetDistanceKm?.toString() ?: "") }
    var speedKmh by remember { mutableStateOf(initial?.targetSpeedKmh?.toString() ?: "8") }
    var incline by remember { mutableStateOf(initial?.targetIncline?.toString() ?: "0") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(loggingType.displayName, style = MaterialTheme.typography.labelMedium, color = Violet)
            }

            when (loggingType) {
                LoggingType.WEIGHTED_REPS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParamField("Sets", sets, Modifier.weight(1f), KeyboardType.Number) { sets = it }
                        ParamField("Reps", reps, Modifier.weight(1f), KeyboardType.Number) { reps = it }
                    }
                    ParamField("Weight (kg)", weightKg, Modifier.fillMaxWidth(), KeyboardType.Decimal) { weightKg = it }
                }
                LoggingType.BODYWEIGHT_REPS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParamField("Sets", sets, Modifier.weight(1f), KeyboardType.Number) { sets = it }
                        ParamField("Reps", reps, Modifier.weight(1f), KeyboardType.Number) { reps = it }
                    }
                }
                LoggingType.TIMED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParamField("Sets", sets, Modifier.weight(1f), KeyboardType.Number) { sets = it }
                        ParamField("Duration (s)", durationInput, Modifier.weight(1f), KeyboardType.Number) { durationInput = it }
                    }
                }
                LoggingType.CARDIO -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParamField("Duration (min)", durationInput, Modifier.weight(1f), KeyboardType.Number) { durationInput = it }
                        ParamField("Speed (km/h)", speedKmh, Modifier.weight(1f), KeyboardType.Decimal) { speedKmh = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParamField("Distance (km)", distanceKm, Modifier.weight(1f), KeyboardType.Decimal) { distanceKm = it }
                        ParamField("Incline (%)", incline, Modifier.weight(1f), KeyboardType.Decimal) { incline = it }
                    }
                }
            }

            Button(
                onClick = {
                    val parsedSets = sets.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    when (loggingType) {
                        LoggingType.WEIGHTED_REPS -> onConfirm(
                            parsedSets, reps.toIntOrNull(), weightKg.toFloatOrNull(),
                            null, null, null, null,
                        )
                        LoggingType.BODYWEIGHT_REPS -> onConfirm(
                            parsedSets, reps.toIntOrNull(), null,
                            null, null, null, null,
                        )
                        LoggingType.TIMED -> onConfirm(
                            parsedSets, null, null,
                            durationInput.toIntOrNull(), null, null, null,
                        )
                        LoggingType.CARDIO -> onConfirm(
                            1, null, null,
                            durationInput.toIntOrNull()?.times(60),
                            distanceKm.toFloatOrNull(),
                            speedKmh.toFloatOrNull(),
                            incline.toFloatOrNull(),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun ParamField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
    )
}
