package com.wrxhud.vehicle

object UnitConverters {
    fun celsiusToFahrenheit(c: Float): Float = c * 9f / 5f + 32f

    fun fahrenheitToCelsius(f: Float): Float = (f - 32f) * 5f / 9f

    fun mpsToMph(mps: Float): Float = mps * 2.2369363f

    fun mpsToKph(mps: Float): Float = mps * 3.6f

    fun kphToMps(kph: Float): Float = kph / 3.6f

    fun kpaToPsi(kpa: Float): Float = kpa * 0.1450377f

    fun psiToKpa(psi: Float): Float = psi / 0.1450377f

    fun psiToBar(psi: Float): Float = psi / 14.50377f

    fun formatTemp(celsius: Float?, useFahrenheit: Boolean): String {
        if (celsius == null) return "—"
        val value = if (useFahrenheit) celsiusToFahrenheit(celsius) else celsius
        return "%.0f".format(value)
    }

    fun formatSpeed(mps: Float?, useMph: Boolean): String {
        if (mps == null) return "—"
        val value = if (useMph) mpsToMph(mps) else mpsToKph(mps)
        return "%.0f".format(value)
    }

    fun formatPressure(psi: Float?, unit: PressureUnit): String {
        if (psi == null) return "—"
        val value = when (unit) {
            PressureUnit.PSI -> psi
            PressureUnit.KPA -> psiToKpa(psi)
            PressureUnit.BAR -> psiToBar(psi)
        }
        return if (unit == PressureUnit.BAR) "%.2f".format(value) else "%.1f".format(value)
    }

    fun formatVoltage(volts: Float?): String {
        if (volts == null) return "—"
        return "%.1f".format(volts)
    }

    fun formatAfr(afr: Float?): String {
        if (afr == null) return "—"
        return "%.1f".format(afr)
    }

    fun formatRpm(rpm: Float?): String {
        if (rpm == null) return "—"
        return "%.0f".format(rpm)
    }

    fun formatGear(gear: Int?): String = when (gear) {
        null -> "N"
        -1 -> "R"
        else -> gear.toString()
    }
}

enum class PressureUnit { PSI, KPA, BAR }
enum class SpeedUnit { MPH, KPH }
enum class TempUnit { C, F }
