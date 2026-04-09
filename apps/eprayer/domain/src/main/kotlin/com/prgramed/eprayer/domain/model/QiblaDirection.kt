package com.prgramed.eprayer.domain.model

data class QiblaDirection(
    val qiblaBearing: Double,
    val deviceHeading: Float,
    val relativeAngle: Double,
    val needsCalibration: Boolean = false,
)
