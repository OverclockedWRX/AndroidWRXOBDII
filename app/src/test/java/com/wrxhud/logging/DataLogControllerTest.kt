package com.wrxhud.logging

import com.google.common.truth.Truth.assertThat
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class DataLogControllerTest {
    private class FakeSink : LogSink {
        val rec = MutableStateFlow(false)
        val name = MutableStateFlow<String?>(null)
        override val recording: StateFlow<Boolean> = rec
        override val activeFileName: StateFlow<String?> = name
        val rows = CopyOnWriteArrayList<LogSample>()
        val threads = CopyOnWriteArrayList<String>()
        @Volatile var flushes = 0
        @Volatile var stops = 0
        override fun start(): String { rec.value = true; name.value = "x.csv"; return "x.csv" }
        override fun stop(): String? { stops++; rec.value = false; name.value = null; return "x.csv" }
        override fun append(sample: LogSample) { rows += sample; threads += Thread.currentThread().name }
        override fun flush() { flushes++ }
    }

    @Test
    fun writesOffCallerThreadAndStopsCleanly() = runBlocking {
        val sink = FakeSink()
        val active = mutableListOf<Boolean>()
        val ctl = DataLogController(
            sink = sink,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            sample = { LogSample(VehicleSnapshot(rpm = 1f), MotionReading(), SpeedLimitReading()) },
            onActiveChanged = { synchronized(active) { active += it } },
            intervalMs = 5
        )
        ctl.toggle()
        withTimeout(3000) { while (sink.rows.size < 25) delay(10) }
        ctl.toggle()
        withTimeout(3000) { while (sink.rec.value) delay(10) }
        assertThat(sink.stops).isEqualTo(1)
        assertThat(sink.flushes).isAtLeast(2)
        assertThat(sink.threads.none { it == Thread.currentThread().name }).isTrue()
        assertThat(synchronized(active) { active.toList() }).isEqualTo(listOf(true, false))
    }
}
