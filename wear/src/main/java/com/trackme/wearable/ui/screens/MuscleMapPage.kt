package com.trackme.wearable.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors

// ---------------------------------------------------------------------------
// Pure helper (internal for testability)
// ---------------------------------------------------------------------------

internal fun muscleColor(muscle: String?): Color = when (muscle?.uppercase()) {
    "CHEST", "TRICEPS", "SHOULDERS" -> WearColors.Signal
    "BACK", "BICEPS", "TRAPS"       -> WearColors.Rest
    "LEGS", "QUADS", "HAMSTRINGS",
    "GLUTES", "CALVES"              -> WearColors.Active
    "CORE", "ABS"                   -> WearColors.Summary
    "CARDIO"                        -> WearColors.Warning
    else                            -> WearColors.TextSecondary
}

// ---------------------------------------------------------------------------
// Which body region to highlight
// ---------------------------------------------------------------------------

private enum class BodyRegion { UPPER, MIDDLE, LOWER, NONE }

private fun muscleRegion(muscle: String?): BodyRegion = when (muscle?.uppercase()) {
    "CHEST", "SHOULDERS", "TRICEPS" -> BodyRegion.UPPER
    "BACK", "TRAPS", "BICEPS"       -> BodyRegion.UPPER
    "CORE", "ABS"                   -> BodyRegion.MIDDLE
    "LEGS", "QUADS", "HAMSTRINGS",
    "GLUTES", "CALVES"              -> BodyRegion.LOWER
    else                            -> BodyRegion.NONE
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

@Composable
fun MuscleMapPage(muscle: String?, exerciseName: String?, modifier: Modifier = Modifier) {
    val accent = muscleColor(muscle)
    val region = muscleRegion(muscle)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Top label
        Text(
            text = "MUSCLE",
            color = WearColors.TextMuted,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        // Muscle group name
        Text(
            text = muscle?.uppercase() ?: "–",
            color = accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        // Abstract body silhouette
        BodySilhouette(
            activeRegion = region,
            activeColor = accent,
            modifier = Modifier.size(width = 60.dp, height = 90.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Exercise name
        if (exerciseName != null) {
            Text(
                text = exerciseName,
                color = WearColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BodySilhouette(
    activeRegion: BodyRegion,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val inactive = WearColors.Elevated

        // Head (circle) — top 18% of height
        val headR  = w * 0.20f
        val headCx = w / 2f
        val headCy = headR + h * 0.01f
        drawCircle(
            color  = inactive,
            radius = headR,
            center = Offset(headCx, headCy),
        )

        // Neck — tiny rect between head and torso
        val neckTop   = headCy + headR
        val neckH     = h * 0.04f
        val neckW     = w * 0.18f
        drawRoundRect(
            color        = inactive,
            topLeft      = Offset(w / 2f - neckW / 2f, neckTop),
            size         = Size(neckW, neckH),
            cornerRadius = CornerRadius(4f),
        )

        // Torso — split into upper (chest/back/shoulders) and middle (core/abs)
        val torsoTop  = neckTop + neckH
        val upperH    = h * 0.28f
        val middleH   = h * 0.14f
        val torsoW    = w * 0.56f
        val torsoLeft = w / 2f - torsoW / 2f

        drawRoundRect(
            color        = if (activeRegion == BodyRegion.UPPER) activeColor else inactive,
            topLeft      = Offset(torsoLeft, torsoTop),
            size         = Size(torsoW, upperH),
            cornerRadius = CornerRadius(6f),
        )
        drawRoundRect(
            color        = if (activeRegion == BodyRegion.MIDDLE) activeColor else inactive,
            topLeft      = Offset(torsoLeft, torsoTop + upperH + 2f),
            size         = Size(torsoW, middleH),
            cornerRadius = CornerRadius(4f),
        )

        // Legs (two rectangles)
        val legsTop  = torsoTop + upperH + middleH + 4f
        val legW     = torsoW * 0.44f
        val legH     = h - legsTop - h * 0.01f
        val legColor = if (activeRegion == BodyRegion.LOWER) activeColor else inactive

        // Left leg
        drawRoundRect(
            color        = legColor,
            topLeft      = Offset(torsoLeft, legsTop),
            size         = Size(legW, legH),
            cornerRadius = CornerRadius(5f),
        )
        // Right leg
        drawRoundRect(
            color        = legColor,
            topLeft      = Offset(torsoLeft + torsoW - legW, legsTop),
            size         = Size(legW, legH),
            cornerRadius = CornerRadius(5f),
        )
    }
}
