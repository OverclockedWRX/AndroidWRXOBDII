package com.wrxhud.vehicle

import kotlin.math.abs

object GearCalculator {
    private const val MIN_SPEED_MPS = 1.5
    private const val MIN_RPM = 800.0
    private const val MATCH_TOLERANCE = 0.08

    /**
     * Estimates engaged gear from RPM and road speed using known WRX ratios.
     * Returns null for neutral / clutch / unmatched.
     */
    fun estimateGear(
        rpm: Double,
        speedMps: Double,
        transmission: WrxProfile.TransmissionProfile,
        tireCircumferenceM: Double,
        includeReverse: Boolean = false
    ): Int? {
        if (speedMps < MIN_SPEED_MPS || rpm < MIN_RPM || tireCircumferenceM <= 0.0) {
            return null
        }

        val wheelRpm = (speedMps / tireCircumferenceM) * 60.0
        if (wheelRpm <= 0.0) return null

        val overall = rpm / wheelRpm
        val candidates = buildList {
            addAll(transmission.gears.map { it.gear to it.ratio * transmission.finalDrive })
            if (includeReverse) {
                add(-1 to transmission.reverse * transmission.finalDrive)
            }
        }

        var bestGear: Int? = null
        var bestError = Double.MAX_VALUE
        for ((gear, expected) in candidates) {
            val error = abs(overall - expected) / expected
            if (error < bestError) {
                bestError = error
                bestGear = gear
            }
        }

        return if (bestError <= MATCH_TOLERANCE) bestGear else null
    }
}
