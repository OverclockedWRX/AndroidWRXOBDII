package com.wrxhud.obd

import android.util.Log

/**
 * Hard gate for what may leave this app onto the OBD-II / CAN adapter.
 *
 * Allowed:
 * - ELM/STN adapter setup (`AT*`, `ST*`)
 * - SAE Mode 01 current data (`01xx`)
 * - SAE Mode 03 / 07 / 0A DTC reads
 * - SAE Mode 04 clear DTCs (user-confirmed code reader only — not calibration write)
 * - UDS ReadDataByIdentifier (`22xxxx`)
 *
 * Blocked: diagnostic session control, write DID, IO control, routines,
 * memory read/write/download, and any unknown payload.
 */
object ObdReadOnlyGuard {
    private const val TAG = "ObdReadOnly"

    fun assertAllowed(command: String) {
        val compact = command.trim().uppercase().replace(" ", "")
        if (compact.isEmpty()) {
            refuse(command, "empty")
        }
        if (isAllowed(compact)) return
        refuse(command, "not on allowlist")
    }

    fun isAllowed(compactUpperNoSpaces: String): Boolean {
        val c = compactUpperNoSpaces
        return when {
            // --- Adapter-local ---
            c == "ATZ" -> true
            c == "ATE0" || c == "ATE1" -> true
            c == "ATL0" || c == "ATL1" -> true
            c == "ATS0" || c == "ATS1" -> true
            c == "ATH0" || c == "ATH1" -> true
            c == "ATAT0" || c == "ATAT1" || c == "ATAT2" -> true
            c == "ATAL" || c == "ATAL0" || c == "ATAL1" -> true
            c.matches(Regex("^ATSP[0-9A-C]$")) -> true
            c.matches(Regex("^ATSH[0-9A-F]{3}$")) -> true
            c == "ATRV" || c == "ATI" || c == "ATDP" || c == "ATDPN" -> true
            c.matches(Regex("^STPTO\\d+$")) -> true

            // --- Vehicle bus: diagnostic reads ---
            c.matches(Regex("^01[0-9A-F]{2}$")) -> true
            c == "03" || c == "07" || c == "0A" -> true
            c.matches(Regex("^22[0-9A-F]{4}$")) -> true

            // --- Vehicle bus: standard clear-codes (code reader) ---
            c == "04" -> true

            else -> false
        }
    }

    private fun refuse(command: String, reason: String): Nothing {
        runCatching { Log.e(TAG, "REFUSED OBD/CAN command ($reason): [$command]") }
        throw SecurityException("Read-only OBD guard refused command: $command ($reason)")
    }
}
