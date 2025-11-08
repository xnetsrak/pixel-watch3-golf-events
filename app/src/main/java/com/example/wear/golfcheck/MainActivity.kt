package com.example.wear.golfcheck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.wear.golfcheck.data.GolfShotEvent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var golfExerciseService: GolfExerciseServiceImpl
    private val tag = "GolfCheckApp"

    private val statusTextState = mutableStateOf("Initializing...")
    private val isTrackingState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        golfExerciseService = GolfExerciseServiceImpl(this)
        setContent {
            GolfTestScreen(statusTextState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTrackingState.value) {
            lifecycleScope.launch {
                golfExerciseService.stop()
            }
        }
    }

    @Composable
    fun GolfTestScreen(
        status: MutableState<String>
    ) {
        val context = LocalContext.current
        var isTracking by isTrackingState
        val coroutineScope = rememberCoroutineScope()

        val permissions = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        )

        var hasPermissions by remember {
            mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            hasPermissions = permissionsMap.values.all { it }
            if (!hasPermissions) {
                status.value = "Permissions denied."
            }
        }

        LaunchedEffect(Unit) {
            golfExerciseService.golfShotEvents.collect {
                it?.let {
                    val swingType = it.swingType.toString()
                    Log.i(tag, "GOLF SHOT REGISTERED! Type: $swingType")
                    status.value = "GOLF SHOT! ($swingType)"
                }
            }
        }

        LaunchedEffect(key1 = Unit) {
            status.value = "Ready to start."
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = status.value,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isTracking) {
                        coroutineScope.launch {
                            golfExerciseService.stop()
                            status.value = "Session stopped."
                            isTracking = false
                        }
                    } else {
                        if (!hasPermissions) {
                            status.value = "Requesting permissions..."
                            permissionLauncher.launch(permissions)
                        } else {
                            status.value = """
                                Starting golf session...
                                Swing the club!
                            """.trimIndent()
                            val sensorsAvailable = golfExerciseService.start()
                            if (sensorsAvailable) {
                                isTracking = true
                            } else {
                                status.value = "Error: Accelerometer or gyroscope unavailable on this device."
                                isTracking = false
                            }
                        }
                    }
                }
            ) {
                Text(if (isTracking) "Stop" else "Start")
            }
        }
    }
}
