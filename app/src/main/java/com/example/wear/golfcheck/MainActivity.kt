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
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseEvent
import androidx.health.services.client.data.ExerciseEventType
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.GolfShotEvent
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var exerciseClient: ExerciseClient
    private val tag = "GolfCheckApp"

    private val statusTextState = mutableStateOf("Initializing...")
    private val isTrackingState = mutableStateOf(false)

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() {
            Log.d(tag, "Callback registered")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(tag, "Callback registration failed", throwable)
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            Log.d(tag, "Availability changed for $dataType: $availability")
        }

        override fun onExerciseUpdateReceived(update: androidx.health.services.client.data.ExerciseUpdate) {
        }

        override fun onLapSummaryReceived(lapSummary: androidx.health.services.client.data.ExerciseLapSummary) {
        }

        override fun onExerciseEventReceived(event: ExerciseEvent) {
            if (event is GolfShotEvent) {
                val swingType = event.swingType.toString()
                Log.i(tag, "GOLF SHOT REGISTERED! Type: $swingType")
                CoroutineScope(Dispatchers.Main).launch {
                    statusTextState.value = "GOLF SHOT! ($swingType)"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exerciseClient = HealthServices.getClient(this).exerciseClient
        setContent {
            GolfTestScreen(exerciseClient, statusTextState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTrackingState.value) {
            lifecycleScope.launch {
                try {
                    exerciseClient.endExerciseAsync().await()
                    exerciseClient.clearUpdateCallbackAsync(exerciseCallback).await()
                    Log.i(tag, "Stopped exercise in onDestroy.")
                } catch (e: Exception) {
                    Log.w(tag, "Error stopping exercise in onDestroy: ${e.message}")
                }
            }
        }
    }

    @Composable
    fun GolfTestScreen(
        client: ExerciseClient,
        status: MutableState<String>
    ) {
        val context = LocalContext.current
        var isTracking by isTrackingState
        val coroutineScope = rememberCoroutineScope()

        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasPermission = isGranted
            if (!isGranted) {
                status.value = "Permission denied."
            }
        }

        LaunchedEffect(key1 = Unit) {
            status.value = "Checking features..."
            try {
                val capabilities = client.getCapabilitiesAsync().await()
                val golfSupported = capabilities.supportedExerciseTypes.contains(ExerciseType.GOLF)

                val golfShotEventSupported = golfSupported &&
                    capabilities.getExerciseTypeCapabilities(ExerciseType.GOLF)
                        ?.supportedExerciseEvents
                        ?.contains(ExerciseEventType.GOLF_SHOT_EVENT) == true

                if (golfSupported && golfShotEventSupported) {
                    status.value = "Golf API: AVAILABLE\nReady to start."
                    Log.i(tag, "GolfShotEvent is supported on this watch.")
                } else {
                    status.value = "Golf API: NOT AVAILABLE\nGolf: $golfSupported\nGolfShotEvent: $golfShotEventSupported"
                    Log.w(tag, "GolfShotEvent is NOT supported.")
                }
            } catch (e: Exception) {
                status.value = "Error: ${e.message}"
                Log.e(tag, "Error checking features", e)
            }
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
                            try {
                                client.endExerciseAsync().await()
                                client.clearUpdateCallbackAsync(exerciseCallback).await()
                                status.value = "Session stopped."
                                isTracking = false
                            } catch (e: Exception) {
                                status.value = "Error stopping: ${e.message}"
                            }
                        }
                    } else {
                        if (!hasPermission) {
                            status.value = "Requesting permission..."
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            coroutineScope.launch {
                                status.value = "Starting golf session...\nSwing the club!"
                                isTracking = true

                                try {
                                    val capabilities = client.getCapabilitiesAsync().await()
                                    if (ExerciseType.GOLF !in capabilities.supportedExerciseTypes) {
                                        status.value = "Golf is not supported."
                                        isTracking = false
                                        return@launch
                                    }
                                    val supportedDataTypes = capabilities.getExerciseTypeCapabilities(ExerciseType.GOLF).supportedDataTypes

                                    val dataTypesToRequest = when {
                                        DataType.HEART_RATE_BPM in supportedDataTypes -> setOf(DataType.HEART_RATE_BPM)
                                        supportedDataTypes.isNotEmpty() -> setOf(supportedDataTypes.first())
                                        else -> emptySet()
                                    }

                                    if (dataTypesToRequest.isEmpty()) {
                                        status.value = "No supported data types available for golf session."
                                        isTracking = false
                                        return@launch
                                    }

                                    val config = ExerciseConfig.builder(ExerciseType.GOLF)
                                        .setDataTypes(dataTypesToRequest)
                                        .build()

                                    client.setUpdateCallback(exerciseCallback)
                                    client.startExerciseAsync(config).await()
                                    status.value = "Golf session started. Waiting for shots..."
                                } catch (e: Exception) {
                                    status.value = "Error starting: ${e.message}"
                                    isTracking = false
                                }
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
