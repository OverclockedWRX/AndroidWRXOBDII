package com.wrxhud.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/** Backoff schedule 1, 2, 4, 8 s then capped at 15 s. [attempt] is 1-based. */
object ReconnectBackoff {
    const val MAX_MS = 15_000L
    fun delayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 4)
        return minOf(1_000L shl shift, MAX_MS)
    }
}

/** Why one connect-and-poll session ended. */
enum class SessionEnd {
    /** Link dropped, poller failed or connect failed: try again after a backoff. */
    RETRY,

    /** Nothing to retry (no permission, no adapter paired, simulator selected). */
    GIVE_UP
}

/**
 * Supervises connect-and-poll sessions. The session body (existing connect code plus the
 * poller) lives in the service; this only decides when to run it again. Cancelling the
 * calling coroutine (ACTION_STOP, simulator switch) ends the loop.
 */
class ReconnectLoop(
    private val onStatus: (String) -> Unit,
    private val backoff: (Int) -> Long = ReconnectBackoff::delayMs,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    /**
     * @param session runs one session; call `markConnected()` once the link is up so the
     * attempt counter resets for the next drop.
     */
    suspend fun run(session: suspend (markConnected: () -> Unit) -> SessionEnd) {
        var attempt = 0
        while (coroutineContext.isActive) {
            var connected = false
            val end = session { connected = true }
            if (end == SessionEnd.GIVE_UP) return
            if (connected) attempt = 0
            attempt++
            onStatus("Reconnecting… ($attempt)")
            pause(backoff(attempt))
        }
    }
}
