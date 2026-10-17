package com.wrxhud

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wrxhud.hud.AttitudeCalibrationScreen
import com.wrxhud.hud.HudColors
import com.wrxhud.hud.HudThemeController
import com.wrxhud.hud.HudViewModel
import com.wrxhud.onboarding.OnboardingWizard
import com.wrxhud.ui.AppShell

class MainActivity : ComponentActivity() {
    private val viewModel: HudViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        completeOnboardingAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContent {
            val ui by viewModel.uiState.collectAsState()
            val rawMotion by viewModel.rawMotion.collectAsState()

            LaunchedEffect(ui.preferences.onboardingComplete) {
                if (ui.preferences.onboardingComplete) {
                    viewModel.startRuntimeIfNeeded(ui.preferences)
                    viewModel.ensureMotionStarted()
                }
            }

            SideEffect {
                HudThemeController.colorTheme = ui.preferences.colorTheme
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = HudColors.Background
            ) {
                when {
                    !ui.preferences.onboardingComplete -> {
                        OnboardingWizard(
                            preferences = ui.preferences,
                            connectionStatus = ui.connectionStatus,
                            onUpdate = viewModel::updatePreferences,
                            onEnsureObd = viewModel::ensureObdForSetup,
                            onScanPids = { viewModel.scanPids() },
                            onRequestPermissions = { requestNeededPermissions() },
                            onFinished = { }
                        )
                    }
                    !ui.preferences.motionCalibrated -> {
                        LaunchedEffect(Unit) { viewModel.ensureMotionStarted() }
                        AttitudeCalibrationScreen(
                            motion = rawMotion,
                            getLiveMotion = { viewModel.rawMotionNow() },
                            isRecalibration = false,
                            onCalibrated = viewModel::saveMountCalibration
                        )
                    }
                    else -> {
                        AppShell(
                            ui = ui,
                            onUpdatePreferences = viewModel::updatePreferences,
                            onScanPids = { viewModel.scanPids() },
                            onReadDtcs = { viewModel.readDtcs() },
                            onClearDtcs = { viewModel.clearDtcs() },
                            connectionStatus = ui.connectionStatus,
                            onRecalibrateMotion = viewModel::clearMountCalibration,
                            onToggleLogging = viewModel::toggleDataLog
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestNeededPermissions() {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            completeOnboardingAndStart()
        }
    }

    private fun completeOnboardingAndStart() {
        viewModel.updatePreferences { it.copy(onboardingComplete = true) }
        val prefs = viewModel.uiState.value.preferences.copy(onboardingComplete = true)
        viewModel.startRuntimeIfNeeded(prefs)
        viewModel.ensureMotionStarted()
    }
}
