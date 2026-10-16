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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wrxhud.hud.HudColors
import com.wrxhud.hud.HudViewModel
import com.wrxhud.onboarding.OnboardingWizard
import com.wrxhud.ui.AppShell

class MainActivity : ComponentActivity() {
    private val viewModel: HudViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After grants, start runtime using current preferences.
        val prefs = viewModel.uiState.value.preferences
        viewModel.startRuntimeIfNeeded(prefs.copy(onboardingComplete = true))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContent {
            val ui by viewModel.uiState.collectAsState()

            LaunchedEffect(ui.preferences.onboardingComplete) {
                if (ui.preferences.onboardingComplete) {
                    viewModel.startRuntimeIfNeeded(ui.preferences)
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = HudColors.Background
            ) {
                if (!ui.preferences.onboardingComplete) {
                    OnboardingWizard(
                        preferences = ui.preferences,
                        onUpdate = viewModel::updatePreferences,
                        onRequestPermissions = { requestNeededPermissions() },
                        onFinished = {
                            // Runtime starts via LaunchedEffect when onboardingComplete flips,
                            // and again after the permission dialog returns.
                        }
                    )
                } else {
                    AppShell(
                        ui = ui,
                        onUpdatePreferences = viewModel::updatePreferences
                    )
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
            viewModel.startRuntimeIfNeeded(
                viewModel.uiState.value.preferences.copy(onboardingComplete = true)
            )
        }
    }
}
