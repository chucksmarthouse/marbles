package com.aboratech.marbles

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Exposes device tilt as a gravity vector in the sensor's own x/y axes
 * (m/s^2, ~9.8 magnitude when flat). Prefers TYPE_GRAVITY (fused,
 * low-noise) and falls back to the raw accelerometer on devices that
 * lack it.
 */
class TiltSensor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile var x: Float = 0f
        private set

    @Volatile var y: Float = 0f
        private set

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        x = event.values[0]
        y = event.values[1]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
