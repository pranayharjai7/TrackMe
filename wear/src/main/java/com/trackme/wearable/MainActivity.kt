package com.trackme.wearable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.trackme.wearable.health.HealthConnectManager
import com.trackme.wearable.ui.TrackMeWearApp
import com.trackme.wearable.viewmodel.WearSessionViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WearSessionViewModel by viewModels()

    private val healthConnectPermissions = setOf(
        "android.permission.health.WRITE_EXERCISE",
        "android.permission.health.WRITE_HEART_RATE",
        "android.permission.health.WRITE_TOTAL_CALORIES_BURNED",
    )

    private val healthPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // permissions resolved — no further action needed in this version
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        requestHealthConnectPermissionsIfNeeded()
        setContent {
            TrackMeWearApp(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        viewModel.flushHealthMetrics()
        super.onStop()
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = buildList {
            add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 2001)
        }
    }

    private fun requestHealthConnectPermissionsIfNeeded() {
        val manager = HealthConnectManager(this)
        if (!manager.isAvailable()) return
        healthPermissionLauncher.launch(healthConnectPermissions.toTypedArray())
    }
}
