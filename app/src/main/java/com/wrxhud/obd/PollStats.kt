package com.wrxhud.obd

import com.wrxhud.settings.PollingMode

/** Live poll-rate readout (shown in Settings > Vehicle info). */
data class PollStats(
    val mode: PollingMode? = null,
    val rpmPerSec: Float = 0f,
    val cyclesPerSec: Float = 0f,
    val requestsPerSec: Float = 0f,
    val avgLatencyMs: Float = 0f,
    val maxLatencyMs: Float = 0f
)

/**
 * Counts requests / RPM samples / cycles. [tick] returns a fresh [PollStats] about once a
 * second, [logLine] a summary roughly every [logEveryMs] (for logcat tag ObdPoller).
 */
class PollStatsMeter(
    private val windowMs: Long = 1000L,
    private val logEveryMs: Long = 10_000L
) {
    private class Acc {
        var requests = 0
        var rpm = 0
        var cycles = 0
        var latencyNanos = 0L
        var maxNanos = 0L
        fun reset() { requests = 0; rpm = 0; cycles = 0; latencyNanos = 0; maxNanos = 0 }
    }

    private val win = Acc()
    private val log = Acc()
    private var winStart: Long? = null
    private var logStart: Long? = null

    fun request(nanos: Long) {
        for (a in arrayOf(win, log)) {
            a.requests++
            a.latencyNanos += nanos
            if (nanos > a.maxNanos) a.maxNanos = nanos
        }
    }

    fun rpmSample() { win.rpm++; log.rpm++ }
    fun cycle() { win.cycles++; log.cycles++ }

    fun tick(nowMs: Long, mode: PollingMode): PollStats? {
        val start = winStart ?: run { winStart = nowMs; logStart = nowMs; return null }
        val elapsed = nowMs - start
        if (elapsed < windowMs) return null
        val s = stats(win, elapsed, mode)
        win.reset()
        winStart = nowMs
        return s
    }

    /** Non-null when a 10 s summary is due (and resets that window). */
    fun logLine(nowMs: Long, mode: PollingMode): String? {
        val start = logStart ?: return null
        val elapsed = nowMs - start
        if (elapsed < logEveryMs) return null
        val s = stats(log, elapsed, mode)
        log.reset()
        logStart = nowMs
        return "mode=${mode.name} rpm=%.1f/s cycles=%.1f/s req=%.1f/s latency avg=%.0fms max=%.0fms"
            .format(s.rpmPerSec, s.cyclesPerSec, s.requestsPerSec, s.avgLatencyMs, s.maxLatencyMs)
    }

    private fun stats(a: Acc, elapsedMs: Long, mode: PollingMode): PollStats {
        val sec = elapsedMs / 1000f
        return PollStats(
            mode = mode,
            rpmPerSec = a.rpm / sec,
            cyclesPerSec = a.cycles / sec,
            requestsPerSec = a.requests / sec,
            avgLatencyMs = if (a.requests == 0) 0f else a.latencyNanos / a.requests / 1_000_000f,
            maxLatencyMs = a.maxNanos / 1_000_000f
        )
    }
}
