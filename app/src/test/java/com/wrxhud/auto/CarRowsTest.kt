package com.wrxhud.auto

import com.google.common.truth.Truth.assertThat
import com.wrxhud.settings.HudPreferences
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarRowsTest {
    private val prefs = HudPreferences()

    @Test
    fun rowsReflectSnapshotAndEndWithStatus() {
        val rows = CarRows.build(VehicleSnapshot(rpm = 3200f, obdConnected = true), prefs)
        assertThat(rows.first { it.title == "RPM" }.text).contains("3200")
        assertThat(rows.last().title).isEqualTo("STATUS")
        assertThat(rows.last().text).contains("OBD OK")
    }

    @Test
    fun refreshIsThrottledAndDistinct() = runTest {
        // 100 updates over 1 s (10 ms apart), alternating between 5 distinct RPM values.
        val source = flow {
            repeat(100) { i ->
                emit(CarRows.build(VehicleSnapshot(rpm = 1000f + (i / 20) * 100f), prefs))
                delay(10)
            }
        }
        val emissions = source.carRefresh(periodMs = 400).toList()
        // 1 s at 400 ms period: at most 3 invalidations, never one per update.
        assertThat(emissions.size).isAtMost(3)
        assertThat(emissions.size).isAtLeast(1)
    }

    @Test
    fun identicalRowsNeverRefresh() = runTest {
        val same = CarRows.build(VehicleSnapshot(rpm = 2000f), prefs)
        val source = flow { repeat(50) { emit(same); delay(10) } }
        assertThat(source.carRefresh(100).toList().size).isEqualTo(1)
    }
}
