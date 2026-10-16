package com.wrxhud.obd

import android.util.Log
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.WrxProfile

/**
 * One-shot scan of supported Mode 01 PIDs plus known Mode 22 probes.
 * Must run on the OBD thread with the normal poller paused.
 */
class PidScanner(
    private val connection: ObdConnection,
    private val assignments: List<PidAssignment> = emptyList()
) {
    companion object {
        private const val TAG = "PidScanner"
        private const val ECU_HEADER = 0x7E0

        /** Mode 22 DIDs to probe under 7E0 (oil temp / pressure suspects). */
        val mode22Probes: List<Int> = buildList {
            addAll(WrxProfile.oilTempCandidates.map { it.did })
            // Extra Subaru DID probes (oil pressure / related unknowns).
            add(0x0114)
            add(0x0115)
            add(0x1154)
            add(0x1155)
            add(0x1234)
        }.distinct()
    }

    fun scan(): List<PidScanRow> {
        val supported = discoverSupportedPids()
        Log.i(TAG, "Scanning ${supported.size} Mode 01 PIDs + Mode 22 probes")
        val rows = mutableListOf<PidScanRow>()

        for (pid in supported.sorted()) {
            // Skip support-bitmask PIDs themselves.
            if (pid % 0x20 == 0) continue
            val raw = connection.sendCommand(ObdCommands.mode01(pid))
            val data = StnParser.parseMode01(raw, pid) ?: continue
            if (data.isEmpty()) continue
            rows += buildRow(mode = 0x01, id = pid, header = null, data = data)
        }

        connection.sendCommand(ObdCommands.setHeader(ECU_HEADER))
        for (did in mode22Probes) {
            val raw = connection.sendCommand(ObdCommands.mode22(did))
            val data = StnParser.parseMode22(raw, did) ?: continue
            if (data.isEmpty()) continue
            rows += buildRow(mode = 0x22, id = did, header = ECU_HEADER, data = data)
        }
        connection.sendCommand("ATSH7DF")

        return rows
    }

    private fun discoverSupportedPids(): Set<Int> {
        val supported = mutableSetOf<Int>()
        fun mergeSupport(basePid: Int, offset: Int) {
            val raw = connection.sendCommand(ObdCommands.mode01(basePid))
            val data = StnParser.parseMode01(raw, basePid) ?: return
            StnParser.decodeSupportedPidBitmask(data).forEach { bit ->
                supported.add(bit + offset)
            }
        }
        mergeSupport(0x00, 0)
        if (supported.contains(0x20)) mergeSupport(0x20, 0x20)
        if (supported.contains(0x40)) mergeSupport(0x40, 0x40)
        if (supported.contains(0x60)) mergeSupport(0x60, 0x60)
        if (supported.contains(0x80)) mergeSupport(0x80, 0x80)
        if (supported.contains(0xA0)) mergeSupport(0xA0, 0xA0)
        return supported
    }

    private fun buildRow(mode: Int, id: Int, header: Int?, data: ByteArray): PidScanRow {
        val rawHex = data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val candidates = buildCandidates(data)
        val assigned = assignments.firstOrNull { it.mode == mode && it.id == id }?.signal
        return PidScanRow(
            mode = mode,
            id = id,
            header = header,
            rawHex = rawHex,
            data = data,
            candidates = candidates,
            assignedSignal = assigned
        )
    }

    private fun buildCandidates(data: ByteArray): List<PidScanCandidate> {
        val list = mutableListOf<PidScanCandidate>()
        PidValueDecoder.decode(data, PidDecode.TEMP_A40)?.let { v ->
            if (v in -40f..200f) {
                list += PidScanCandidate(
                    PidDecode.TEMP_A40,
                    v,
                    "%.0f °C / %.0f °F".format(v, UnitConverters.celsiusToFahrenheit(v))
                )
            }
        }
        PidValueDecoder.decode(data, PidDecode.PRESSURE_KPA)?.let { v ->
            list += PidScanCandidate(
                PidDecode.PRESSURE_KPA,
                v,
                "%.0f kPa / %.1f psi".format(v, UnitConverters.kpaToPsi(v))
            )
        }
        PidValueDecoder.decode(data, PidDecode.LAMBDA_AFR)?.let { v ->
            if (v in 8f..22f) {
                list += PidScanCandidate(PidDecode.LAMBDA_AFR, v, "%.2f :1 AFR".format(v))
            }
        }
        PidValueDecoder.decode(data, PidDecode.VOLTAGE_AB)?.let { v ->
            if (v in 0f..20f) {
                list += PidScanCandidate(PidDecode.VOLTAGE_AB, v, "%.2f V".format(v))
            }
        }
        PidValueDecoder.decode(data, PidDecode.PERCENT_A)?.let { v ->
            list += PidScanCandidate(PidDecode.PERCENT_A, v, "%.0f %%".format(v))
        }
        PidValueDecoder.decode(data, PidDecode.RAW_A)?.let { v ->
            list += PidScanCandidate(PidDecode.RAW_A, v, "A=%d".format(v.toInt()))
        }
        if (data.size >= 2) {
            PidValueDecoder.decode(data, PidDecode.RAW_AB)?.let { v ->
                list += PidScanCandidate(PidDecode.RAW_AB, v, "AB=%d".format(v.toInt()))
            }
        }
        return list
    }
}
