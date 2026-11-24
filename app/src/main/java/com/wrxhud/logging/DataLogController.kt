package com.wrxhud.logging

import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogSample(
    val snapshot: VehicleSnapshot,
    val motion: MotionReading,
    val speedLimit: SpeedLimitReading
)

/** Sink the controller writes to; [CsvDataLogger] in the app, a fake in tests. */
interface LogSink {
    val recording: StateFlow<Boolean>
    val activeFileName: StateFlow<String?>
    fun start(): String?
    fun stop(): String?
    fun append(sample: LogSample)
    fun flush()
}

/**
 * Runs the CSV log off the main thread: a sampler pushes frames into a bounded
 * channel every [intervalMs] and a writer coroutine drains it (flush every 10 rows).
 * [scope] must use an IO dispatcher.
 */
class DataLogController(
    private val sink: LogSink,
    private val scope: CoroutineScope,
    private val sample: () -> LogSample,
    private val onActiveChanged: (Boolean) -> Unit = {},
    private val intervalMs: Long = 100L
) {
    constructor(
        logger: CsvDataLogger,
        scope: CoroutineScope,
        sample: () -> LogSample,
        onActiveChanged: (Boolean) -> Unit = {}
    ) : this(CsvLogSink(logger), scope, sample, onActiveChanged)

    val recording: StateFlow<Boolean> get() = sink.recording
    val activeFileName: StateFlow<String?> get() = sink.activeFileName

    private var job: Job? = null

    @Synchronized
    fun toggle() {
        if (job?.isActive == true) stop() else start()
    }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            sink.start() ?: return@launch
            onActiveChanged(true)
            val channel = Channel<LogSample>(64, BufferOverflow.DROP_OLDEST)
            val writer = launch {
                var rows = 0
                for (s in channel) {
                    sink.append(s)
                    if (++rows % 10 == 0) sink.flush()
                }
            }
            try {
                while (isActive) {
                    channel.trySend(sample())
                    delay(intervalMs)
                }
            } finally {
                channel.close()
                withContext(NonCancellable) {
                    writer.join()
                    sink.stop()
                    onActiveChanged(false)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
    }
}

private class CsvLogSink(private val logger: CsvDataLogger) : LogSink {
    override val recording get() = logger.recording
    override val activeFileName get() = logger.activeFileName
    override fun start() = logger.start()
    override fun stop() = logger.stop()
    override fun append(sample: LogSample) = logger.append(sample.snapshot, sample.motion, sample.speedLimit)
    override fun flush() = logger.flush()
}
