package com.trackme.ui.workout.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.LoggingType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.loggingType
import com.trackme.ui.components.WheelPicker
import com.trackme.ui.components.HorizontalWheelPicker
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
    inputStyle: String = "TAP_EXPAND",
    onDismiss: () -> Unit,
) {
    val loggingType = exercise.loggingType()
    
    val wholeNumbers = remember { (0..300).toList() }
    val decimalWeightOptions = remember { listOf(0.0f, 0.25f, 0.5f, 0.75f) }
    val decimalTenths = remember { (0..9).map { it / 10f } }
    val setsList = remember { (1..20).toList() }
    val repsList = remember { (0..100).toList() }
    val minsList = remember { (0..120).toList() }
    val secsList = remember { (0..59).toList() }

    var sets by remember { mutableIntStateOf(initial?.targetSets ?: 3) }
    
    val initialWeight = initial?.targetWeightKg ?: 0f
    var weightWhole by remember { mutableIntStateOf(initialWeight.toInt()) }
    var weightDecimal by remember { mutableFloatStateOf(decimalWeightOptions.minByOrNull { kotlin.math.abs(it - (initialWeight - initialWeight.toInt())) } ?: 0f) }
    
    var repsInput by remember { mutableIntStateOf(initial?.targetReps ?: 10) }
    
    val initialDuration = initial?.targetDurationSeconds ?: if (loggingType == LoggingType.CARDIO) 20 * 60 else 30
    var durationMin by remember { mutableIntStateOf(initialDuration / 60) }
    var durationSec by remember { mutableIntStateOf(initialDuration % 60) }
    
    val initialDistance = initial?.targetDistanceKm ?: 0f
    var distanceWhole by remember { mutableIntStateOf(initialDistance.toInt()) }
    var distanceDecimal by remember { mutableFloatStateOf(decimalTenths.minByOrNull { kotlin.math.abs(it - (initialDistance - initialDistance.toInt())) } ?: 0f) }
    
    val initialSpeed = initial?.targetSpeedKmh ?: 8f
    var speedWhole by remember { mutableIntStateOf(initialSpeed.toInt()) }
    var speedDecimal by remember { mutableFloatStateOf(decimalTenths.minByOrNull { kotlin.math.abs(it - (initialSpeed - initialSpeed.toInt())) } ?: 0f) }
    
    val initialIncline = initial?.targetIncline ?: 0f
    var inclineWhole by remember { mutableIntStateOf(initialIncline.toInt()) }
    var inclineDecimal by remember { mutableFloatStateOf(decimalTenths.minByOrNull { kotlin.math.abs(it - (initialIncline - initialIncline.toInt())) } ?: 0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSurfaceMuted.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(loggingType.displayName, style = MaterialTheme.typography.labelMedium, color = Violet)
            }

            val isHorizontal = inputStyle == "HORIZONTAL"
            var isExpanded by remember { mutableStateOf(inputStyle != "TAP_EXPAND") }
            LaunchedEffect(inputStyle) { isExpanded = inputStyle != "TAP_EXPAND" }
            
            if (!isExpanded) {
                val summaryText = when (loggingType) {
                    LoggingType.WEIGHTED_REPS -> "$sets sets × $repsInput reps × ${weightWhole + weightDecimal} kg"
                    LoggingType.BODYWEIGHT_REPS -> "$sets sets × $repsInput reps"
                    LoggingType.TIMED -> "$sets sets × ${durationMin}m ${durationSec}s"
                    LoggingType.CARDIO -> "${durationMin}m | ${distanceWhole + distanceDecimal} km | ${speedWhole + speedDecimal} km/h | ${inclineWhole + inclineDecimal}%"
                }
                OutlinedButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Target: $summaryText", color = OnSurface)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                }
            } else {
                when (loggingType) {
                    LoggingType.WEIGHTED_REPS -> {
                        if (isHorizontal) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Sets", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                        HorizontalWheelPicker(items = setsList, initialIndex = setsList.indexOf(sets).takeIf { it >= 0 } ?: 2, modifier = Modifier.fillMaxWidth(), onItemSelected = { sets = it })
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Reps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                        HorizontalWheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.fillMaxWidth(), onItemSelected = { repsInput = it })
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Weight (kg)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = weightWhole, modifier = Modifier.weight(1f), onItemSelected = { weightWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalWeightOptions, initialIndex = decimalWeightOptions.indexOf(weightDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { weightDecimal = it })
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Sets", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    WheelPicker(items = setsList, initialIndex = setsList.indexOf(sets).takeIf { it >= 0 } ?: 2, modifier = Modifier.width(60.dp), onItemSelected = { sets = it })
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Reps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    WheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.width(80.dp), onItemSelected = { repsInput = it })
                                }
                            }
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Weight (kg)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    WheelPicker(items = wholeNumbers, initialIndex = weightWhole, modifier = Modifier.width(60.dp), onItemSelected = { weightWhole = it })
                                    Text(".", style = MaterialTheme.typography.titleMedium)
                                    WheelPicker(items = decimalWeightOptions, initialIndex = decimalWeightOptions.indexOf(weightDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(60.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { weightDecimal = it })
                                }
                            }
                        }
                    }
                    LoggingType.BODYWEIGHT_REPS -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sets", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                if (isHorizontal) HorizontalWheelPicker(items = setsList, initialIndex = setsList.indexOf(sets).takeIf { it >= 0 } ?: 2, modifier = Modifier.fillMaxWidth(), onItemSelected = { sets = it })
                                else WheelPicker(items = setsList, initialIndex = setsList.indexOf(sets).takeIf { it >= 0 } ?: 2, modifier = Modifier.width(60.dp), onItemSelected = { sets = it })
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Reps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                if (isHorizontal) HorizontalWheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.fillMaxWidth(), onItemSelected = { repsInput = it })
                                else WheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.width(80.dp), onItemSelected = { repsInput = it })
                            }
                        }
                    }
                    LoggingType.TIMED -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sets", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                if (isHorizontal) HorizontalWheelPicker(items = setsList, initialIndex = setsList.indexOf(sets).takeIf { it >= 0 } ?: 2, modifier = Modifier.fillMaxWidth(), onItemSelected = { sets = it })
                                else WheelPicker(items = setsList, initialIndex = setsList.indexOf(sets).takeIf { it >= 0 } ?: 2, modifier = Modifier.width(60.dp), onItemSelected = { sets = it })
                            }
                            Column(Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Duration", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.weight(1f), itemToString = { "$it m" }, onItemSelected = { durationMin = it })
                                        Text(":", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = secsList, initialIndex = durationSec, modifier = Modifier.weight(1f), itemToString = { "%02d s".format(it) }, onItemSelected = { durationSec = it })
                                    } else {
                                        WheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.width(60.dp), itemToString = { "$it m" }, onItemSelected = { durationMin = it })
                                        Text(":", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = secsList, initialIndex = durationSec, modifier = Modifier.width(60.dp), itemToString = { "%02d s".format(it) }, onItemSelected = { durationSec = it })
                                    }
                                }
                            }
                        }
                    }
                    LoggingType.CARDIO -> {
                        if (isHorizontal) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Duration (min)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    HorizontalWheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.fillMaxWidth(), onItemSelected = { durationMin = it })
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Speed (km/h)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = speedWhole, modifier = Modifier.weight(1f), onItemSelected = { speedWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(speedDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { speedDecimal = it })
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Distance (km)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = distanceWhole, modifier = Modifier.weight(1f), onItemSelected = { distanceWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(distanceDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { distanceDecimal = it })
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Incline (%)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = inclineWhole, modifier = Modifier.weight(1f), onItemSelected = { inclineWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(inclineDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { inclineDecimal = it })
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Duration (min)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    WheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.width(80.dp), onItemSelected = { durationMin = it })
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Speed (km/h)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = speedWhole, modifier = Modifier.width(56.dp), onItemSelected = { speedWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(speedDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { speedDecimal = it })
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Distance (km)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = distanceWhole, modifier = Modifier.width(56.dp), onItemSelected = { distanceWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(distanceDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { distanceDecimal = it })
                                    }
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Incline (%)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = inclineWhole, modifier = Modifier.width(56.dp), onItemSelected = { inclineWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(inclineDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { inclineDecimal = it })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    when (loggingType) {
                        LoggingType.WEIGHTED_REPS -> onConfirm(
                            sets, repsInput, weightWhole + weightDecimal,
                            null, null, null, null,
                        )
                        LoggingType.BODYWEIGHT_REPS -> onConfirm(
                            sets, repsInput, null,
                            null, null, null, null,
                        )
                        LoggingType.TIMED -> onConfirm(
                            sets, null, null,
                            durationMin * 60 + durationSec, null, null, null,
                        )
                        LoggingType.CARDIO -> onConfirm(
                            1, null, null,
                            durationMin * 60,
                            distanceWhole + distanceDecimal,
                            speedWhole + speedDecimal,
                            inclineWhole + inclineDecimal,
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
