package com.wrxhud.obd

/**
 * Commands this app may send. Enforced at send time by [ObdReadOnlyGuard].
 *
 * Vehicle bus: Mode 01 / 03 / 07 / 09 / 0A / UDS 0x22 reads, plus Mode 04 clear-DTCs
 * only when the user confirms Clear on the Codes settings page.
 * Never: session control, write DID, IO control, routines, memory download.
 */
object ObdCommands {
    val INIT_SEQUENCE = listOf(
        "ATZ",
        "ATE0",
        "ATL0",
        "ATS0",
        "ATH0",
        "ATAL", // allow long / multi-frame responses (still adapter-local)
        "ATAT2",
        "ATSP6",
        "STPTO 32"
    )

    /** SAE Mode 03 — request stored emission DTCs (read). */
    const val MODE_03_STORED_DTCS = "03"

    /** SAE Mode 07 — request pending DTCs (read). */
    const val MODE_07_PENDING_DTCS = "07"

    /** SAE Mode 0A — request permanent DTCs (read). */
    const val MODE_0A_PERMANENT_DTCS = "0A"

    /**
     * SAE Mode 04 — clear/reset emission DTCs.
     * Standard diagnostic clear only — not ECU reprogramming.
     */
    const val MODE_04_CLEAR_DTCS = "04"

    /** SAE Mode 01 — show current data (read). */
    fun mode01(pid: Int): String = "01%02X".format(pid)

    /**
     * Multi-PID Mode 01 request (up to 6 PIDs on CAN), e.g. `010C0D11`.
     * [countHint] is the ELM327/STN response-count digit (`010C1`): the adapter returns
     * after that many ECU replies instead of waiting out its timeout.
     */
    fun mode01Multi(pids: List<Int>, countHint: Int? = null): String {
        require(pids.size in 1..MAX_MODE01_BATCH) { "1..$MAX_MODE01_BATCH PIDs per request" }
        return buildString {
            append("01")
            pids.forEach { append("%02X".format(it)) }
            if (countHint != null) append("%X".format(countHint))
        }
    }

    /** Most PIDs one Mode 01 request may carry (SAE J1979). */
    const val MAX_MODE01_BATCH = 6

    /** SAE Mode 09 — vehicle information (VIN, CALID, ECU name) — read. */
    fun mode09(pid: Int): String = "09%02X".format(pid)

    /** UDS ReadDataByIdentifier (service 0x22) — read. */
    fun mode22(did: Int): String = "22%04X".format(did)

    /** Adapter header for the next request (does not write ECU memory). */
    fun setHeader(header: Int): String = "ATSH%03X".format(header)
}
