package com.wrxhud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wrxhud.hud.HudScreen
import com.wrxhud.hud.HudUiState
import com.wrxhud.obd.PidScanRow
import com.wrxhud.settings.SettingsScreen

@Composable
fun AppShell(
    ui: HudUiState,
    onUpdatePreferences: ((com.wrxhud.settings.HudPreferences) -> com.wrxhud.settings.HudPreferences) -> Unit,
    onScanPids: suspend () -> List<PidScanRow> = { emptyList() },
    connectionStatus: String = ui.connectionStatus,
    onRecalibrateMotion: () -> Unit = {},
    onToggleLogging: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            preferences = ui.preferences,
            onBack = { showSettings = false },
            onUpdate = onUpdatePreferences,
            onScanPids = onScanPids,
            connectionStatus = connectionStatus,
            onRecalibrateMotion = {
                showSettings = false
                onRecalibrateMotion()
            }
        )
        return
    }

    HudScreen(
        snapshot = ui.snapshot,
        preferences = ui.preferences,
        connectionStatus = ui.connectionStatus,
        motion = ui.motion,
        speedLimit = ui.speedLimit,
        logging = ui.logging,
        logFileName = ui.logFileName,
        onToggleLogging = onToggleLogging,
        onOpenSettings = { showSettings = true }
    )
}
