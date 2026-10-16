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
    val oilPressurePsi: Float? = null,
    val oilPressureLow: Boolean? = null,
    val intakeAirTempC: Float? = null,
    val boostPsi: Float? = null,
    val batteryVolts: Float? = null,
    val fuelLevelPercent: Float? = null,
    val milOn: Boolean? = null,
    val knockActive: Boolean? = null,
    val damLow: Boolean? = null,
    val obdConnected: Boolean = false,
    val usingSimulator: Boolean = false,
    val alarms: AlarmState = AlarmState()
)

data class AlarmState(
    val cel: Boolean = false,
    val lowFuel: Boolean = false,
    val lowOilPressure: Boolean = false,
    val highCoolant: Boolean = false,
    val highOilTemp: Boolean = false,
    val highIat: Boolean = false,
    val overboost: Boolean = false,
    val lowBattery: Boolean = false,
    val linkLost: Boolean = false,
    val gpsLost: Boolean = false,
    val knock: Boolean = false,
    val dam: Boolean = false
)

enum class GaugeId {
    RPM,
    SPEED,
    GEAR,
    COOLANT,
    OIL_TEMP,
    OIL_PRESSURE,
    INTAKE_AIR_TEMP,
    BOOST,
    BATTERY
}

enum class LampId {
    CEL,
    LOW_FUEL,
    LOW_OIL_PRESSURE,
    HIGH_COOLANT,
    HIGH_OIL_TEMP,
    HIGH_IAT,
    OVERBOOST,
    LOW_BATTERY,
    LINK_LOST,
    GPS_LOST,
    KNOCK,
    DAM
}
