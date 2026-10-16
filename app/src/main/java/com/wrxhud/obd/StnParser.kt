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

    /** Mode 01 PID A6: odometer in km. */
    fun decodeOdometerKm(data: ByteArray): Float? {
        if (data.size < 4) return null
        val raw = (
            ((data[0].toInt() and 0xFF).toLong() shl 24) or
                ((data[1].toInt() and 0xFF).toLong() shl 16) or
                ((data[2].toInt() and 0xFF).toLong() shl 8) or
                (data[3].toInt() and 0xFF).toLong()
            )
        val km = raw / 10f
        // Reject obviously invalid / unsupported padding
        if (km < 0f || km > 2_000_000f) return null
        return km
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
}
