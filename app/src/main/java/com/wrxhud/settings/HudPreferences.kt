package com.wrxhud.settings

import com.wrxhud.gps.GpsSourceMode
import com.wrxhud.obd.PidAssignment
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.LampId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import com.wrxhud.vehicle.WrxProfile

enum class ShiftLightPattern {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    OUTSIDE_IN,
    INSIDE_OUT;

    val label: String
        get() = when (this) {
            LEFT_TO_RIGHT -> "Left → right"
            RIGHT_TO_LEFT -> "Right → left"
            OUTSIDE_IN -> "Outside → in"
            INSIDE_OUT -> "Inside → out"
        }
}

data class HudPreferences(
    val onboardingComplete: Boolean = false,
    val enabledGauges: Set<GaugeId> = setOf(
        GaugeId.RPM,
        GaugeId.SPEED,
        GaugeId.GEAR,
        GaugeId.COOLANT,
        GaugeId.OIL_TEMP,
        GaugeId.INTAKE_AIR_TEMP,
        GaugeId.BOOST,
        GaugeId.AFR,
        GaugeId.BATTERY
    ),
    val enabledLamps: Set<LampId> = setOf(
        LampId.CEL,
        LampId.LOW_FUEL,
        LampId.HIGH_COOLANT,
        LampId.HIGH_OIL_TEMP,
        LampId.HIGH_IAT,
        LampId.OVERBOOST,
        LampId.LOW_BATTERY,
        LampId.LOW_OIL_PRESSURE,
        LampId.LINK_LOST,
        LampId.GPS_LOST,
        LampId.KNOCK,
        LampId.DAM
    ),
    val tempUnit: TempUnit = TempUnit.F,
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val pressureUnit: PressureUnit = PressureUnit.PSI,
    val redlineRpm: Int = WrxProfile.REDLINE_RPM,
    val shiftLightPattern: ShiftLightPattern = ShiftLightPattern.LEFT_TO_RIGHT,
    val shiftLightStarts: List<Int> = listOf(
        3300, 3600, 3900, 4200, 4500, 4800, 5100, 5400, 5700, 6100
    ),
    val useSptCvt: Boolean = false,
    val tireWidthMm: Int = WrxProfile.DEFAULT_TIRE_WIDTH_MM,
    val tireAspect: Int = WrxProfile.DEFAULT_TIRE_ASPECT,
    val wheelDiameterIn: Int = WrxProfile.DEFAULT_WHEEL_DIAMETER_IN,
    val useSimulator: Boolean = false,
    val pinkyMode: Boolean = false,
    /** Fast & Furious redline Easter egg overlay after the 2nd+ redline hit this session. */
    val dangerToManifold: Boolean = false,
    /** Extruded / beveled 3D look for the RPM bar tach. */
    val rpmGauge3d: Boolean = false,
    /** How pitch / roll / yaw are shown next to the tach. */
    val motionDisplayMode: MotionDisplayMode = MotionDisplayMode.RINGS,
    /** True after the user completes the in-car mount accelerometer test. */
    val motionCalibrated: Boolean = false,
    val motionCalibPitchDeg: Float = 0f,
    val motionCalibRollDeg: Float = 0f,
    val motionCalibYawDeg: Float = 0f,
    val motionCalibGForce: Float = 1f,
    /** Built-in / Bluetooth / USB / Auto GPS source selection. */
    val gpsSourceMode: GpsSourceMode = GpsSourceMode.AUTO,
    /** Optional bonded BT GPS MAC; empty = auto-pick by device name. */
    val bluetoothGpsAddress: String = "",
    /** User PID/DID mappings from the scanner; empty = auto discovery defaults. */
    val pidAssignments: List<PidAssignment> = emptyList(),
    val coolantAmberC: Float = 105f,
    val coolantRedC: Float = 115f,
    val oilTempAmberC: Float = 120f,
    val oilTempRedC: Float = 130f,
    val iatAmberC: Float = 50f,
    val iatRedC: Float = 60f,
    val boostAmberPsi: Float = 18f,
    val boostRedPsi: Float = 22f,
    val lowFuelPercent: Float = 15f,
    val lowBatteryVolts: Float = 12.0f,
    val oilPressureAmberPsi: Float = 15f,
    val oilPressureRedPsi: Float = 10f
) {
    fun toAlarmThresholds(): WrxProfile.AlarmThresholds = WrxProfile.AlarmThresholds(
        coolantAmberC = coolantAmberC,
        coolantRedC = coolantRedC,
        oilTempAmberC = oilTempAmberC,
        oilTempRedC = oilTempRedC,
        iatAmberC = iatAmberC,
        iatRedC = iatRedC,
        boostAmberPsi = boostAmberPsi,
        boostRedPsi = boostRedPsi,
        lowFuelPercent = lowFuelPercent,
        lowBatteryVolts = lowBatteryVolts,
        oilPressureAmberPsi = oilPressureAmberPsi,
        oilPressureRedPsi = oilPressureRedPsi
    )

    fun assignmentFor(signal: com.wrxhud.obd.MappableSignal): PidAssignment? =
        pidAssignments.firstOrNull { it.signal == signal }

    fun mountCalibration(): com.wrxhud.sensors.MotionMountCalibration =
        com.wrxhud.sensors.MotionMountCalibration(
            pitchDeg = motionCalibPitchDeg,
            rollDeg = motionCalibRollDeg,
            yawDeg = motionCalibYawDeg,
            gForce = motionCalibGForce
        )
}
