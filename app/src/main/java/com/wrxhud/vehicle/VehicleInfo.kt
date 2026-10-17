package com.wrxhud.vehicle

/**
 * Static / infrequently changing vehicle identity pulled from OBD (Mode 09 / UDS)
 * plus adapter and profile metadata for the Settings → Vehicle Info tab.
 */
data class VehicleInfo(
    val vin: String? = null,
    val vinSource: String? = null,
    val calibrationId: String? = null,
    val ecuName: String? = null,
    val adapterId: String? = null,
    val protocol: String? = null,
    val adapterVoltage: String? = null,
    val oilTempSource: String? = null,
    val mapSource: String? = null,
    val profileModelYears: String = WrxProfile.MODEL_YEARS,
    val profileEngine: String = WrxProfile.ENGINE,
    val statusMessage: String? = null
)
