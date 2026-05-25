package com.trackme.wearable.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.designsystem.WearIconButton
import com.trackme.wearable.haptics.WearHaptics
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Pure helper (internal for testability)
// ---------------------------------------------------------------------------

internal fun volumeLabel(current: Int, max: Int): String = "Vol: $current/$max"

// ---------------------------------------------------------------------------
// Media key helper
// ---------------------------------------------------------------------------

internal fun sendMediaKey(context: Context, keyCode: Int) {
    val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }
    context.sendBroadcast(downIntent)
    val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
    context.sendBroadcast(upIntent)
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

@Composable
fun MediaPage(modifier: Modifier = Modifier) {
    val context      = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val maxVolume    = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volumeLevel  by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    val isPlaying    = remember { audioManager.isMusicActive() }

    val focusRequester = remember { FocusRequester() }
    val rotaryAccumulator = remember { floatArrayOf(0f) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onRotaryScrollEvent { event ->
                rotaryAccumulator[0] += event.verticalScrollPixels
                val threshold = 16f
                while (abs(rotaryAccumulator[0]) >= threshold) {
                    val direction = if (rotaryAccumulator[0] > 0)
                        AudioManager.ADJUST_LOWER
                    else
                        AudioManager.ADJUST_RAISE
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        direction,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    WearHaptics.bezelStep(context)
                    rotaryAccumulator[0] += if (rotaryAccumulator[0] > 0) -threshold else threshold
                }
                true
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Top label
        Text(
            text = "MEDIA",
            color = WearColors.TextMuted,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        // Playback controls row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WearIconButton(
                label = "⏮",
                onClick = { sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
            )
            WearIconButton(
                label = if (isPlaying) "⏸" else "▶",
                onClick = { sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
            )
            WearIconButton(
                label = "⏭",
                onClick = { sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Volume display
        Text(
            text = volumeLabel(current = volumeLevel, max = maxVolume),
            color = WearColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Rotate bezel to adjust",
            color = WearColors.TextMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}
