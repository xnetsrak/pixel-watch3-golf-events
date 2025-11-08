package com.example.wear.golfcheck

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.wear.golfcheck.data.GolfShotEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GolfExerciseServiceImpl(context: Context) : GolfExerciseService(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var lastAccelerometerData: FloatArray? = null
    private var lastGyroscopeData: FloatArray? = null

    fun start() {
        // Check that both sensors are available before starting
        if (accelerometer == null || gyroscope == null) {
            throw IllegalStateException("Golf shot detection requires both accelerometer and gyroscope sensors")
        }
        
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    suspend fun stop() {
        sensorManager.unregisterListener(this)
        serviceJob.cancelAndJoin()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this example
    }

    override fun onSensorChanged(event: SensorEvent?) {
        serviceScope.launch {
            when (event?.sensor?.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    lastAccelerometerData = event.values.clone()
                    // Only detect swing if both sensors have provided data
                    lastGyroscopeData?.let { gyro ->
                        lastAccelerometerData?.let { accel ->
                            detectSwing(accel, gyro)
                        }
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroscopeData = event.values.clone()
                }
            }
        }
    }

    private fun detectSwing(acceleration: FloatArray, gyroscope: FloatArray) {
        val accelMagnitude = Math.sqrt((acceleration[0] * acceleration[0] + acceleration[1] * acceleration[1] + acceleration[2] * acceleration[2]).toDouble())
        val gyroMagnitude = Math.sqrt((gyroscope[0] * gyroscope[0] + gyroscope[1] * gyroscope[1] + gyroscope[2] * gyroscope[2]).toDouble())

        // This is a very simplistic example. A real implementation would need a much more
        // sophisticated algorithm, likely involving machine learning or more complex signal processing.
        if (accelMagnitude > 25) { // High acceleration -> Full swing
            markGolfShotEvent(GolfShotEvent.GolfShotSwingType.FULL)
        } else if (accelMagnitude > 15) { // Medium acceleration -> Partial swing
            markGolfShotEvent(GolfShotEvent.GolfShotSwingType.PARTIAL)
        } else if (gyroMagnitude > 4 && accelMagnitude < 10) { // High rotation, low acceleration -> Putt
            markGolfShotEvent(GolfShotEvent.GolfShotSwingType.PUTT)
        } else {
            // Potentially UNKNOWN, but this could be very noisy.
            // For this example, we'll avoid marking UNKNOWN to prevent spamming events.
        }
    }
}
