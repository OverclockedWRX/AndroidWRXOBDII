package com.wrxhud.hud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.wrxhud.settings.HudColorTheme

/**
 * Active HUD palette. Reading any [HudColors] property during composition
 * subscribes to [colorTheme] so the UI recomposes when the theme flips.
 */
object HudThemeController {
    var colorTheme by mutableStateOf(HudColorTheme.CLASSIC)

    /** @deprecated Prefer [colorTheme]; kept for call sites during migration. */
    var pinkyMode: Boolean
        get() = colorTheme == HudColorTheme.PINKY
        set(value) {
            colorTheme = if (value) HudColorTheme.PINKY else HudColorTheme.CLASSIC
        }
}

private data class HudColorScheme(
    val background: Color,
    val panel: Color,
    val border: Color,
    val textPrimary: Color,
    val textDim: Color,
    val accent: Color,
    val boostBlue: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val tachTrack: Color,
    val tachFill: Color,
    val lampOff: Color
)

private val ClassicScheme = HudColorScheme(
    background = Color(0xFF050505),
    panel = Color(0xFF121212),
    border = Color(0xFF2A2A2A),
    textPrimary = Color(0xFFF5F5F5),
    textDim = Color(0xFF8A8A8A),
    accent = Color(0xFFE10600),
    boostBlue = Color(0xFF3B82F6),
    green = Color(0xFF22C55E),
    amber = Color(0xFFF59E0B),
    red = Color(0xFFEF4444),
    tachTrack = Color(0xFF1F1F1F),
    tachFill = Color(0xFFE8E8E8),
    lampOff = Color(0xFF2A2A2A)
)

private val PinkyScheme = HudColorScheme(
    background = Color(0xFF14080F),
    panel = Color(0xFF261018),
    border = Color(0xFF6B3A52),
    textPrimary = Color(0xFFFFF0F6),
    textDim = Color(0xFFD4A0B5),
    accent = Color(0xFFFF4FA3),
    boostBlue = Color(0xFFF472B6),
    green = Color(0xFFF9A8D4),
    amber = Color(0xFFFB7185),
    red = Color(0xFFE11D8A),
    tachTrack = Color(0xFF2A1520),
    tachFill = Color(0xFFFFE4F0),
    lampOff = Color(0xFF3D2433)
)

/** Deeper blacks, muted chromas — easier on night eyes. */
private val NightScheme = HudColorScheme(
    background = Color(0xFF000000),
    panel = Color(0xFF0A0A0A),
    border = Color(0xFF1A1A1A),
    textPrimary = Color(0xFFC8C8C8),
    textDim = Color(0xFF5A5A5A),
    accent = Color(0xFFB91C1C),
    boostBlue = Color(0xFF1D4ED8),
    green = Color(0xFF15803D),
    amber = Color(0xFFB45309),
    red = Color(0xFF991B1B),
    tachTrack = Color(0xFF101010),
    tachFill = Color(0xFF9CA3AF),
    lampOff = Color(0xFF181818)
)

/**
 * High-contrast daylight “e-ink” look — cream paper + black ink.
 * Refreshes at the same rate as other themes (no slow e-ink simulation).
 */
private val EinkScheme = HudColorScheme(
    background = Color(0xFFF2EFE6),
    panel = Color(0xFFE8E4D9),
    border = Color(0xFF2A2A2A),
    textPrimary = Color(0xFF111111),
    textDim = Color(0xFF4A4A4A),
    accent = Color(0xFF111111),
    boostBlue = Color(0xFF1A1A1A),
    green = Color(0xFF333333),
    amber = Color(0xFF222222),
    red = Color(0xFF000000),
    tachTrack = Color(0xFFD4D0C4),
    tachFill = Color(0xFF111111),
    lampOff = Color(0xFFC8C4B8)
)

object HudColors {
    private val scheme: HudColorScheme
        get() = when (HudThemeController.colorTheme) {
            HudColorTheme.CLASSIC -> ClassicScheme
            HudColorTheme.PINKY -> PinkyScheme
            HudColorTheme.NIGHT -> NightScheme
            HudColorTheme.EINK -> EinkScheme
        }

    val Background: Color get() = scheme.background
    val Panel: Color get() = scheme.panel
    val Border: Color get() = scheme.border
    val TextPrimary: Color get() = scheme.textPrimary
    val TextDim: Color get() = scheme.textDim
    val Accent: Color get() = scheme.accent
    val BoostBlue: Color get() = scheme.boostBlue
    val Green: Color get() = scheme.green
    val Amber: Color get() = scheme.amber
    val Red: Color get() = scheme.red
    val TachTrack: Color get() = scheme.tachTrack
    val TachFill: Color get() = scheme.tachFill
    val LampOff: Color get() = scheme.lampOff
}
