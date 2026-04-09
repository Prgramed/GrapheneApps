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
import kotlin.math.sqrt

data class CompassReading(
    val heading: Float,
    val needsCalibration: Boolean,
)

@Singleton
class CompassSensorManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun headingUpdates(): Flow<CompassReading> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val useRotationVector = rotationVectorSensor != null

        val accelerometer = if (!useRotationVector) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val magnetometer = if (!useRotationVector) sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

        if (!useRotationVector && (accelerometer == null || magnetometer == null)) {
            close(IllegalStateException("Compass sensor not available on this device"))
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMagnetic = false
        var smoothedHeading = Float.NaN
        var needsCalibration = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotationMatrix: FloatArray

                if (useRotationVector && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                } else {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            lowPassFilter(event.values, gravity, SENSOR_ALPHA)
                            hasGravity = true
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            lowPassFilter(event.values, geomagnetic, SENSOR_ALPHA)
                            hasMagnetic = true
                        }
                        else -> return
                    }
                    if (!hasGravity || !hasMagnetic) return

                    rotationMatrix = FloatArray(9)
                    val inclinationMatrix = FloatArray(9)
                    if (!SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, geomagnetic)) return
                }

                // Extract heading from rotation matrix — tilt-independent.
                // Rotation matrix columns map device axes to world (X=East, Y=North, Z=Up).
                // Device -Z axis = back of phone = direction user faces.
                // Project -Z onto horizontal plane to get heading at any tilt.
                val fwdEast = -rotationMatrix[2].toDouble()
                val fwdNorth = -rotationMatrix[5].toDouble()
                val horizontalMag = sqrt(fwdEast * fwdEast + fwdNorth * fwdNorth)

                val rawDegrees = if (horizontalMag < 0.15) {
                    // Phone is nearly flat — use Y axis (top of phone) instead
                    val topEast = rotationMatrix[1].toDouble()
                    val topNorth = rotationMatrix[4].toDouble()
                    ((Math.toDegrees(atan2(topEast, topNorth)) + 360) % 360).toFloat()
                } else {
                    ((Math.toDegrees(atan2(fwdEast, fwdNorth)) + 360) % 360).toFloat()
                }

                smoothedHeading = if (smoothedHeading.isNaN()) {
                    rawDegrees
                } else {
                    circularLerp(smoothedHeading, rawDegrees, HEADING_ALPHA)
                }

                trySend(CompassReading(smoothedHeading, needsCalibration))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                    sensor?.type == Sensor.TYPE_ROTATION_VECTOR
                ) {
                    needsCalibration = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                }
            }
        }

        val sensorRate = 150_000 // microseconds (6.67Hz)
        if (useRotationVector) {
            sensorManager.registerListener(listener, rotationVectorSensor, sensorRate)
        } else {
            sensorManager.registerListener(listener, accelerometer, sensorRate)
            sensorManager.registerListener(listener, magnetometer, sensorRate)
        }

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
        private const val SENSOR_ALPHA = 0.25f
        private const val HEADING_ALPHA = 0.3f
    }
}
