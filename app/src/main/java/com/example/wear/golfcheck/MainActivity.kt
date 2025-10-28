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
import androidx.compose.ui.graphics.Color
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
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.GolfShotEvent
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private lateinit var exerciseClient: ExerciseClient
    private val tag = "GolfCheckApp"

    // Shared observable state for tracking status that both Activity logic and Composables read/write
    private val statusTextState = mutableStateOf("Initializing...")
    private val isTrackingState = mutableStateOf(false)

    // Our callback to listen for exercise updates (and golf shots!)
    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onAvailabilityChanged(dataType: DataType, availability: Availability) {
            Log.d(tag, "Availability changed for $dataType: $availability")
        }

        override fun onExerciseUpdateReceived(update: androidx.health.services.client.data.ExerciseUpdate) {
            // We receive continuous updates here (e.g. heart rate, time)
        }

        override fun onLapSummaryReceived(lapSummary: androidx.health.services.client.data.ExerciseLapSummary) {
            // Not relevant for golf
        }

        override fun onExerciseEventReceived(event: ExerciseEvent) {
            // THIS IS THE IMPORTANT PART!
            if (event is GolfShotEvent) {
                val swingType = event.swingType.name
                Log.i(tag, "GOLF SHOT REGISTERED! Type: $swingType")

                // Update the UI text. Must happen on the Main thread.
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
        // Use lifecycleScope to ensure we use the latest isTrackingState value and do cleanup
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
        // Use the single shared MutableState so composable and Activity logic are consistent
        var isTracking by isTrackingState
        val coroutineScope = rememberCoroutineScope()

        // Check permissions
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

        // STEP 1: Check if the capability is available when the app starts
        LaunchedEffect(key1 = Unit) {
            status.value = "Checking features..."
            try {
                val capabilities = client.getCapabilitiesAsync().await()
                val golfSupported = capabilities.supportedExerciseTypes.contains(ExerciseType.GOLF)
                val golfShotEventSupported = capabilities.supportedExerciseEvents.contains(ExerciseEvent.GOLF_SHOT)

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

        // UI
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
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isTracking) {
                        // Stop session
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
                        // Start session
                        if (!hasPermission) {
                            status.value = "Requesting permission..."
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            coroutineScope.launch {
                                status.value = "Starting golf session...\nSwing the club!"
                                isTracking = true

                                // Query supported data types for GOLF exercise and choose a safe fallback
                                try {
                                    val capabilities = client.getCapabilitiesAsync().await()
                                    val supportedDataTypes = capabilities.getSupportedDataTypes(ExerciseType.GOLF)

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

                                    // Configure the exercise to listen for golf shots with valid data types
                                    val config = ExerciseConfig.builder(ExerciseType.GOLF)
                                        .setDataTypes(dataTypesToRequest) // at least one data type guaranteed above
                                        .build()

                                    client.setUpdateCallbackAsync(exerciseCallback).await()
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