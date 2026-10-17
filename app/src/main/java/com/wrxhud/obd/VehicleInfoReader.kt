package com.wrxhud.obd

import android.util.Log
import com.wrxhud.vehicle.VehicleInfo
import com.wrxhud.vehicle.WrxProfile

/**
 * One-shot vehicle identity reads (Mode 09 / UDS F190) plus adapter AT queries.
 * All commands pass through [ObdReadOnlyGuard] via [ObdConnection.sendCommand].
 */
object VehicleInfoReader {
    private const val TAG = "VehicleInfoReader"
    private const val VIN_TIMEOUT_MS = 4_000L
    private const val UDS_VIN_DID = 0xF190

    fun read(
        connection: ObdConnection,
        oilTempSource: String? = null,
        mapSource: String? = null
    ): VehicleInfo {
        val adapterId = connection.sendCommand("ATI").trim().ifBlank { null }
        val protocol = connection.sendCommand("ATDPN").trim().ifBlank { null }
            ?: connection.sendCommand("ATDP").trim().ifBlank { null }
        val voltage = connection.sendCommand("ATRV").trim().ifBlank { null }

        var vin: String? = null
        var vinSource: String? = null

        val mode09Vin = StnParser.decodeVinMode09(
            connection.sendCommand(ObdCommands.mode09(0x02), timeoutMs = VIN_TIMEOUT_MS)
        )
        if (mode09Vin != null) {
            vin = mode09Vin
            vinSource = "09 02"
        } else {
            val udsRaw = connection.sendCommand(
                ObdCommands.mode22(UDS_VIN_DID),
                timeoutMs = VIN_TIMEOUT_MS
            )
            val udsData = StnParser.parseMode22(udsRaw, UDS_VIN_DID)
            val udsVin = udsData?.let { StnParser.decodeAsciiField(it) }
            if (udsVin != null && looksLikeVin(udsVin)) {
                vin = udsVin
                vinSource = "22 F190"
            }
        }

        val calId = StnParser.decodeAsciiInfoResponse(
            connection.sendCommand(ObdCommands.mode09(0x04), timeoutMs = VIN_TIMEOUT_MS),
            mode = 0x49,
            pid = 0x04
        )
        val ecuName = StnParser.decodeAsciiInfoResponse(
            connection.sendCommand(ObdCommands.mode09(0x0A), timeoutMs = VIN_TIMEOUT_MS),
            mode = 0x49,
            pid = 0x0A
        )

        Log.i(
            TAG,
            "Vehicle info: vin=${vin ?: "n/a"} ($vinSource) cal=${calId ?: "n/a"} ecu=${ecuName ?: "n/a"}"
        )

        return VehicleInfo(
            vin = vin,
            vinSource = vinSource,
            calibrationId = calId,
            ecuName = ecuName,
            adapterId = adapterId?.take(48),
            protocol = protocol?.take(32),
            adapterVoltage = voltage?.take(16),
            oilTempSource = oilTempSource,
            mapSource = mapSource,
            profileModelYears = WrxProfile.MODEL_YEARS,
            profileEngine = WrxProfile.ENGINE,
            statusMessage = when {
                vin != null -> null
                else -> "VIN not reported by ECU"
            }
        )
    }

    fun simulatorPlaceholder(
        oilTempSource: String? = "sim",
        mapSource: String? = "sim"
    ): VehicleInfo = VehicleInfo(
        vin = null,
        vinSource = null,
        calibrationId = null,
        ecuName = "Simulator",
        adapterId = "SIM",
        protocol = "n/a",
        adapterVoltage = null,
        oilTempSource = oilTempSource,
        mapSource = mapSource,
        statusMessage = "Simulator — no VIN"
    )

    private fun looksLikeVin(s: String): Boolean {
        val t = s.trim()
        return t.length in 11..20 && t.all { it.isLetterOrDigit() }
    }
}
