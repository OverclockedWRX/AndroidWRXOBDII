package com.wrxhud.telemetry

import com.wrxhud.gps.GpsReading
import com.wrxhud.vehicle.VehicleInfo
import com.wrxhud.vehicle.VehicleSnapshot
import com.wrxhud.vehicle.WrxProfile

/** The few preference fields the display merge depends on. */
data class MergePrefs(
    val useSimulator: Boolean = false,
    val thresholds: WrxProfile.AlarmThresholds = WrxProfile.AlarmThresholds()
)

/**
 * Pure merge of raw OBD/simulator telemetry with phone GPS into what the HUD
 * and Android Auto display. Same rules the ViewModel used to apply.
 */
object TelemetryMerge {
    fun displaySnapshot(raw: VehicleSnapshot, prefs: MergePrefs, gps: GpsReading): VehicleSnapshot {
        val hasObdStream = if (prefs.useSimulator) {
            raw.usingSimulator
        } else {
            raw.obdConnected && !raw.usingSimulator
        }
        return if (hasObdStream) {
            raw.copy(
                gpsSpeedMps = gps.speedMps ?: raw.gpsSpeedMps,
                gpsHasFix = gps.hasFix,
                usingSimulator = prefs.useSimulator && raw.usingSimulator
            )
        } else {
            raw.phoneOnly(
                gpsSpeedMps = gps.speedMps,
                gpsHasFix = gps.hasFix,
                thresholds = prefs.thresholds,
                expectObd = !prefs.useSimulator
            )
        }
    }

    fun displayStatus(raw: VehicleSnapshot, prefs: MergePrefs, status: String): String = when {
        prefs.useSimulator && raw.usingSimulator -> status
        prefs.useSimulator -> "Starting simulator…"
        raw.obdConnected -> status
        status.isNotBlank() && status != "Stopped" && status != "Idle" -> status
        else -> "No OBD — GPS only"
    }

    fun displayVehicleInfo(info: VehicleInfo, snapshot: VehicleSnapshot): VehicleInfo =
        info.copy(oilTempSource = info.oilTempSource ?: snapshot.oilTempSource)
}
