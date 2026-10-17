package com.wrxhud.obd

enum class DtcBank(val label: String) {
    STORED("Stored"),
    PENDING("Pending"),
    PERMANENT("Permanent")
}

data class DiagnosticTroubleCode(
    val code: String,
    val bank: DtcBank,
    val description: String = DtcDescriptions.lookup(code)
)

data class DtcReadResult(
    val codes: List<DiagnosticTroubleCode> = emptyList(),
    val milOn: Boolean? = null,
    val confirmedCountHint: Int? = null,
    val message: String? = null
)

/**
 * Small built-in glossary for common powertrain codes. Unknown codes still show the SAE id.
 */
object DtcDescriptions {
    private val MAP: Map<String, String> = mapOf(
        "P0000" to "No fault stored (placeholder)",
        "P0101" to "MAF circuit range/performance",
        "P0102" to "MAF circuit low",
        "P0103" to "MAF circuit high",
        "P0106" to "MAP circuit range/performance",
        "P0107" to "MAP circuit low",
        "P0108" to "MAP circuit high",
        "P0111" to "IAT circuit range/performance",
        "P0112" to "IAT circuit low",
        "P0113" to "IAT circuit high",
        "P0116" to "ECT circuit range/performance",
        "P0117" to "ECT circuit low",
        "P0118" to "ECT circuit high",
        "P0121" to "TPS/pedal A range/performance",
        "P0122" to "TPS/pedal A low",
        "P0123" to "TPS/pedal A high",
        "P0171" to "System too lean (Bank 1)",
        "P0172" to "System too rich (Bank 1)",
        "P0174" to "System too lean (Bank 2)",
        "P0175" to "System too rich (Bank 2)",
        "P0300" to "Random/multiple cylinder misfire",
        "P0301" to "Cylinder 1 misfire",
        "P0302" to "Cylinder 2 misfire",
        "P0303" to "Cylinder 3 misfire",
        "P0304" to "Cylinder 4 misfire",
        "P0327" to "Knock sensor 1 circuit low",
        "P0328" to "Knock sensor 1 circuit high",
        "P0335" to "Crankshaft position sensor A circuit",
        "P0340" to "Camshaft position sensor A circuit",
        "P0420" to "Catalyst efficiency below threshold (Bank 1)",
        "P0430" to "Catalyst efficiency below threshold (Bank 2)",
        "P0441" to "Evap incorrect purge flow",
        "P0442" to "Evap small leak",
        "P0455" to "Evap large leak",
        "P0456" to "Evap very small leak",
        "P0500" to "Vehicle speed sensor A",
        "P0506" to "Idle control RPM lower than expected",
        "P0507" to "Idle control RPM higher than expected",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0606" to "ECM/PCM processor fault",
        "P0700" to "Transmission control system (MIL request)",
        "P1101" to "Subaru MAF / intake performance (mfr)",
        "P1235" to "Subaru fuel pump / pressure (mfr)",
        "P150A" to "Subaru idle / ISC related (mfr)",
        "C0035" to "Left front wheel speed sensor",
        "C0036" to "Right front wheel speed sensor",
        "B0001" to "Driver frontal airbag (generic)",
        "U0100" to "Lost communication with ECM/PCM",
        "U0101" to "Lost communication with TCM",
        "U0155" to "Lost communication with instrument cluster"
    )

    fun lookup(code: String): String = MAP[code.uppercase()] ?: "No description on file"
}
