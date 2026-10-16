package com.wrxhud.gps

/**
 * Minimal NMEA-0183 parser for GPS speed / fix from Bluetooth or USB dongles.
 */
object NmeaParser {
    private const val KNOTS_TO_MPS = 0.514444f

    data class Update(
        val speedMps: Float? = null,
        val hasFix: Boolean? = null
    )

    fun parseLine(raw: String): Update? {
        val line = raw.trim().removePrefix("\u0000")
        if (!line.startsWith("$") || line.length < 6) return null
        val star = line.lastIndexOf('*')
        val body = if (star > 0) line.substring(0, star) else line
        val fields = body.split(',')
        val type = fields[0].uppercase()
        return when {
            type.endsWith("RMC") -> parseRmc(fields)
            type.endsWith("GGA") -> parseGga(fields)
            type.endsWith("VTG") -> parseVtg(fields)
            else -> null
        }
    }

    private fun parseRmc(fields: List<String>): Update? {
        if (fields.size < 8) return null
        val valid = fields[2].equals("A", ignoreCase = true)
        val knots = fields[7].toFloatOrNull()
        val speed = if (valid && knots != null) knots * KNOTS_TO_MPS else null
        return Update(speedMps = speed, hasFix = valid)
    }

    private fun parseGga(fields: List<String>): Update? {
        if (fields.size < 7) return null
        val quality = fields[6].toIntOrNull() ?: 0
        return Update(hasFix = quality > 0)
    }

    private fun parseVtg(fields: List<String>): Update? {
        // $GPVTG,...,x.x,N,x.x,K,... — prefer knots field (index 5) or km/h (index 7)
        if (fields.size < 6) return null
        val knots = fields.getOrNull(5)?.toFloatOrNull()
        if (knots != null) return Update(speedMps = knots * KNOTS_TO_MPS)
        val kmh = fields.getOrNull(7)?.toFloatOrNull()
        return if (kmh != null) Update(speedMps = kmh / 3.6f) else null
    }
}
