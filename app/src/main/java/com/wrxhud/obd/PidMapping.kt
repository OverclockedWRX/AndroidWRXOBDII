package com.wrxhud.obd

import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.WrxProfile

/** User-assignable HUD signals that can be bound to a scanned PID/DID. */
enum class MappableSignal(val label: String) {
    OIL_TEMP("Oil temp"),
    OIL_PRESSURE("Oil pressure"),
    AFR("AFR"),
    COOLANT("Coolant"),
    IAT("IAT"),
    BOOST_MAP("Boost MAP"),
    BATTERY("Battery")
}

/** How to interpret the response payload bytes. */
enum class PidDecode(val label: String) {
    TEMP_A40("Temp A-40 (°C)"),
    PRESSURE_KPA("Pressure A (kPa)"),
    LAMBDA_AFR("λ → AFR"),
    VOLTAGE_AB("Voltage AB/1000"),
    PERCENT_A("Percent A"),
    RAW_A("Raw A"),
    RAW_AB("Raw A×256+B")
}

/**
 * Persisted mapping from a Mode 01 PID or Mode 22 DID to a HUD signal.
 * [mode] is 0x01 or 0x22; [id] is the PID or DID; [header] is used for Mode 22 (default 0x7E0).
 */
data class PidAssignment(
    val signal: MappableSignal,
    val mode: Int,
    val id: Int,
    val decode: PidDecode,
    val header: Int? = null
) {
    fun addressLabel(): String = when (mode) {
        0x01 -> "01 %02X".format(id)
        0x22 -> "22 %04X".format(id)
        else -> "%02X %X".format(mode, id)
    }

    fun encode(): String {
        val h = header?.let { "|%03X".format(it) }.orEmpty()
        return "${signal.name}|${"%02X".format(mode)}|${"%X".format(id)}|${decode.name}$h"
    }

    companion object {
        fun decode(raw: String): PidAssignment? {
            val parts = raw.split("|")
            if (parts.size < 4) return null
            val signal = runCatching { MappableSignal.valueOf(parts[0]) }.getOrNull() ?: return null
            val mode = parts[1].toIntOrNull(16) ?: return null
            val id = parts[2].toIntOrNull(16) ?: return null
            val decode = runCatching { PidDecode.valueOf(parts[3]) }.getOrNull() ?: return null
            val header = parts.getOrNull(4)?.toIntOrNull(16)
            return PidAssignment(signal, mode, id, decode, header)
        }
    }
}

object PidValueDecoder {
    fun decode(data: ByteArray, formula: PidDecode): Float? {
        return when (formula) {
            PidDecode.TEMP_A40 -> StnParser.decodeTempC(data)
            PidDecode.PRESSURE_KPA -> StnParser.decodePressureKpa(data)
            PidDecode.LAMBDA_AFR -> StnParser.decodeAfr(data, WrxProfile.STOICH_AFR)
            PidDecode.VOLTAGE_AB -> StnParser.decodeVoltage(data)
            PidDecode.PERCENT_A -> StnParser.decodePercent(data)
            PidDecode.RAW_A -> data.firstOrNull()?.let { (it.toInt() and 0xFF).toFloat() }
            PidDecode.RAW_AB -> {
                if (data.size < 2) null
                else (((data[0].toInt() and 0xFF) * 256) + (data[1].toInt() and 0xFF)).toFloat()
            }
        }
    }

    /** Convert a decoded raw value into the unit stored on [com.wrxhud.vehicle.VehicleSnapshot]. */
    fun toSnapshotValue(signal: MappableSignal, formula: PidDecode, decoded: Float): Float {
        return when (signal) {
            MappableSignal.OIL_PRESSURE -> when (formula) {
                PidDecode.PRESSURE_KPA -> UnitConverters.kpaToPsi(decoded)
                else -> decoded
            }
            MappableSignal.BOOST_MAP -> decoded // MAP kPa; poller applies baro
            else -> decoded
        }
    }
}

