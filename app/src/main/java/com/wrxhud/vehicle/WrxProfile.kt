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
    const val REDLINE_RPM = 6200
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

    /**
     * Mode 22 IAT suspects (FA24 often omits SAE 01 0F). A−40 °C decode.
     * Values near ambient→coolant at idle are accepted; assign in PID Log to confirm.
     */
    val iatCandidates: List<Mode22Candidate> = listOf(
        0x0008, 0x000D, 0x000E, 0x0011, 0x0018, 0x0019,
        0x0100, 0x0101, 0x0102, 0x0108, 0x0109, 0x010A, 0x010B, 0x010C,
        0x0110, 0x0111, 0x0112, 0x0116, 0x0117, 0x0118, 0x0119,
        0x1140, 0x1141, 0x1148, 0x1149, 0x114A, 0x114B, 0x114C,
        0x1150, 0x1151, 0x1152, 0x1156, 0x1157, 0x1158, 0x1159
    ).distinct().map { did ->
        Mode22Candidate(
            did = did,
            label = "IAT Mode22 0x${"%04X".format(did)}",
            scale = { bytes ->
                if (bytes.isNotEmpty()) (bytes[0].toInt() and 0xFF) - 40f else null
            },
            // Intake charge: colder than coolant when warm; warmer than deep freeze.
            plausible = { it in -30f..90f }
        )
    }

    /**
     * Mode 22 MAP / manifold absolute pressure suspects (FA24 often omits SAE 01 0B).
     * Decoded as kPa (raw A, or AB/10 when 2+ bytes look like scaled MAP).
     */
    val mapCandidates: List<Mode22Candidate> = listOf(
        0x000B, 0x000C, 0x0012, 0x0013, 0x0014, 0x0015,
        0x0103, 0x0104, 0x0105, 0x0106, 0x0107, 0x010D, 0x010E, 0x010F,
        0x011A, 0x011B, 0x011C, 0x011D, 0x011E, 0x011F,
        0x1142, 0x1143, 0x1144, 0x1145, 0x1146, 0x1147,
        0x115A, 0x115B, 0x115C, 0x115D, 0x115E, 0x115F,
        0x1230, 0x1231, 0x1232, 0x1233, 0x1235, 0x1236
    ).distinct().map { did ->
        Mode22Candidate(
            did = did,
            label = "MAP Mode22 0x${"%04X".format(did)}",
            scale = { bytes ->
                when {
                    bytes.isEmpty() -> null
                    bytes.size >= 2 -> {
                        val ab = ((bytes[0].toInt() and 0xFF) * 256) + (bytes[1].toInt() and 0xFF)
                        // Common Subaru scalings: raw kPa in A, or AB/10, or AB/100.
                        when {
                            ab in 50..4000 && ab / 10 in 20..300 -> ab / 10f
                            ab in 20..300 -> ab.toFloat()
                            else -> (bytes[0].toInt() and 0xFF).toFloat()
                        }
                    }
                    else -> (bytes[0].toInt() and 0xFF).toFloat()
                }
            },
            // Absolute manifold pressure: deep vacuum ~20 kPa → overboost ~300 kPa.
            plausible = { it in 15f..320f }
        )
    }

    /** Feedback knock / fine knock learn suspects (often signed / scaled bytes). */
    val knockCandidates: List<Mode22Candidate> = listOf(
        0x0020, 0x0021, 0x0022, 0x0023, 0x0024, 0x0025,
        0x0120, 0x0121, 0x0122, 0x0123, 0x0124, 0x0125,
        0x1160, 0x1161, 0x1162, 0x1163, 0x1164, 0x1165,
        0x1190, 0x1191, 0x1192, 0x1193
    ).distinct().map { did ->
        Mode22Candidate(
            did = did,
            label = "Knock Mode22 0x${"%04X".format(did)}",
            scale = { bytes ->
                if (bytes.isEmpty()) null
                else bytes[0].toInt().toFloat() // signed-ish raw for log inspection
            },
            plausible = { it in -128f..128f }
        )
    }

    /** Dynamic Advance Multiplier suspects (often 0–1 or 0–16 scaled). */
    val damCandidates: List<Mode22Candidate> = listOf(
        0x0026, 0x0027, 0x0028, 0x0029, 0x002A,
        0x0126, 0x0127, 0x0128, 0x0129, 0x012A,
        0x1166, 0x1167, 0x1168, 0x1169, 0x116A,
        0x1194, 0x1195, 0x1196, 0x1197
    ).distinct().map { did ->
        Mode22Candidate(
            did = did,
            label = "DAM Mode22 0x${"%04X".format(did)}",
            scale = { bytes ->
                if (bytes.isEmpty()) null
                else {
                    val a = bytes[0].toInt() and 0xFF
                    when {
                        a in 0..16 -> a.toFloat()
                        bytes.size >= 2 -> {
                            val ab = (a * 256) + (bytes[1].toInt() and 0xFF)
                            ab / 32768f // λ-style 0–2
                        }
                        else -> a / 255f
                    }
                }
            },
            plausible = { it in 0f..16f }
        )
    }

    /** Oil pressure PSI suspects (many VBs have switch only — probes are exploratory). */
    val oilPressureCandidates: List<Mode22Candidate> = listOf(
        0x0114, 0x0115, 0x1154, 0x1155, 0x1234,
        0x0130, 0x0131, 0x0132, 0x1170, 0x1171, 0x1172
    ).distinct().map { did ->
        Mode22Candidate(
            did = did,
            label = "Oil pressure Mode22 0x${"%04X".format(did)}",
            scale = { bytes ->
                if (bytes.isEmpty()) null
                else {
                    val a = (bytes[0].toInt() and 0xFF).toFloat()
                    // Try kPa→psi if value looks like absolute pressure.
                    if (a in 50f..800f) a * 0.145038f else a
                }
            },
            plausible = { it in 0f..150f }
        )
    }

    /**
     * Broad read-only Mode 22 DID hunt for the PID scanner (UDS 0x22 only).
     * Dense around known Subaru blocks; not a full 0x0000–0xFFFF sweep.
     */
    val mode22ProbeDids: List<Int> = buildList {
        fun addRange(from: Int, to: Int) {
            for (did in from..to) add(did)
        }
        addAll(oilTempCandidates.map { it.did })
        addAll(iatCandidates.map { it.did })
        addAll(mapCandidates.map { it.did })
        addAll(knockCandidates.map { it.did })
        addAll(damCandidates.map { it.did })
        addAll(oilPressureCandidates.map { it.did })
        // Neighborhood sweeps (read-only)
        addRange(0x0001, 0x0040)
        addRange(0x00F0, 0x013F)
        addRange(0x1000, 0x1060)
        addRange(0x1100, 0x11A0)
        addRange(0x1200, 0x1260)
        // Standard info DIDs sometimes present (VIN / cal IDs) — still reads
        add(0xF190)
        add(0xF191)
        add(0xF187)
        add(0xF189)
    }.distinct().sorted()

    object Pid {
        const val MONITOR_STATUS = 0x01
        const val COOLANT = 0x05
        const val MAP = 0x0B
        const val RPM = 0x0C
        const val SPEED = 0x0D
        const val IAT = 0x0F
        /** Absolute throttle position (%). */
        const val THROTTLE_ABS = 0x11
        const val FUEL_LEVEL = 0x2F
        const val BARO = 0x33
        const val CONTROL_MODULE_VOLTAGE = 0x42
        /** SAE engine oil temperature (°C = A - 40). */
        const val OIL_TEMP = 0x5C
        /** Wideband O2 sensor 1 equivalence ratio (λ) + voltage. */
        const val O2_EQ_RATIO = 0x34
        /** Commanded equivalence ratio (λ). */
        const val COMMANDED_EQ_RATIO = 0x44
        /** Accelerator pedal position D — driver demand (%). */
        const val ACCEL_PEDAL_D = 0x49
    }

    /** Gasoline stoichiometric AFR used to convert λ → AFR. */
    const val STOICH_AFR = 14.7f

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
        val lowBatteryVolts: Float = 12.0f,
        /** Low oil pressure warnings (psi). */
        val oilPressureAmberPsi: Float = 15f,
        val oilPressureRedPsi: Float = 10f,
        /** Lean warning (gasoline AFR). */
        val afrLeanAmber: Float = 15.2f,
        val afrLeanRed: Float = 16.0f,
        /** Rich warning (gasoline AFR). */
        val afrRichAmber: Float = 11.5f,
        val afrRichRed: Float = 10.5f
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
