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
import kotlinx.coroutines.cancelAndJoin

class GolfExerciseServiceImpl(context: Context) : GolfExerciseService(), SensorEventListener {

    companion object {
        // Threshold values for golf shot detection
        private const val FULL_SWING_ACCEL_THRESHOLD = 25.0
        private const val PARTIAL_SWING_ACCEL_THRESHOLD = 15.0
        private const val PUTT_GYRO_THRESHOLD = 4.0
        private const val PUTT_ACCEL_MAX = 10.0
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    // Debouncing: Track last shot detection time to prevent duplicate events
    private var lastShotDetectionTime = 0L
    private val shotDetectionCooldownMs = 1500L // 1.5 seconds cooldown between shots
    private var lastAccelerometerData: FloatArray? = null
    private var lastGyroscopeData: FloatArray? = null

    private val sensorDataLock = Any()
  
    fun start(): Boolean {
        val accelRegistered = accelerometer?.let { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        
        val gyroRegistered = gyroscope?.let { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        
        return accelRegistered && gyroRegistered
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    suspend fun awaitStop() {
        serviceJob.cancelAndJoin()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this example
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val accelData: FloatArray
                val gyroData: FloatArray
                synchronized(sensorDataLock) {
                    lastAccelerometerData = event.values.clone()
                    accelData = lastAccelerometerData!!.clone()
                    gyroData = lastGyroscopeData?.clone() ?: FloatArray(3)
                }
                detectSwing(accelData, gyroData)
            }
            Sensor.TYPE_GYROSCOPE -> {
                synchronized(sensorDataLock) {
                    lastGyroscopeData = event.values.clone()
                }
            }
        }
    }

    private fun detectSwing(acceleration: FloatArray, gyroscope: FloatArray) {
        // Check if we're still in cooldown period from last shot detection
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastShotDetectionTime < shotDetectionCooldownMs) {
            return // Still in cooldown, ignore this event
        }
        
        val accelMagnitude = Math.sqrt((acceleration[0] * acceleration[0] + acceleration[1] * acceleration[1] + acceleration[2] * acceleration[2]).toDouble())
        val gyroMagnitude = Math.sqrt((gyroscope[0] * gyroscope[0] + gyroscope[1] * gyroscope[1] + gyroscope[2] * gyroscope[2]).toDouble())

        // This is a very simplistic example. A real implementation would need a much more
        // sophisticated algorithm, likely involving machine learning or more complex signal processing.
        if (accelMagnitude > FULL_SWING_ACCEL_THRESHOLD) { // High acceleration -> Full swing
            lastShotDetectionTime = currentTime
            markGolfShotEvent(GolfShotEvent.GolfShotSwingType.FULL)
        } else if (accelMagnitude > PARTIAL_SWING_ACCEL_THRESHOLD) { // Medium acceleration -> Partial swing
            lastShotDetectionTime = currentTime
            markGolfShotEvent(GolfShotEvent.GolfShotSwingType.PARTIAL)
        } else if (gyroMagnitude > PUTT_GYRO_THRESHOLD && accelMagnitude < PUTT_ACCEL_MAX) { // High rotation, low acceleration -> Putt
            lastShotDetectionTime = currentTime
            markGolfShotEvent(GolfShotEvent.GolfShotSwingType.PUTT)
        } else {
            // Potentially UNKNOWN, but this could be very noisy.
            // For this example, we'll avoid marking UNKNOWN to prevent spamming events.
        }
    }
}
