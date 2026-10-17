package com.wrxhud.obd

import android.util.Log

/**
 * Read-only DTC queries (Modes 03 / 07 / 0A) plus user-initiated Mode 04 clear.
 * Must run on the OBD thread with the poller paused.
 */
class DtcReader(private val connection: ObdConnection) {
    companion object {
        private const val TAG = "DtcReader"
    }

    fun readAll(): DtcReadResult {
        var mil: Boolean? = null
        var countHint: Int? = null
        runCatching {
            val raw = connection.sendCommand(ObdCommands.mode01(0x01))
            val data = StnParser.parseMode01(raw, 0x01)
            if (data != null) {
                DtcParser.milAndCountFromPid01(data)?.let { (m, c) ->
                    mil = m
                    countHint = c
                }
            }
        }

        val stored = readBank(ObdCommands.MODE_03_STORED_DTCS, 0x43, DtcBank.STORED)
        val pending = readBank(ObdCommands.MODE_07_PENDING_DTCS, 0x47, DtcBank.PENDING)
        val permanent = readBank(ObdCommands.MODE_0A_PERMANENT_DTCS, 0x4A, DtcBank.PERMANENT)
        val codes = (stored + pending + permanent)
            .distinctBy { "${it.bank}:${it.code}" }
            .sortedWith(compareBy({ it.bank.ordinal }, { it.code }))

        Log.i(TAG, "DTC read: ${codes.size} codes (MIL=$mil countHint=$countHint)")
        val message = when {
            codes.isEmpty() && mil == false -> "No DTCs — MIL off"
            codes.isEmpty() -> "No DTCs reported"
            else -> "Found ${codes.size} code(s)"
        }
        return DtcReadResult(
            codes = codes,
            milOn = mil,
            confirmedCountHint = countHint,
            message = message
        )
    }

    /**
     * Clears stored emission-related DTCs (SAE Mode 04).
     * Does not reflash or write calibration — only the standard clear-codes service.
     */
    fun clearCodes(): Boolean {
        val raw = connection.sendCommand(ObdCommands.MODE_04_CLEAR_DTCS)
        val ok = !StnParser.isError(raw) &&
            (StnParser.normalize(raw).contains("44") || StnParser.normalize(raw).isNotEmpty())
        Log.i(TAG, "Clear DTCs -> ok=$ok raw=${StnParser.normalize(raw)}")
        return ok
    }

    private fun readBank(command: String, echo: Int, bank: DtcBank): List<DiagnosticTroubleCode> {
        return try {
            val raw = connection.sendCommand(command)
            DtcParser.parseModeResponse(raw, echo, bank)
        } catch (t: Throwable) {
            Log.w(TAG, "DTC bank ${bank.label} failed", t)
            emptyList()
        }
    }
}
