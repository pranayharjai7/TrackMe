package com.trackme.wearable.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.sign

/**
 * Rotary bezel input with acceleration and spam protection.
 */
class BezelInputController(
    private val notchThresholdPx: Float = 18f,
    private val spamWindowMs: Long = 40L,
) {
    private var accumulatedPx by mutableFloatStateOf(0f)
    private var lastNotchAt by mutableLongStateOf(0L)
    var focusFieldIndex by mutableIntStateOf(0)

    fun onScroll(deltaPx: Float, onNotch: (direction: Int, acceleratedSteps: Int) -> Unit): Boolean {
        if (deltaPx == 0f) return false
        accumulatedPx += deltaPx
        val direction = sign(accumulatedPx).toInt()
        if (abs(accumulatedPx) < notchThresholdPx) return true

        val now = System.currentTimeMillis()
        if (now - lastNotchAt < spamWindowMs) {
            accumulatedPx = 0f
            return true
        }
        lastNotchAt = now

        val steps = (abs(accumulatedPx) / notchThresholdPx).toInt().coerceAtLeast(1)
        val accelerated = when {
            steps >= 4 -> steps
            steps >= 2 -> 2
            else -> 1
        }
        accumulatedPx = 0f
        onNotch(direction, accelerated * direction)
        return true
    }

    fun reset() {
        accumulatedPx = 0f
    }
}
