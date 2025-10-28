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


class MainActivity : ComponentActivity() {

    private lateinit var exerciseClient: ExerciseClient
    private val tag = "GolfCheckApp"

    // We need a state to update the UI from our callback
    private val statusTextState = mutableStateOf("Initializing...")
    private var isTracking = false

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

    @Composable
    fun GolfTestScreen(
        client: ExerciseClient,
        status: MutableState<String>
    ) {
        val context = LocalContext.current
        var isTracking by remember { mutableStateOf(this.isTracking) }
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

                                // Configure the exercise to listen for golf shots
                                val config = ExerciseConfig.builder(ExerciseType.GOLF)
                                    .setDataTypes(setOf(DataType.HEART_RATE_BPM)) // Must have at least one data type
                                    .setExerciseEvents(setOf(ExerciseEvent.GOLF_SHOT)) // The important part!
                                    .build()

                                try {
                                    // Register our callback
                                    client.setUpdateCallback(exerciseCallback)
                                    // Start the exercise
                                    client.startExerciseAsync(config).await()
                                } catch (e: Exception) {
                                    status.value = "Error starting: ${e.message}"
                                    isTracking = false
                                }
                            }
                        }
                    }
                },
                enabled = status.value.contains("AVAILABLE") || isTracking
            ) {
                Text(if (isTracking) "Stop Session" else "Start Golf Session")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            // Clean up if the app is closed while tracking
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    exerciseClient.endExerciseAsync().await()
                    exerciseClient.clearUpdateCallbackAsync(exerciseCallback).await()
                } catch (e: Exception) {
                    Log.e(tag, "Error cleaning up in onDestroy", e)
                }
            }
        }
    }
}
