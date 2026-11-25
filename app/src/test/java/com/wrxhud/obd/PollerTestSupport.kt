package com.wrxhud.obd

import com.wrxhud.settings.PollingMode
import com.wrxhud.vehicle.WrxProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/** Runs a real [ObdPoller] against [link] until the link closes itself. */
fun runPoller(
    link: FakeEcuLink,
    mode: PollingMode,
    assignments: List<PidAssignment> = emptyList(),
    modeProvider: (() -> PollingMode)? = null,
    onPoller: (ObdPoller) -> Unit = {}
): ObdPoller {
    val scope = CoroutineScope(Job())
    val poller = ObdPoller(
        connection = link,
        scope = scope,
        gearConfigProvider = { ObdPoller.GearConfig(WrxProfile.manual6, 1.95) },
        gpsSpeedProvider = { null to false },
        thresholdsProvider = { WrxProfile.AlarmThresholds() },
        assignmentsProvider = { assignments },
        pollingModeProvider = modeProvider ?: { mode },
        // Simulated adapter: every command costs 15 ms of scheduler time.
        clockMs = { link.commands.size * 15L }
    )
    onPoller(poller)
    runBlocking { poller.start().join() }
    return poller
}
