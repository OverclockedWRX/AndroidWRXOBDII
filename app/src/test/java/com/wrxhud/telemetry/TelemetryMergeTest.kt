package com.wrxhud.telemetry

import com.google.common.truth.Truth.assertThat
import com.wrxhud.gps.GpsReading
import com.wrxhud.vehicle.VehicleInfo
import com.wrxhud.vehicle.VehicleSnapshot
import org.junit.Test

class TelemetryMergeTest {
    private val live = MergePrefs(useSimulator = false)
    private val sim = MergePrefs(useSimulator = true)

    @Test
    fun obdStreamKeepsObdFieldsAndTakesGps() {
        val raw = VehicleSnapshot(rpm = 3000f, coolantC = 90f, obdConnected = true)
        val out = TelemetryMerge.displaySnapshot(raw, live, GpsReading(speedMps = 20f, hasFix = true))
        assertThat(out.rpm).isEqualTo(3000f)
        assertThat(out.gpsSpeedMps).isEqualTo(20f)
        assertThat(out.gpsHasFix).isTrue()
    }

    @Test
    fun phoneOnlyWhenObdDownKeepsGpsAndClearsObd() {
        val raw = VehicleSnapshot(rpm = 3000f, obdConnected = false)
        val out = TelemetryMerge.displaySnapshot(raw, live, GpsReading(speedMps = 12f, hasFix = true))
        assertThat(out.rpm).isNull()
        assertThat(out.gpsSpeedMps).isEqualTo(12f)
        assertThat(out.obdConnected).isFalse()
        assertThat(out.alarms.linkLost).isTrue()
    }

    @Test
    fun simulatorFramesPassThroughOnlyInSimulatorMode() {
        val raw = VehicleSnapshot(rpm = 4000f, usingSimulator = true, obdConnected = true)
        assertThat(TelemetryMerge.displaySnapshot(raw, sim, GpsReading()).rpm).isEqualTo(4000f)
        assertThat(TelemetryMerge.displaySnapshot(raw, live, GpsReading()).rpm).isNull()
    }

    @Test
    fun statusRules() {
        val up = VehicleSnapshot(obdConnected = true)
        val down = VehicleSnapshot()
        assertThat(TelemetryMerge.displayStatus(up, live, "Connected: X")).isEqualTo("Connected: X")
        assertThat(TelemetryMerge.displayStatus(down, live, "Reconnecting… (2)")).isEqualTo("Reconnecting… (2)")
        assertThat(TelemetryMerge.displayStatus(down, live, "Idle")).isEqualTo("No OBD — GPS only")
        assertThat(TelemetryMerge.displayStatus(down, live, "Stopped")).isEqualTo("No OBD — GPS only")
        assertThat(TelemetryMerge.displayStatus(down, sim, "Simulator")).isEqualTo("Starting simulator…")
        assertThat(TelemetryMerge.displayStatus(VehicleSnapshot(usingSimulator = true), sim, "Simulator"))
            .isEqualTo("Simulator")
    }

    @Test
    fun vehicleInfoFallsBackToSnapshotOilSource() {
        val info = TelemetryMerge.displayVehicleInfo(VehicleInfo(), VehicleSnapshot(oilTempSource = "01 5C"))
        assertThat(info.oilTempSource).isEqualTo("01 5C")
    }
}
