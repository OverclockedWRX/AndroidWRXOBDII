package com.wrxhud.obd

import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import com.wrxhud.vehicle.WrxProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates plausible WRX telemetry so the HUD can be developed without a car.
 * Periodically pulls through redline and randomly trips warning lamps.
 */
class SimulatedObdSource(
    private val thresholds: WrxProfile.AlarmThresholds = WrxProfile.AlarmThresholds(),
    private val redlineRpm: Int = WrxProfile.REDLINE_RPM
) {
    fun snapshots(): Flow<VehicleSnapshot> = flow {
        var t = 0.0
        var previousRpm = 1200f
        val rng = Random(System.currentTimeMillis())

        // Two quick redline pulls early so DANGER TO MANIFOLD can fire in sim (~2nd hit).
        var nextPullAtMs = System.currentTimeMillis() + rng.nextLong(2_500L, 4_000L)
        var pullStartMs = 0L
        var pullActive = false
        var completedPulls = 0

        var nextAlertAtMs = System.currentTimeMillis() + rng.nextLong(2_000L, 5_000L)
        val alertUntil = LongArray(ALERT_COUNT) { 0L }

        while (true) {
            val now = System.currentTimeMillis()
            t += 0.05

            if (!pullActive && now >= nextPullAtMs) {
                pullActive = true
                pullStartMs = now
            }

            val rpm = if (pullActive) {
                val elapsed = (now - pullStartMs).toFloat()
                when {
                    elapsed < 900f -> {
                        // Climb from cruising band into redline
                        val climb = (elapsed / 900f).coerceIn(0f, 1f)
                        3800f + (redlineRpm + 80f - 3800f) * climb
                    }
                    elapsed < 1_600f -> redlineRpm + 40f + 30f * sin(elapsed * 0.05f).toFloat()
                    elapsed < 2_200f -> {
                        val drop = ((elapsed - 1_600f) / 600f).coerceIn(0f, 1f)
                        (redlineRpm + 40f) * (1f - drop) + 2800f * drop
                    }
                    else -> {
                        pullActive = false
                        completedPulls++
                        nextPullAtMs = now + if (completedPulls < 2) {
                            rng.nextLong(2_000L, 3_500L)
                        } else {
                            rng.nextLong(5_000L, 11_000L)
                        }
                        2800f
                    }
                }
            } else {
                // Cruise below redline, still sweeping shift lights
                (2400f + 2600f * (0.5f + 0.5f * sin(t * 1.35).toFloat()))
            }

            if (now >= nextAlertAtMs) {
                // Flip 1–3 random warning channels on for a short burst
                val count = rng.nextInt(1, 4)
                repeat(count) {
                    val idx = rng.nextInt(ALERT_COUNT)
                    alertUntil[idx] = now + rng.nextLong(1_200L, 3_500L)
                }
                nextAlertAtMs = now + rng.nextLong(2_500L, 6_000L)
            }

            fun alert(i: Int) = now < alertUntil[i]

            val speedMph = (20f + 55f * (0.5f + 0.5f * sin(t * 0.7).toFloat()))
                .coerceAtLeast(if (rpm > redlineRpm * 0.85f) 45f else 15f)
            val speedMps = speedMph / 2.2369363f

            val boostBase = (-8f + 22f * ((rpm - 1800f) / 4200f).coerceIn(0f, 1f))
            val boost = if (alert(ALERT_OVERBOOST)) {
                thresholds.boostRedPsi + 1.5f
            } else {
                boostBase
            }

            val coolant = when {
                alert(ALERT_COOLANT) -> thresholds.coolantRedC + 2f
                else -> 88f + 8f * sin(t * 0.05).toFloat()
            }
            val oil = when {
                alert(ALERT_OIL) -> thresholds.oilTempRedC + 2f
                else -> 95f + 10f * sin(t * 0.04).toFloat()
            }
            val iat = when {
                alert(ALERT_IAT) -> thresholds.iatRedC + 2f
                else -> 28f + 6f * sin(t * 0.1).toFloat()
            }
            val fuel = when {
                alert(ALERT_FUEL) -> thresholds.lowFuelPercent - 3f
                else -> 55f
            }
            val battery = when {
                alert(ALERT_BATTERY) -> thresholds.lowBatteryVolts - 0.8f
                else -> 13.8f + 0.2f * sin(t).toFloat()
            }

            val gear = when {
                speedMph < 15 -> 1
                speedMph < 30 -> 2
                speedMph < 45 -> 3
                speedMph < 60 -> 4
                speedMph < 75 -> 5
                else -> 6
            }

            val base = VehicleSnapshot(
                timestampMs = now,
                rpm = rpm,
                previousRpm = previousRpm,
                rpmSampleAgeMs = 40,
                gpsSpeedMps = speedMps,
                gpsHasFix = !alert(ALERT_GPS),
                obdSpeedKph = UnitConverters.mpsToKph(speedMps),
                gear = gear,
                coolantC = coolant,
                oilTempC = oil,
                oilTempSource = "sim",
                intakeAirTempC = iat,
                boostPsi = boost,
                afr = (14.7f + 1.2f * sin(t * 0.9).toFloat()).coerceIn(11.5f, 16.2f),
                oilPressureOk = rpm >= 400f,
                batteryVolts = battery,
                fuelLevelPercent = fuel,
                throttlePercent = (8f + 85f * (rpm / redlineRpm.toFloat()).coerceIn(0f, 1f)
                    + 5f * sin(t * 1.1).toFloat()).coerceIn(0f, 100f),
                acceleratorPercent = (5f + 90f * (rpm / redlineRpm.toFloat()).coerceIn(0f, 1f)
                    + 4f * sin(t * 1.3).toFloat()).coerceIn(0f, 100f),
                brakePercent = null,
                milOn = alert(ALERT_CEL),
                knockActive = alert(ALERT_KNOCK),
                damLow = alert(ALERT_DAM),
                obdConnected = true,
                usingSimulator = true
            )
            val alarms = AlarmEvaluator.evaluate(base, thresholds, expectObd = true)
            emit(base.copy(alarms = alarms))
            previousRpm = rpm
            delay(40)
        }
    }

    private companion object {
        const val ALERT_CEL = 0
        const val ALERT_FUEL = 1
        const val ALERT_COOLANT = 2
        const val ALERT_OIL = 3
        const val ALERT_IAT = 4
        const val ALERT_OVERBOOST = 5
        const val ALERT_BATTERY = 6
        const val ALERT_GPS = 7
        const val ALERT_KNOCK = 8
        const val ALERT_DAM = 9
        const val ALERT_COUNT = 10
    }
}
