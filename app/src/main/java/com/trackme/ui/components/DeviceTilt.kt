package com.trackme.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

data class Tilt(val pitch: Float = 0f, val roll: Float = 0f)

private object DeviceTiltObserver {
    private const val MIN_UPDATE_INTERVAL_MS = 66L // ~15 fps is plenty for subtle parallax.
    private const val MIN_TILT_DELTA = 0.0025f

    val tilt = MutableStateFlow(Tilt())

    private var activeCollectors = 0
    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    fun start(context: Context) = synchronized(this) {
        activeCollectors++
        if (activeCollectors == 1) {
            register(context.applicationContext)
        }
    }

    fun stop() = synchronized(this) {
        activeCollectors = (activeCollectors - 1).coerceAtLeast(0)
        if (activeCollectors == 0) {
            sensorManager?.unregisterListener(listener)
            listener = null
            sensorManager = null
            tilt.value = Tilt()
        }
    }

    private fun register(context: Context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accelerometer == null || magnetometer == null) return

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotation = FloatArray(9)
        val inclination = FloatArray(9)
        val orientation = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var lastUpdateAt = 0L

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity, endIndex = gravity.size)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        event.values.copyInto(geomagnetic, endIndex = geomagnetic.size)
                        hasGeomagnetic = true
                    }
                }

                if (!hasGravity || !hasGeomagnetic) return

                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastUpdateAt < MIN_UPDATE_INTERVAL_MS) return

                if (SensorManager.getRotationMatrix(rotation, inclination, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotation, orientation)
                    val current = tilt.value
                    val newPitch = current.pitch * 0.85f + orientation[1] * 0.15f
                    val newRoll = current.roll * 0.85f + orientation[2] * 0.15f

                    if (abs(newPitch - current.pitch) >= MIN_TILT_DELTA ||
                        abs(newRoll - current.roll) >= MIN_TILT_DELTA
                    ) {
                        lastUpdateAt = now
                        tilt.value = Tilt(newPitch, newRoll)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager = manager
        listener = sensorListener
        manager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        manager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }
}

@Composable
fun rememberDeviceTilt(): State<Tilt> {
    val context = LocalContext.current.applicationContext
    DisposableEffect(context) {
        DeviceTiltObserver.start(context)
        onDispose { DeviceTiltObserver.stop() }
    }

    return DeviceTiltObserver.tilt.collectAsState()
}

fun androidx.compose.ui.Modifier.parallaxTilt(tilt: Tilt, intensity: Float = 10f): androidx.compose.ui.Modifier {
    return this.graphicsLayer {
        rotationX = tilt.pitch * intensity
        rotationY = -tilt.roll * intensity // Inverted for natural feel
        cameraDistance = 12f * density
    }
}

fun androidx.compose.ui.Modifier.parallaxTilt(
    tiltState: State<Tilt>,
    intensity: Float = 10f,
): androidx.compose.ui.Modifier {
    return this.graphicsLayer {
        val tilt = tiltState.value
        rotationX = tilt.pitch * intensity
        rotationY = -tilt.roll * intensity
        cameraDistance = 12f * density
    }
}
