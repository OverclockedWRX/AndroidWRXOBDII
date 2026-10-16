package com.wrxhud.settings

import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.LampId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import com.wrxhud.vehicle.WrxProfile

data class HudPreferences(
    val enabledGauges: Set<GaugeId> = setOf(
        GaugeId.RPM,
        GaugeId.SPEED,
        GaugeId.GEAR,
        GaugeId.COOLANT,
        GaugeId.OIL_TEMP,
        GaugeId.INTAKE_AIR_TEMP,
        GaugeId.BOOST,
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
        LampId.LINK_LOST,
        LampId.GPS_LOST,
        LampId.KNOCK,
        LampId.DAM
    ),
    val tempUnit: TempUnit = TempUnit.F,
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val pressureUnit: PressureUnit = PressureUnit.PSI,
    val redlineRpm: Int = WrxProfile.REDLINE_RPM,
    val shiftLightStarts: List<Int> = listOf(
        5200, 5400, 5600, 5800, 6000, 6200, 6400, 6500, 6600, 6700
    ),
    val useSptCvt: Boolean = false,
    val tireWidthMm: Int = WrxProfile.DEFAULT_TIRE_WIDTH_MM,
    val tireAspect: Int = WrxProfile.DEFAULT_TIRE_ASPECT,
    val wheelDiameterIn: Int = WrxProfile.DEFAULT_WHEEL_DIAMETER_IN,
    val useSimulator: Boolean = true,
    val coolantAmberC: Float = 105f,
    val coolantRedC: Float = 115f,
    val oilTempAmberC: Float = 120f,
    val oilTempRedC: Float = 130f,
    val iatAmberC: Float = 50f,
    val iatRedC: Float = 60f,
    val boostAmberPsi: Float = 18f,
    val boostRedPsi: Float = 22f,
    val lowFuelPercent: Float = 15f,
    val lowBatteryVolts: Float = 12.0f
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
        lowBatteryVolts = lowBatteryVolts
    )
}
