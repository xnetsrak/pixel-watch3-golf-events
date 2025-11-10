package com.example.wear.golfcheck

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.wear.golfcheck.data.GolfShotEvent
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
    private var golfService: GolfExerciseServiceImpl? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as GolfExerciseServiceImpl.LocalBinder
            golfService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var service by remember { mutableStateOf(golfService) }
            MainScreen(
                golfShotEventFlow = service?.golfShotEvents,
                onStartClick = { startGolfService() },
                onStopClick = { stopGolfService() }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, GolfExerciseServiceImpl::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
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
fun MainScreen(
    golfShotEventFlow: StateFlow<GolfShotEvent?>?,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val golfShotEvent by golfShotEventFlow?.collectAsState() ?: remember { mutableStateOf(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = golfShotEvent?.swingType?.name ?: "No shot detected"
        )
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
    MainScreen(null, {}, {})
}
