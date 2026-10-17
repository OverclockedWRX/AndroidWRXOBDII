package com.wrxhud.obd

/**
 * Parses ELM327 / STN ASCII replies from the OBDLink MX+.
 */
object StnParser {
    fun normalize(raw: String): String {
        return raw
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .replace("SEARCHING...", "", ignoreCase = true)
            .replace(" ", "")
            .trim()
            .uppercase()
    }

    fun isError(raw: String): Boolean {
        val n = normalize(raw)
        return n.isEmpty() ||
            n.contains("NODATA") ||
            n.contains("ERROR") ||
            n.contains("UNABLETOCONNECT") ||
            n.contains("BUSINIT") ||
            n.contains("?") ||
            n == "STOPPED"
    }

    fun parseHexBytes(raw: String): ByteArray? {
        val n = normalize(raw)
        if (isError(raw) || n.length < 2 || n.length % 2 != 0) return null
        if (!n.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return ByteArray(n.length / 2) { i ->
            n.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** Mode 01 response: 41 <pid> <data...> */
    fun parseMode01(raw: String, expectedPid: Int): ByteArray? {
        val bytes = parseHexBytes(raw) ?: return null
        if (bytes.size < 3) return null
        val mode = bytes[0].toInt() and 0xFF
        val pid = bytes[1].toInt() and 0xFF
        if (mode != 0x41 || pid != expectedPid) return null
        return bytes.copyOfRange(2, bytes.size)
    }

    /** Mode 22 response: 62 <did_hi> <did_lo> <data...> */
    fun parseMode22(raw: String, expectedDid: Int): ByteArray? {
        val bytes = parseHexBytes(raw) ?: return null
        if (bytes.size < 3) return null
        val mode = bytes[0].toInt() and 0xFF
        val did = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        if (mode != 0x62 || did != expectedDid) return null
        return bytes.copyOfRange(3, bytes.size)
    }

    fun decodeRpm(data: ByteArray): Float? {
        if (data.size < 2) return null
        val a = data[0].toInt() and 0xFF
        val b = data[1].toInt() and 0xFF
        return ((a * 256) + b) / 4f
    }

    fun decodeTempC(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        return (data[0].toInt() and 0xFF) - 40f
    }

    fun decodeSpeedKph(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        return (data[0].toInt() and 0xFF).toFloat()
    }

    fun decodePercent(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        return (data[0].toInt() and 0xFF) * 100f / 255f
    }

    fun decodePressureKpa(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        return (data[0].toInt() and 0xFF).toFloat()
    }

    /**
     * SAE Mode 01 PID 0x87 — Absolute Intake Manifold Pressure.
     * A = supported-sensor bitmask; each present sensor is 2 bytes × 0.03125 kPa.
     * Returns sensor A when bit 0 is set (typical single-MAP layout).
     */
    fun decodeAbsoluteMapKpa(data: ByteArray): Float? {
        if (data.size < 3) return null
        val support = data[0].toInt() and 0xFF
        if (support and 0x01 == 0) return null
        val a = data[1].toInt() and 0xFF
        val b = data[2].toInt() and 0xFF
        val kpa = ((a * 256) + b) * 0.03125f
        if (kpa !in 5f..350f) return null
        return kpa
    }

    fun decodeVoltage(data: ByteArray): Float? {
        if (data.size < 2) return null
        val a = data[0].toInt() and 0xFF
        val b = data[1].toInt() and 0xFF
        return ((a * 256) + b) / 1000f
    }

    fun decodeMil(data: ByteArray): Boolean? {
        if (data.isEmpty()) return null
        return (data[0].toInt() and 0x80) != 0
    }

    /**
     * Mode 01 PID 0x34 / 0x44: equivalence ratio λ = (A×256+B) / 32768.
     * Gasoline AFR = λ × [stoichAfr] (typically 14.7).
     */
    fun decodeEquivalenceRatio(data: ByteArray): Float? {
        if (data.size < 2) return null
        val a = data[0].toInt() and 0xFF
        val b = data[1].toInt() and 0xFF
        val lambda = ((a * 256) + b) / 32768f
        if (lambda <= 0f || lambda > 2.5f) return null
        return lambda
    }

    fun decodeAfr(data: ByteArray, stoichAfr: Float = 14.7f): Float? {
        val lambda = decodeEquivalenceRatio(data) ?: return null
        return lambda * stoichAfr
    }

    fun decodeSupportedPidBitmask(data: ByteArray): Set<Int> {
        if (data.size < 4) return emptySet()
        val supported = mutableSetOf<Int>()
        val bits = (
            ((data[0].toInt() and 0xFF).toLong() shl 24) or
                ((data[1].toInt() and 0xFF).toLong() shl 16) or
                ((data[2].toInt() and 0xFF).toLong() shl 8) or
                (data[3].toInt() and 0xFF).toLong()
            )
        for (i in 0 until 32) {
            val mask = 1L shl (31 - i)
            if (bits and mask != 0L) {
                supported.add(i + 1)
            }
        }
        return supported
    }

    /** Printable ASCII from raw DID / info bytes (strip NULs and non-printables). */
    fun decodeAsciiField(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val s = buildString {
            for (b in data) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c in ' '..'~') append(c)
            }
        }.trim()
        return s.ifBlank { null }
    }

    /**
     * Mode 09 PID 02 VIN. ELM may return multi-frame `49 02 NN <ascii…>` lines.
     * Accepts 11–20 alphanumeric chars (17 typical for North America).
     */
    fun decodeVinMode09(raw: String): String? {
        if (isError(raw)) return null
        val payload = extractMode09Payload(raw, pid = 0x02) ?: return null
        val vin = decodeAsciiField(payload)?.filter { it.isLetterOrDigit() }
            ?: return null
        return vin.takeIf { it.length in 11..20 }
    }

    /**
     * Mode 09 ASCII info (CALID 04, ECU name 0A, etc.): strip `49 <pid> <msg#>` framing.
     */
    fun decodeAsciiInfoResponse(raw: String, mode: Int, pid: Int): String? {
        if (isError(raw)) return null
        val payload = extractMode09Payload(raw, pid = pid) ?: run {
            // Some adapters return bare ASCII after the mode/pid header in hex dump form.
            val bytes = parseHexBytes(raw) ?: return null
            if (bytes.size < 3) return null
            if ((bytes[0].toInt() and 0xFF) != mode) return null
            if ((bytes[1].toInt() and 0xFF) != pid) return null
            return decodeAsciiField(bytes.copyOfRange(2, bytes.size))
        }
        return decodeAsciiField(payload)
    }

    /**
     * Collects data bytes after each `49 <pid> <frame>` triplet in a multi-line ELM reply.
     */
    private fun extractMode09Payload(raw: String, pid: Int): ByteArray? {
        val hex = raw.uppercase()
            .replace(Regex("SEARCHING\\.\\.\\.", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^0-9A-F]"), "")
        if (hex.length < 6 || hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val out = ArrayList<Byte>()
        var i = 0
        while (i + 2 < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 == 0x49 && b1 == pid) {
                // 49 <pid> <msg_count_or_index> <data…>
                i += 3
                while (i < bytes.size) {
                    val next = bytes[i].toInt() and 0xFF
                    val next1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
                    if (next == 0x49 && next1 == pid) break
                    out.add(bytes[i])
                    i++
                }
            } else {
                i++
            }
        }
        return if (out.isEmpty()) null else out.toByteArray()
    }
}
