package com.wrxhud.obd

/**
 * Scripted ELM/STN + ECU for poller tests. Records every command sent.
 *
 * Mode 01 data comes from [pidData]; PIDs not in [supported] answer NO DATA.
 * Multi-PID requests answer in ATH0 / ATAL form (single frame, or "0:/1:" multi-frame).
 */
class FakeEcuLink(
    val supported: Set<Int> = DEFAULT_SUPPORTED,
    val pidData: MutableMap<Int, String> = DEFAULT_DATA.toMutableMap(),
    private val mode22: Map<Int, String> = emptyMap(),
    /** Number of commands after which [isOpen] turns false (checked by the poller per cycle). */
    private val closeAfter: Int = Int.MAX_VALUE,
    /** Hook to tamper with a response: (command, response) -> response. */
    var tamper: (String, String) -> String = { _, r -> r },
    /** When true, multi-PID requests are answered by a second ECU line too. */
    var secondEcu: Boolean = false,
    /** Multi-PID requests (more than one PID) answer NO DATA. */
    var rejectMultiPid: Boolean = false
) : ObdLink {
    val commands = mutableListOf<String>()
    private var open = true

    override val isOpen: Boolean get() = open

    fun close() { open = false }

    override fun sendCommand(command: String, waitForPrompt: Boolean, timeoutMs: Long): String {
        ObdReadOnlyGuard.assertAllowed(command)
        commands += command
        if (commands.size >= closeAfter) open = false
        return tamper(command, respond(command.trim().uppercase().replace(" ", "")))
    }

    private fun respond(c: String): String = when {
        c == "ATI" -> "ELM327 v1.4b"
        c == "ATDPN" -> "A6"
        c == "ATRV" -> "13.8V"
        c.startsWith("AT") || c.startsWith("ST") -> "OK"
        c.matches(Regex("^22[0-9A-F]{4}$")) -> {
            val did = c.substring(2).toInt(16)
            mode22[did] ?: "NO DATA"
        }
        c.startsWith("09") -> "NO DATA"
        c.startsWith("01") -> mode01(c)
        else -> "NO DATA"
    }

    private fun mode01(c: String): String {
        val body = c.substring(2)
        val pids = (0 until body.length / 2).map { body.substring(it * 2, it * 2 + 2).toInt(16) }
        if (pids.size == 1) {
            val pid = pids[0]
            if (pid % 0x20 == 0 && pid <= 0xA0) return supportLine(pid)
            val d = pidData[pid]
            return if (pid in supported && d != null) "41${"%02X".format(pid)}$d" else "NO DATA"
        }
        if (rejectMultiPid) return "NO DATA"
        val parts = pids.filter { it in supported && pidData[it] != null }
            .map { "%02X".format(it) + pidData[it] }
        if (parts.isEmpty()) return "NO DATA"
        val payload = "41" + parts.joinToString("")
        val bytes = payload.length / 2
        val first = if (bytes <= 7) {
            payload.chunked(2).joinToString(" ")
        } else {
            multiFrame(payload)
        }
        return if (secondEcu) first + "\r" + "41 0C 00 00" else first
    }

    private fun multiFrame(payloadHex: String): String {
        val bytes = payloadHex.chunked(2)
        val total = bytes.size
        val sb = StringBuilder("%03X\r".format(total))
        val firstChunk = bytes.take(6)
        sb.append("0: ").append(firstChunk.joinToString(" ")).append('\r')
        var idx = 1
        bytes.drop(6).chunked(7).forEach { chunk ->
            sb.append("${idx % 16}: ").append(chunk.joinToString(" ")).append('\r')
            idx++
        }
        return sb.toString()
    }

    private fun supportLine(base: Int): String {
        var bits = 0L
        for (p in supported) {
            if (p in (base + 1)..(base + 32)) bits = bits or (1L shl (32 - (p - base)))
        }
        return "41${"%02X".format(base)}${"%08X".format(bits)}"
    }

    companion object {
        val DEFAULT_SUPPORTED = setOf(
            0x01, 0x05, 0x0B, 0x0C, 0x0D, 0x0F, 0x11, 0x20,
            0x2F, 0x33, 0x34, 0x40, 0x42, 0x49, 0x5C
        )
        val DEFAULT_DATA = mapOf(
            0x01 to "00076100",
            0x05 to "7B",
            0x0B to "37",
            0x0C to "1AF8",
            0x0D to "3C",
            0x0F to "3A",
            0x11 to "40",
            0x2F to "80",
            0x33 to "65",
            0x34 to "80008000",
            0x42 to "35E8",
            0x49 to "30",
            0x5C to "5A"
        )
    }
}
