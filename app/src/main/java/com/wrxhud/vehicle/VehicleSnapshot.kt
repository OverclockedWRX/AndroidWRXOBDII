package com.wrxhud.vehicle

/**
 * Immutable telemetry frame shared by the live OBD path and the simulator.
 * Null [Float]/[Boolean] fields mean "no data yet / unsupported".
 */
data class VehicleSnapshot(
    val timestampMs: Long = 0L,
    val rpm: Float? = null,
    val previousRpm: Float? = null,
    val rpmSampleAgeMs: Long = 0L,
    val gpsSpeedMps: Float? = null,
    val gpsHasFix: Boolean = false,
    val obdSpeedKph: Float? = null,
    val gear: Int? = null,
    val coolantC: Float? = null,
    val oilTempC: Float? = null,
    /** How oil temp was resolved, e.g. "01 5C", "22 0113", or "none". */
    val oilTempSource: String? = null,
    val intakeAirTempC: Float? = null,
    val boostPsi: Float? = null,
    /** Gasoline AFR from λ × 14.7 (wideband / commanded eq ratio). */
    val afr: Float? = null,
    /**
     * Oil pressure warning switch (stock VB has no PSI sender over OBD).
     * true = Normal, false = Low, null = no reading yet.
     */
    val oilPressureOk: Boolean? = null,
    val batteryVolts: Float? = null,
    val fuelLevelPercent: Float? = null,
    /** Absolute throttle plate position (%), Mode 01 PID 0x11. */
    val throttlePercent: Float? = null,
    /** Accelerator pedal / driver demand (%), Mode 01 PID 0x49 when supported. */
    val acceleratorPercent: Float? = null,
    /**
     * Brake pedal (%). Left null — no universal Mode 01 brake PID on FA24;
     * reserved for a future OEM DID mapping.
     */
    val brakePercent: Float? = null,
    val milOn: Boolean? = null,
    val knockActive: Boolean? = null,
    val damLow: Boolean? = null,
    val obdConnected: Boolean = false,
    val usingSimulator: Boolean = false,
    val alarms: AlarmState = AlarmState()
) {
    /**
     * Keeps only phone GPS fields; clears all OBD / simulator telemetry.
     */
    fun phoneOnly(
        gpsSpeedMps: Float? = this.gpsSpeedMps,
        gpsHasFix: Boolean = this.gpsHasFix,
        thresholds: WrxProfile.AlarmThresholds = WrxProfile.AlarmThresholds(),
        expectObd: Boolean = true
    ): VehicleSnapshot {
        val cleared = VehicleSnapshot(
            timestampMs = System.currentTimeMillis(),
            gpsSpeedMps = gpsSpeedMps,
            gpsHasFix = gpsHasFix,
            obdConnected = false,
            usingSimulator = false
        )
        return cleared.copy(
            alarms = AlarmEvaluator.evaluate(cleared, thresholds, expectObd = expectObd)
        )
    }
}

data class AlarmState(
    val cel: Boolean = false,
    val lowFuel: Boolean = false,
    val highCoolant: Boolean = false,
    val highOilTemp: Boolean = false,
    val highIat: Boolean = false,
    val overboost: Boolean = false,
    val lowBattery: Boolean = false,
    val linkLost: Boolean = false,
    val gpsLost: Boolean = false,
    val knock: Boolean = false,
    val dam: Boolean = false,
    val lowOilPressure: Boolean = false
)

enum class GaugeId {
    RPM,
    THROTTLE,
    SPEED,
    GEAR,
    COOLANT,
    OIL_TEMP,
    OIL_PRESSURE,
    INTAKE_AIR_TEMP,
    BOOST,
    AFR,
    BATTERY
}

enum class LampId {
    CEL,
    LOW_FUEL,
    HIGH_COOLANT,
    HIGH_OIL_TEMP,
    HIGH_IAT,
    OVERBOOST,
    LOW_BATTERY,
    LOW_OIL_PRESSURE,
    LINK_LOST,
    GPS_LOST,
    KNOCK,
    DAM
}
