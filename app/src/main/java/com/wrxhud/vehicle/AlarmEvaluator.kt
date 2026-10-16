package com.wrxhud.vehicle

object AlarmEvaluator {
    fun evaluate(
        snapshot: VehicleSnapshot,
        thresholds: WrxProfile.AlarmThresholds,
        expectObd: Boolean
    ): AlarmState {
        return AlarmState(
            cel = snapshot.milOn == true,
            lowFuel = snapshot.fuelLevelPercent?.let { it <= thresholds.lowFuelPercent } == true,
            highCoolant = snapshot.coolantC?.let { it >= thresholds.coolantAmberC } == true,
            highOilTemp = snapshot.oilTempC?.let { it >= thresholds.oilTempAmberC } == true,
            highIat = snapshot.intakeAirTempC?.let { it >= thresholds.iatAmberC } == true,
            overboost = snapshot.boostPsi?.let { it >= thresholds.boostAmberPsi } == true,
            lowBattery = snapshot.batteryVolts?.let { it > 0f && it < thresholds.lowBatteryVolts } == true,
            linkLost = expectObd && !snapshot.obdConnected && !snapshot.usingSimulator,
            gpsLost = !snapshot.gpsHasFix,
            knock = snapshot.knockActive == true,
            dam = snapshot.damLow == true
        )
    }

    fun severityForCoolant(tempC: Float?, thresholds: WrxProfile.AlarmThresholds): Severity {
        if (tempC == null) return Severity.NONE
        return when {
            tempC >= thresholds.coolantRedC -> Severity.CRITICAL
            tempC >= thresholds.coolantAmberC -> Severity.WARN
            else -> Severity.NORMAL
        }
    }

    fun severityForOilTemp(tempC: Float?, thresholds: WrxProfile.AlarmThresholds): Severity {
        if (tempC == null) return Severity.NONE
        return when {
            tempC >= thresholds.oilTempRedC -> Severity.CRITICAL
            tempC >= thresholds.oilTempAmberC -> Severity.WARN
            else -> Severity.NORMAL
        }
    }

    fun severityForIat(tempC: Float?, thresholds: WrxProfile.AlarmThresholds): Severity {
        if (tempC == null) return Severity.NONE
        return when {
            tempC >= thresholds.iatRedC -> Severity.CRITICAL
            tempC >= thresholds.iatAmberC -> Severity.WARN
            else -> Severity.NORMAL
        }
    }

    fun severityForBoost(psi: Float?, thresholds: WrxProfile.AlarmThresholds): Severity {
        if (psi == null) return Severity.NONE
        return when {
            psi >= thresholds.boostRedPsi -> Severity.CRITICAL
            psi >= thresholds.boostAmberPsi -> Severity.WARN
            else -> Severity.NORMAL
        }
    }

    enum class Severity { NONE, NORMAL, WARN, CRITICAL }
}
