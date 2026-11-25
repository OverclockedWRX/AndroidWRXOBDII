package com.wrxhud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wrxhud.hud.HudScreen
import com.wrxhud.hud.HudViewModel
import com.wrxhud.settings.SettingsScreen

/**
 * Hosts HUD and Settings. Each branch collects only the flows it draws; the shell itself
 * observes nothing but which page is open.
 */
@Composable
fun AppShell(
    viewModel: HudViewModel,
    onRecalibrateMotion: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val vehicleInfo by viewModel.vehicleInfo.collectAsStateWithLifecycle()
        val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
        SettingsScreen(
            preferences = preferences,
            vehicleInfo = vehicleInfo,
            onBack = { showSettings = false },
            onUpdate = viewModel::updatePreferences,
            onScanPids = { viewModel.scanPids() },
            onReadDtcs = { viewModel.readDtcs() },
            onClearDtcs = { viewModel.clearDtcs() },
            onRefreshVehicleInfo = { viewModel.refreshVehicleInfo() },
            connectionStatus = connectionStatus,
            onRecalibrateMotion = {
                showSettings = false
                onRecalibrateMotion()
            }
        )
        return
    }

    HudRoute(viewModel = viewModel, onOpenSettings = { showSettings = true })
}

@Composable
private fun HudRoute(viewModel: HudViewModel, onOpenSettings: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val speedLimit by viewModel.speedLimit.collectAsStateWithLifecycle()
    val logging by viewModel.logging.collectAsStateWithLifecycle()
    val logFileName by viewModel.logFileName.collectAsStateWithLifecycle()
    // Motion is handed down as a state holder, never read here: only the gauges that
    // draw it observe the ~60 Hz stream.
    val motionState = viewModel.motion.collectAsStateWithLifecycle()
    val motionProvider = remember(motionState) { { motionState.value } }

    HudScreen(
        snapshot = snapshot,
        preferences = preferences,
        connectionStatus = connectionStatus,
        motion = motionProvider,
        speedLimit = speedLimit,
        logging = logging,
        logFileName = logFileName,
        onToggleLogging = viewModel::toggleDataLog,
        onOpenSettings = onOpenSettings
    )
}
