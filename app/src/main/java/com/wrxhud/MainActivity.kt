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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import com.wrxhud.ui.HighRefreshRate

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
        keepScreenOn()
        preferHighRefreshRate()
        hideSystemUi()

        setContent {
            // Root observes only two booleans and the theme key; everything else is
            // collected by the screen that draws it.
            val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
            val motionCalibrated by viewModel.motionCalibrated.collectAsStateWithLifecycle()
            val colorTheme by remember { viewModel.preferences.map { it.colorTheme }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(HudThemeController.colorTheme)

            LaunchedEffect(onboardingComplete) {
                if (onboardingComplete) {
                    viewModel.startRuntimeIfNeeded(viewModel.preferences.value)
                    viewModel.ensureMotionStarted()
                }
            }

            SideEffect {
                HudThemeController.colorTheme = colorTheme
            }

            val view = LocalView.current
            DisposableEffect(Unit) {
                view.keepScreenOn = true
                HighRefreshRate.requestOnView(view)
                onDispose { view.keepScreenOn = false }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = HudColors.Background
            ) {
                when {
                    !onboardingComplete -> {
                        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
                        val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
                        OnboardingWizard(
                            preferences = preferences,
                            connectionStatus = connectionStatus,
                            onUpdate = viewModel::updatePreferences,
                            onEnsureObd = viewModel::ensureObdForSetup,
                            onScanPids = { viewModel.scanPids() },
                            onRequestPermissions = { requestNeededPermissions() },
                            onFinished = { }
                        )
                    }
                    !motionCalibrated -> {
                        LaunchedEffect(Unit) { viewModel.ensureMotionStarted() }
                        // rawMotion (~60 Hz) is collected only on this screen.
                        val rawMotion by viewModel.rawMotion.collectAsStateWithLifecycle()
                        AttitudeCalibrationScreen(
                            motion = rawMotion,
                            getLiveMotion = { viewModel.rawMotionNow() },
                            isRecalibration = false,
                            onCalibrated = viewModel::saveMountCalibration
                        )
                    }
                    else -> {
                        AppShell(
                            viewModel = viewModel,
                            onRecalibrateMotion = viewModel::clearMountCalibration
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        keepScreenOn()
        preferHighRefreshRate()
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            preferHighRefreshRate()
            hideSystemUi()
        }
    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun preferHighRefreshRate() {
        HighRefreshRate.apply(this)
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
        val prefs = viewModel.preferences.value.copy(onboardingComplete = true)
        viewModel.startRuntimeIfNeeded(prefs)
        viewModel.ensureMotionStarted()
    }
}
