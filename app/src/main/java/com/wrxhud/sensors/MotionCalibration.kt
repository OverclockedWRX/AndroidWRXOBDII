package com.wrxhud.sensors

import kotlin.math.sqrt

/**
 * Mount baseline captured while the car is parked and the phone is fixed in its mount.
 * Live attitude is reported relative to this zero.
 */
data class MotionMountCalibration(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val gForce: Float = 1f
)

fun wrapSignedDegrees(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

/** Subtract mount baseline so parked / mounted attitude reads ~0° / ~0° / ~0°. */
fun MotionReading.relativeTo(calib: MotionMountCalibration, calibrated: Boolean): MotionReading {
    if (!calibrated || !available) return this
    return copy(
        pitchDeg = pitchDeg?.let { wrapSignedDegrees(it - calib.pitchDeg) },
        rollDeg = rollDeg?.let { wrapSignedDegrees(it - calib.rollDeg) },
        yawDeg = yawDeg?.let { wrapSignedDegrees(it - calib.yawDeg) }
        // g-force stays absolute (gravity + acceleration)
    )
}

data class CalibrationSampleStats(
    val pitchMean: Float,
    val rollMean: Float,
    val yawMean: Float,
    val gMean: Float,
    val pitchStd: Float,
    val rollStd: Float,
    val yawStd: Float,
    val gStd: Float,
    val sampleCount: Int
)

fun computeCalibrationStats(
    pitches: List<Float>,
    rolls: List<Float>,
    yaws: List<Float>,
    gs: List<Float>
): CalibrationSampleStats? {
    if (pitches.isEmpty() || pitches.size != rolls.size || pitches.size != yaws.size) return null
    fun mean(v: List<Float>) = v.sum() / v.size
    fun std(v: List<Float>, m: Float): Float {
        if (v.size < 2) return 0f
        val varSum = v.sumOf { ((it - m) * (it - m)).toDouble() }
        return sqrt(varSum / v.size).toFloat()
    }
    // Unwrap yaw around the first sample so averaging across ±180 works.
    val yaw0 = yaws.first()
    val yawUnwrapped = yaws.map { yaw0 + wrapSignedDegrees(it - yaw0) }
    val pMean = mean(pitches)
    val rMean = mean(rolls)
    val yMean = wrapSignedDegrees(mean(yawUnwrapped))
    val gMean = if (gs.isNotEmpty()) mean(gs) else 1f
    return CalibrationSampleStats(
        pitchMean = pMean,
        rollMean = rMean,
        yawMean = yMean,
        gMean = gMean,
        pitchStd = std(pitches, pMean),
        rollStd = std(rolls, rMean),
        yawStd = std(yawUnwrapped, mean(yawUnwrapped)),
        gStd = if (gs.isNotEmpty()) std(gs, gMean) else 0f,
        sampleCount = pitches.size
    )
}

enum class CalibrationRejectReason {
    TOO_FEW_SAMPLES,
    TOO_SHAKY,
    G_FORCE_OUT_OF_RANGE,
    NO_ATTITUDE
}

sealed class CalibrationResult {
    data class Ok(val calibration: MotionMountCalibration) : CalibrationResult()
    data class Rejected(val reason: CalibrationRejectReason, val detail: String) : CalibrationResult()
}

/**
 * Accept a parked mount sample set if attitude is stable and g ≈ 1.
 */
fun evaluateMountCalibration(stats: CalibrationSampleStats?): CalibrationResult {
    if (stats == null || stats.sampleCount < 15) {
        return CalibrationResult.Rejected(
            CalibrationRejectReason.TOO_FEW_SAMPLES,
            "Hold still — need a longer sample."
        )
    }
    val maxAngleStd = 2.5f
    if (stats.pitchStd > maxAngleStd || stats.rollStd > maxAngleStd || stats.yawStd > 4f) {
        return CalibrationResult.Rejected(
            CalibrationRejectReason.TOO_SHAKY,
            "Too much movement (P±%.1f° R±%.1f° Y±%.1f°). Park, don’t touch the phone."
                .format(stats.pitchStd, stats.rollStd, stats.yawStd)
        )
    }
    if (stats.gMean !in 0.85f..1.15f || stats.gStd > 0.08f) {
        return CalibrationResult.Rejected(
            CalibrationRejectReason.G_FORCE_OUT_OF_RANGE,
            "G-force unstable (%.2fg). Park on level ground with the car still."
                .format(stats.gMean)
        )
    }
    return CalibrationResult.Ok(
        MotionMountCalibration(
            pitchDeg = stats.pitchMean,
            rollDeg = stats.rollMean,
            yawDeg = stats.yawMean,
            gForce = stats.gMean
        )
    )
}
