package com.wrxhud.obd

import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import com.wrxhud.vehicle.WrxProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin

/**
 * Generates plausible WRX telemetry so the HUD can be developed without a car.
 */
class SimulatedObdSource(
    private val thresholds: WrxProfile.AlarmThresholds = WrxProfile.AlarmThresholds()
) {
    fun snapshots(): Flow<VehicleSnapshot> = flow {
        var t = 0.0
        var previousRpm = 1200f
        while (true) {
            t += 0.05
            val rpm = (2200 + 2800 * (0.5 + 0.5 * sin(t * 1.6))).toFloat()
            val speedMph = (25 + 45 * (0.5 + 0.5 * sin(t * 0.7))).toFloat()
            val speedMps = speedMph / 2.2369363f
            val boost = (4f + 10f * ((rpm - 2000f) / 4000f).coerceIn(0f, 1f))
            val coolant = 88f + 8f * sin(t * 0.05).toFloat()
            val oil = 95f + 10f * sin(t * 0.04).toFloat()
            val iat = 28f + 6f * sin(t * 0.1).toFloat()
            val gear = when {
                speedMph < 15 -> 1
                speedMph < 30 -> 2
                speedMph < 45 -> 3
                speedMph < 60 -> 4
                speedMph < 75 -> 5
                else -> 6
            }

            val base = VehicleSnapshot(
                timestampMs = System.currentTimeMillis(),
                rpm = rpm,
                previousRpm = previousRpm,
                rpmSampleAgeMs = 40,
                gpsSpeedMps = speedMps,
                gpsHasFix = true,
                obdSpeedKph = UnitConverters.mpsToKph(speedMps),
                gear = gear,
                coolantC = coolant,
                oilTempC = oil,
                oilPressurePsi = null,
                oilPressureLow = false,
                intakeAirTempC = iat,
                boostPsi = boost,
                batteryVolts = 13.8f + 0.2f * sin(t).toFloat(),
                fuelLevelPercent = 55f,
                milOn = false,
                knockActive = false,
                damLow = false,
                obdConnected = true,
                usingSimulator = true
            )
            val alarms = AlarmEvaluator.evaluate(base, thresholds, expectObd = true)
            emit(base.copy(alarms = alarms))
            previousRpm = rpm
            delay(40)
        }
    }
}
