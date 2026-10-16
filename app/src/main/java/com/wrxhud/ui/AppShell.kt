package com.wrxhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wrxhud.hud.HudColors
import com.wrxhud.hud.HudScreen
import com.wrxhud.hud.HudUiState
import com.wrxhud.navigation.NavigationScreen
import com.wrxhud.settings.SettingsScreen
import com.wrxhud.telemetry.AppPage

@Composable
fun AppShell(
    ui: HudUiState,
    onUpdatePreferences: ((com.wrxhud.settings.HudPreferences) -> com.wrxhud.settings.HudPreferences) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf(ui.preferences.defaultPage) }

    LaunchedEffect(ui.preferences.defaultPage) {
        // Apply default only when preferences first load / user changes default in settings
        // while sitting on the other tab we don't force-switch mid-session except at start.
    }

    // Initial selection from preferences once
    LaunchedEffect(ui.preferences.onboardingComplete) {
        if (ui.preferences.onboardingComplete) {
            selectedPage = ui.preferences.defaultPage
        }
    }

    if (showSettings) {
        SettingsScreen(
            preferences = ui.preferences,
            onBack = { showSettings = false },
            onUpdate = onUpdatePreferences
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
    ) {
        TabRow(
            selectedTabIndex = if (selectedPage == AppPage.HUD) 0 else 1,
            containerColor = HudColors.Panel,
            contentColor = HudColors.TextPrimary,
            indicator = { tabPositions ->
                val index = if (selectedPage == AppPage.HUD) 0 else 1
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                    color = HudColors.Accent
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedPage == AppPage.HUD,
                onClick = { selectedPage = AppPage.HUD },
                text = {
                    Text(
                        "HUD",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            )
            Tab(
                selected = selectedPage == AppPage.NAVIGATION,
                onClick = { selectedPage = AppPage.NAVIGATION },
                text = {
                    Text(
                        "NAVIGATION",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            )
        }

        when (selectedPage) {
            AppPage.HUD -> HudScreen(
                snapshot = ui.snapshot,
                preferences = ui.preferences,
                connectionStatus = ui.connectionStatus,
                onOpenSettings = { showSettings = true }
            )
            AppPage.NAVIGATION -> NavigationScreen(
                onOpenSettings = { showSettings = true }
            )
        }
    }
}
