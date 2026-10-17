package com.wrxhud.obd

/**
 * Parses SAE Mode 03 / 07 / 0A DTC replies from ELM/STN ASCII.
 *
 * Response payload shape: `4X` then zero or more 2-byte DTC words
 * (and sometimes an optional count byte that we skip when it isn't a valid DTC).
 */
object DtcParser {
    fun parseModeResponse(raw: String, expectedModeEcho: Int, bank: DtcBank): List<DiagnosticTroubleCode> {
        if (StnParser.isError(raw)) return emptyList()
        val bytes = StnParser.parseHexBytes(raw) ?: return emptyList()
        if (bytes.isEmpty()) return emptyList()

        // Find the mode echo (0x43 / 0x47 / 0x4A). Controllers may prepend CAN headers
        // when ATH1 is on; we search for the service echo.
        var idx = bytes.indexOfFirst { (it.toInt() and 0xFF) == expectedModeEcho }
        if (idx < 0) return emptyList()
        idx++ // skip mode byte

        val codes = linkedSetOf<String>()
        // Walk remaining bytes as DTC pairs. Skip lone trailing bytes.
        while (idx + 1 < bytes.size) {
            val hi = bytes[idx].toInt() and 0xFF
            val lo = bytes[idx + 1].toInt() and 0xFF
            idx += 2
            if (hi == 0 && lo == 0) continue // padding / empty slot
            codes += formatDtc(hi, lo)
        }
        return codes.map { DiagnosticTroubleCode(code = it, bank = bank) }
    }

    fun formatDtc(hi: Int, lo: Int): String {
        val system = when ((hi shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val d1 = (hi shr 4) and 0x03
        val d2 = hi and 0x0F
        val d3 = (lo shr 4) and 0x0F
        val d4 = lo and 0x0F
        return "%c%X%X%X%X".format(system, d1, d2, d3, d4)
    }

    /** Mode 01 PID 01: MIL + confirmed DTC count in the low 7 bits of A. */
    fun milAndCountFromPid01(data: ByteArray): Pair<Boolean, Int>? {
        if (data.isEmpty()) return null
        val a = data[0].toInt() and 0xFF
        return ((a and 0x80) != 0) to (a and 0x7F)
    }
}
