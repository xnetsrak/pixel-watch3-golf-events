package com.example.wear.golfcheck

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import com.example.wear.golfcheck.data.GolfShotEvent
import com.example.wear.golfcheck.utils.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

class GolfExerciseServiceImpl : Service(), SensorEventListener {

    private lateinit var vibrationHelper: VibrationHelper
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): GolfExerciseServiceImpl = this@GolfExerciseServiceImpl
    }

    private val _golfShotEventFlow = MutableStateFlow<GolfShotEvent?>(null)
    val golfShotEvents: StateFlow<GolfShotEvent?> = _golfShotEventFlow

    companion object {
        // Threshold values for golf shot detection
        private const val FULL_SWING_ACCEL_THRESHOLD = 25.0
        private const val PARTIAL_SWING_ACCEL_THRESHOLD = 15.0
        private const val PUTT_GYRO_THRESHOLD = 4.0
        private const val PUTT_ACCEL_MAX = 10.0
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "GolfExerciseServiceChannel"
    }

    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val accelerometer: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val gyroscope: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }

    private val serviceJob = Job()
    
    // Debouncing: Track last shot detection time to prevent duplicate events
    private var lastShotDetectionTime = 0L
    private val shotDetectionCooldownMs = 1500L // 1.5 seconds cooldown between shots
    private var lastAccelerometerData: FloatArray? = null
    private var lastGyroscopeData: FloatArray? = null

    private val sensorDataLock = Any()
  
    override fun onCreate() {
        super.onCreate()
        vibrationHelper = VibrationHelper(this)
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Golf Exercise Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Golf Swing Detection Active")
            .setContentText("Detecting your swings in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    /**
     * Calculate the magnitude of a 3D vector.
     * @param values Array containing x, y, z components
     * @return Magnitude of the vector
     */
    private fun calculateMagnitude(values: FloatArray): Double {
        return sqrt((values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble())
    }

    private fun start() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stop() {
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
        
        val accelMagnitude = calculateMagnitude(acceleration)
        val gyroMagnitude = calculateMagnitude(gyroscope)

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
        }
    }

    private fun markGolfShotEvent(swingType: GolfShotEvent.GolfShotSwingType) {
        val golfShotEvent = GolfShotEvent(swingType)
        _golfShotEventFlow.value = golfShotEvent
        vibrationHelper.vibrate(500)
    }
}
