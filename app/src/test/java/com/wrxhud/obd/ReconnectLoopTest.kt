package com.wrxhud.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectLoopTest {
    @Test
    fun backoffScheduleCapsAtFifteenSeconds() {
        val got = (1..8).map { ReconnectBackoff.delayMs(it) }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L, 15000L, 15000L), got)
    }

    @Test
    fun retriesWithStatusAndBackoffUntilGiveUp() = runTest {
        val statuses = mutableListOf<String>()
        val waits = mutableListOf<Long>()
        var calls = 0
        val loop = ReconnectLoop(
            onStatus = { statuses += it },
            pause = { waits += it }
        )
        loop.run {
            calls++
            if (calls < 4) SessionEnd.RETRY else SessionEnd.GIVE_UP
        }
        assertEquals(4, calls)
        assertEquals(listOf("Reconnecting… (1)", "Reconnecting… (2)", "Reconnecting… (3)"), statuses)
        assertEquals(listOf(1000L, 2000L, 4000L), waits)
    }

    @Test
    fun attemptCounterResetsAfterASuccessfulConnection() = runTest {
        val statuses = mutableListOf<String>()
        var calls = 0
        ReconnectLoop(onStatus = { statuses += it }, pause = { }).run { markConnected ->
            calls++
            when (calls) {
                1, 2 -> SessionEnd.RETRY            // connect failures: 1, 2
                3 -> { markConnected(); SessionEnd.RETRY } // connected then dropped: reset
                4 -> SessionEnd.RETRY
                else -> SessionEnd.GIVE_UP
            }
        }
        assertEquals(
            listOf("Reconnecting… (1)", "Reconnecting… (2)", "Reconnecting… (1)", "Reconnecting… (2)"),
            statuses
        )
    }

    @Test
    fun cancellationStopsTheLoop() = runTest {
        var calls = 0
        val job = launch {
            ReconnectLoop(onStatus = { }).run {
                calls++
                SessionEnd.RETRY
            }
        }
        testScheduler.advanceTimeBy(20_000)
        testScheduler.runCurrent()
        val before = calls
        job.cancel(CancellationException("stop"))
        job.join()
        testScheduler.advanceTimeBy(100_000)
        testScheduler.runCurrent()
        assertEquals(before, calls)
        assertEquals(true, currentTime >= 20_000)
    }
}
