package com.wrxhud.vehicle

/**
 * 2022–present Subaru WRX (VB / FA24) vehicle profile.
 *
 * Oil temp: try SAE Mode 01 PID 0x5C first, then Mode 22 candidates under header 7E0.
 * A candidate is accepted for the session when the ECU returns a plausible temperature.
 */
object WrxProfile {
    const val MODEL_YEARS = "2022–present"
    const val ENGINE = "FA24"
    const val REDLINE_RPM = 6700
    const val DEFAULT_TIRE_WIDTH_MM = 245
    const val DEFAULT_TIRE_ASPECT = 40
    const val DEFAULT_WHEEL_DIAMETER_IN = 18

    val manual6 = TransmissionProfile(
        name = "6MT",
        finalDrive = 4.111,
        gears = listOf(
            GearRatio(1, 3.454),
            GearRatio(2, 1.947),
            GearRatio(3, 1.367),
            GearRatio(4, 1.029),
            GearRatio(5, 0.825),
            GearRatio(6, 0.667)
        ),
        reverse = 3.636
    )

    /** SPT / Lineartronic stepped ratios used in paddle mode. */
    val sptCvt = TransmissionProfile(
        name = "SPT/CVT",
        finalDrive = 4.444,
        gears = listOf(
            GearRatio(1, 3.490),
            GearRatio(2, 2.031),
            GearRatio(3, 1.461),
            GearRatio(4, 1.116),
            GearRatio(5, 0.903),
            GearRatio(6, 0.756),
            GearRatio(7, 0.645),
            GearRatio(8, 0.571)
        ),
        reverse = 3.394
    )

    data class GearRatio(val gear: Int, val ratio: Double)

    data class TransmissionProfile(
        val name: String,
        val finalDrive: Double,
        val gears: List<GearRatio>,
        val reverse: Double
    )

    data class Mode22Candidate(
        val did: Int,
        val label: String,
        val scale: (ByteArray) -> Float?,
        val plausible: (Float) -> Boolean
    )

    /**
     * Mode 22 oil-temp candidates (header 7E0). Probed at connect if Mode 01 0x5C fails.
     * 0x0113 is a common Subaru oil-temp DID (A-40); 0x1153 is a secondary probe.
     */
    val oilTempCandidates: List<Mode22Candidate> = listOf(
        Mode22Candidate(
            did = 0x0113,
            label = "Oil temp Mode22 0x0113",
            scale = { bytes ->
                if (bytes.isNotEmpty()) (bytes[0].toInt() and 0xFF) - 40f else null
            },
            plausible = { it in -20f..160f }
        ),
        Mode22Candidate(
            did = 0x1153,
            label = "Oil temp Mode22 0x1153",
            scale = { bytes ->
                if (bytes.isNotEmpty()) (bytes[0].toInt() and 0xFF) - 40f else null
            },
            plausible = { it in -20f..160f }
        )
    )

    val knockCandidates: List<Mode22Candidate> = emptyList()
    val damCandidates: List<Mode22Candidate> = emptyList()

    object Pid {
        const val MONITOR_STATUS = 0x01
        const val COOLANT = 0x05
        const val MAP = 0x0B
        const val RPM = 0x0C
        const val SPEED = 0x0D
        const val IAT = 0x0F
        const val FUEL_LEVEL = 0x2F
        const val BARO = 0x33
        const val CONTROL_MODULE_VOLTAGE = 0x42
        /** SAE engine oil temperature (°C = A - 40). */
        const val OIL_TEMP = 0x5C
    }

    data class AlarmThresholds(
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
    )

    fun tireCircumferenceMeters(
        widthMm: Int = DEFAULT_TIRE_WIDTH_MM,
        aspect: Int = DEFAULT_TIRE_ASPECT,
        wheelIn: Int = DEFAULT_WHEEL_DIAMETER_IN
    ): Double {
        val sidewallMm = widthMm * (aspect / 100.0)
        val diameterMm = (wheelIn * 25.4) + (2.0 * sidewallMm)
        return Math.PI * (diameterMm / 1000.0)
    }
}
