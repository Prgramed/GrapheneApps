package com.prgramed.eprayer.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class CompassSensorManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun headingUpdates(): Flow<Float> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null || magnetometer == null) {
            close()
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMagnetic = false
        var smoothedHeading = Float.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPassFilter(event.values, gravity, SENSOR_ALPHA)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPassFilter(event.values, geomagnetic, SENSOR_ALPHA)
                        hasMagnetic = true
                    }
                }

                if (hasGravity && hasMagnetic) {
                    val rotationMatrix = FloatArray(9)
                    val inclinationMatrix = FloatArray(9)
                    if (SensorManager.getRotationMatrix(
                            rotationMatrix, inclinationMatrix, gravity, geomagnetic,
                        )
                    ) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val rawDegrees =
                            ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()

                        smoothedHeading = if (smoothedHeading.isNaN()) {
                            rawDegrees
                        } else {
                            circularLerp(smoothedHeading, rawDegrees, HEADING_ALPHA)
                        }

                        trySend(smoothedHeading)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener, accelerometer, SensorManager.SENSOR_DELAY_UI,
        )
        sensorManager.registerListener(
            listener, magnetometer, SensorManager.SENSOR_DELAY_UI,
        )

        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private fun lowPassFilter(input: FloatArray, output: FloatArray, alpha: Float) {
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }

    private fun circularLerp(from: Float, to: Float, alpha: Float): Float {
        val fromRad = Math.toRadians(from.toDouble())
        val toRad = Math.toRadians(to.toDouble())
        val sinAvg = sin(fromRad) * (1 - alpha) + sin(toRad) * alpha
        val cosAvg = cos(fromRad) * (1 - alpha) + cos(toRad) * alpha
        return ((Math.toDegrees(atan2(sinAvg, cosAvg)) + 360) % 360).toFloat()
    }

    companion object {
        private const val SENSOR_ALPHA = 0.1f
        private const val HEADING_ALPHA = 0.15f
    }
}
