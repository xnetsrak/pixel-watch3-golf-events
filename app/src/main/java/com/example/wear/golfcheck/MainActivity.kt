package com.example.wear.golfcheck

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onStartClick = { startGolfService() },
                onStopClick = { stopGolfService() }
            )
        }
    }

    private fun startGolfService() {
        val intent = Intent(this, GolfExerciseServiceImpl::class.java)
        startForegroundService(intent)
    }

    private fun stopGolfService() {
        val intent = Intent(this, GolfExerciseServiceImpl::class.java)
        stopService(intent)
    }
}

@Composable
fun MainScreen(onStartClick: () -> Unit, onStopClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStartClick) {
            Text("Start")
        }
        Button(onClick = onStopClick) {
            Text("Stop")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen({}, {})
}