data class PidScanCandidate(
    val decode: PidDecode,
    val value: Float,
    val display: String
)

data class PidScanRow(
    val mode: Int,
    val id: Int,
    val header: Int?,
    val rawHex: String,
    val data: ByteArray,
    val candidates: List<PidScanCandidate>,
    val assignedSignal: MappableSignal? = null
) {
    fun addressLabel(): String = when (mode) {
        0x01 -> "01 %02X".format(id)
        0x22 -> "22 %04X".format(id)
        else -> "%02X %X".format(mode, id)
    }

    fun toLogEntry(scannedAtMs: Long = System.currentTimeMillis()): PidScanLogEntry =
        PidScanLogEntry(
            mode = mode,
            id = id,
            header = header,
            rawHex = rawHex,
            scannedAtMs = scannedAtMs,
            candidates = candidates.take(6).map {
                PidScanLogCandidate(it.decode, it.display)
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PidScanRow) return false
        return mode == other.mode && id == other.id && header == other.header
    }

    override fun hashCode(): Int = 31 * mode + id + (header ?: 0)
}

/** Lightweight candidate snapshot for the persisted PID log (no raw float). */
data class PidScanLogCandidate(
    val decode: PidDecode,
    val display: String
)

/**
 * Persisted scan hit for Settings → PID Log and Downloads reference files.
 * Merged across scans by [mode]/[id]/[header].
 */
data class PidScanLogEntry(
    val mode: Int,
    val id: Int,
    val header: Int?,
    val rawHex: String,
    val scannedAtMs: Long,
    val candidates: List<PidScanLogCandidate>
) {
    fun addressLabel(): String = when (mode) {
        0x01 -> "01 %02X".format(id)
        0x22 -> "22 %04X".format(id)
        else -> "%02X %X".format(mode, id)
    }

    fun encode(): String {
        val h = header?.let { "%03X".format(it) }.orEmpty()
        val cands = candidates.joinToString("~") {
            "${it.decode.name}:${it.display.replace(';', ',').replace('|', '/')}"
        }
        return "${"%02X".format(mode)}|${"%X".format(id)}|$h|$rawHex|$scannedAtMs|$cands"
    }

    companion object {
        fun decode(raw: String): PidScanLogEntry? {
            val parts = raw.split("|")
            if (parts.size < 5) return null
            val mode = parts[0].toIntOrNull(16) ?: return null
            val id = parts[1].toIntOrNull(16) ?: return null
            val header = parts[2].takeIf { it.isNotBlank() }?.toIntOrNull(16)
            val rawHex = parts[3]
            val scannedAtMs = parts[4].toLongOrNull() ?: return null
            val candidates = parts.getOrNull(5)
                ?.split("~")
                ?.mapNotNull { piece ->
                    val idx = piece.indexOf(':')
                    if (idx <= 0) return@mapNotNull null
                    val decode = runCatching {
                        PidDecode.valueOf(piece.substring(0, idx))
                    }.getOrNull() ?: return@mapNotNull null
                    PidScanLogCandidate(decode, piece.substring(idx + 1))
                }
                .orEmpty()
            return PidScanLogEntry(mode, id, header, rawHex, scannedAtMs, candidates)
        }

        /** Upsert [incoming] into [existing] by address; keep newest scan values. */
        fun merge(
            existing: List<PidScanLogEntry>,
            incoming: List<PidScanLogEntry>
        ): List<PidScanLogEntry> {
            val map = LinkedHashMap<String, PidScanLogEntry>()
            fun key(e: PidScanLogEntry) = "${e.mode}:${e.id}:${e.header ?: -1}"
            existing.forEach { map[key(it)] = it }
            incoming.forEach { map[key(it)] = it }
            return map.values.sortedWith(
                compareBy<PidScanLogEntry> { it.mode }.thenBy { it.id }.thenBy { it.header ?: 0 }
            )
        }
    }
}
