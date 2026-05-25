package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.haptics.WearHaptics
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.SessionStatePayload

// ---------------------------------------------------------------------------
// Pure helper (tested in WorkoutHubScreenTest)
// ---------------------------------------------------------------------------

/**
 * Returns the targetSets for the exercise currently at [session.exerciseIndex],
 * falling back to [session.totalSets] if the index is out of bounds.
 */
internal fun currentTargetSets(session: SessionStatePayload): Int =
    session.exercises.getOrNull(session.exerciseIndex)?.targetSets ?: session.totalSets

// ---------------------------------------------------------------------------
// WorkoutHubScreen composable
// ---------------------------------------------------------------------------

@Composable
fun WorkoutHubScreen(
    uiState: WearUiState,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val session = uiState.session
    val dayState = uiState.dayState
    val recentWorkouts = dayState?.recentWorkouts.orEmpty()
    var recentIndex by remember(recentWorkouts) { mutableIntStateOf(0) }
    var browsingRecent by remember { mutableStateOf(false) }
    LaunchedEffect(recentWorkouts) {
        browsingRecent = false
        if (recentIndex >= recentWorkouts.size) recentIndex = 0
    }
    val selectedRecent = recentWorkouts.getOrNull(recentIndex)
    val workoutTitle = when {
        session?.exerciseName?.isNotBlank() == true -> session.exerciseName
        browsingRecent && selectedRecent != null -> selectedRecent.dayName
        else -> dayState?.workoutName ?: "No Workout"
    }
    val exerciseCount = session?.exercises?.size?.takeIf { it > 0 } ?: dayState?.exerciseCount
    val readinessPill = dayState?.let { "${it.readinessScore} · ${readinessLabel(it.readinessScore)}" }
    val subtitle = when {
        browsingRecent && selectedRecent != null ->
            "${selectedRecent.durationMinutes} min · recent"
        else -> null
    }
    val headerLabel = if (browsingRecent) "RECENT" else "TODAY"

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearColors.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                if (session == null && recentWorkouts.isNotEmpty()) {
                    if (!browsingRecent) {
                        browsingRecent = true
                        recentIndex = 0
                    } else {
                        val delta = if (event.verticalScrollPixels > 0f) 1 else -1
                        recentIndex = (recentIndex + delta).mod(recentWorkouts.size)
                    }
                }
                WearHaptics.bezelStep(context)
                true
            }
            .pointerInput(onStartWorkout) {
                detectTapGestures(onTap = { onStartWorkout() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = headerLabel,
                color = WearColors.TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = workoutTitle,
                color = WearColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                subtitle != null -> Text(
                    text = subtitle,
                    color = WearColors.TextMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
                exerciseCount != null && exerciseCount > 0 -> Text(
                    text = "$exerciseCount exercises",
                    color = WearColors.TextMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (readinessPill != null) {
                Text(
                    text = readinessPill,
                    color = WearColors.TextSecondary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(WearColors.Active.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (session != null) "TAP = CONTINUE" else "TAP = START",
                    color = WearColors.Active,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (uiState.offline) {
                Text(
                    text = "Offline",
                    color = WearColors.Warning,
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                )
            }
            val queued = uiState.queuedCount
            if (queued > 0) {
                Text(
                    text = "$queued queued",
                    color = WearColors.TextMuted,
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
