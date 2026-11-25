package com.wrxhud.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.wrxhud.settings.HudColorTheme
import com.wrxhud.settings.MotionSetting

internal data class HudColorScheme(
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
    val lampOff: Color,
    /** Pitch/roll/G ring — upper arc. */
    val attitudeRingUpper: Color,
    /** Pitch/roll/G ring — lower arc. */
    val attitudeRingLower: Color,
    /** Car silhouette / G blob / numeral glow. */
    val attitudeGlow: Color,
    /** Horizon / crosshair lines inside attitude gauges. */
    val attitudeHorizon: Color,
    /** Inner dial face behind the car / G plot. */
    val attitudeFace: Color,
    /** Glass, wheels, G-tip center (contrasts with glow). */
    val attitudeInk: Color
)

/** Dark / Modern (default). */
private val DarkScheme = HudColorScheme(
    background = Color(0xFF0A0F1E),
    panel = Color(0xFF141928),
    border = Color(0xFF242C40),
    textPrimary = Color(0xFFE6EAF3),
    textDim = Color(0xFF94A0BB),
    accent = Color(0xFF38BDF8),
    boostBlue = Color(0xFF3B82F6),
    green = Color(0xFF4ADE80),
    amber = Color(0xFFFBBF24),
    red = Color(0xFFF43F5E),
    tachTrack = Color(0xFF1A2136),
    tachFill = Color(0xFFE6EAF3),
    lampOff = Color(0xFF222A3F),
    attitudeRingUpper = Color(0xFF2DD4BF),
    attitudeRingLower = Color(0xFF3B82F6),
    attitudeGlow = Color(0xFF38BDF8),
    attitudeHorizon = Color(0xFF94A0BB),
    attitudeFace = Color(0xFF060A15),
    attitudeInk = Color(0xFF0A0F1E)
)

/** Daylight. */
private val LightScheme = HudColorScheme(
    background = Color(0xFFEEF2F9),
    panel = Color(0xFFE3E7EF),
    border = Color(0xFFC3CAD6),
    textPrimary = Color(0xFF0F172A),
    textDim = Color(0xFF475569),
    accent = Color(0xFF0284C7),
    boostBlue = Color(0xFF0284C7),
    green = Color(0xFF15803D),
    amber = Color(0xFFB45309),
    red = Color(0xFFDC2626),
    tachTrack = Color(0xFFD3DAE7),
    tachFill = Color(0xFF0F172A),
    lampOff = Color(0xFFC9D1DF),
    attitudeRingUpper = Color(0xFF0D9488),
    attitudeRingLower = Color(0xFF0284C7),
    attitudeGlow = Color(0xFF0284C7),
    attitudeHorizon = Color(0xFF475569),
    attitudeFace = Color(0xFFF8FAFD),
    attitudeInk = Color(0xFFDCE3EE)
)

/** High contrast: black, white and yellow. */
private val ContrastScheme = HudColorScheme(
    background = Color(0xFF000000),
    panel = Color(0xFF0D0D0D),
    border = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textDim = Color(0xFFF0F0F0),
    accent = Color(0xFFFFD400),
    boostBlue = Color(0xFFFFD400),
    green = Color(0xFF00FF66),
    amber = Color(0xFFFFD400),
    red = Color(0xFFFF3030),
    tachTrack = Color(0xFF262626),
    tachFill = Color(0xFFFFFFFF),
    lampOff = Color(0xFF333333),
    attitudeRingUpper = Color(0xFFFFD400),
    attitudeRingLower = Color(0xFFFFD400),
    attitudeGlow = Color(0xFFFFD400),
    attitudeHorizon = Color(0xFFF0F0F0),
    attitudeFace = Color(0xFF000000),
    attitudeInk = Color(0xFF000000)
)

/** Pure black. */
private val OledScheme = HudColorScheme(
    background = Color(0xFF000000),
    panel = Color(0xFF0A0A0A),
    border = Color(0xFF242424),
    textPrimary = Color(0xFFE8E8E8),
    textDim = Color(0xFF8A8A8A),
    accent = Color(0xFF38BDF8),
    boostBlue = Color(0xFF3B82F6),
    green = Color(0xFF4ADE80),
    amber = Color(0xFFFBBF24),
    red = Color(0xFFF43F5E),
    tachTrack = Color(0xFF141414),
    tachFill = Color(0xFFE8E8E8),
    lampOff = Color(0xFF1C1C1C),
    attitudeRingUpper = Color(0xFF2DD4BF),
    attitudeRingLower = Color(0xFF3B82F6),
    attitudeGlow = Color(0xFF38BDF8),
    attitudeHorizon = Color(0xFF8A8A8A),
    attitudeFace = Color(0xFF000000),
    attitudeInk = Color(0xFF0A0A0A)
)

/** Navy and coral. */
private val ClaudeScheme = HudColorScheme(
    background = Color(0xFF0E141B),
    panel = Color(0xFF17202B),
    border = Color(0xFF27323F),
    textPrimary = Color(0xFFECEEF0),
    textDim = Color(0xFF8B98A5),
    accent = Color(0xFFD97757),
    boostBlue = Color(0xFFCF6A47),
    green = Color(0xFF59BF40),
    amber = Color(0xFFE5C23A),
    red = Color(0xFFE14B4B),
    tachTrack = Color(0xFF1F2A37),
    tachFill = Color(0xFFECEEF0),
    lampOff = Color(0xFF27323F),
    attitudeRingUpper = Color(0xFFE9A58C),
    attitudeRingLower = Color(0xFFCF6A47),
    attitudeGlow = Color(0xFFD97757),
    attitudeHorizon = Color(0xFF8B98A5),
    attitudeFace = Color(0xFF0A0F15),
    attitudeInk = Color(0xFF0E141B)
)

/** Handheld blue. */
private val HandheldScheme = HudColorScheme(
    background = Color(0xFF0E141B),
    panel = Color(0xFF1F2630),
    border = Color(0xFF2A313B),
    textPrimary = Color(0xFFDCDEDF),
    textDim = Color(0xFF8B929A),
    accent = Color(0xFF1A9FFF),
    boostBlue = Color(0xFF1A9FFF),
    green = Color(0xFF59BF40),
    amber = Color(0xFFE5C23A),
    red = Color(0xFFE14B4B),
    tachTrack = Color(0xFF262E3A),
    tachFill = Color(0xFFDCDEDF),
    lampOff = Color(0xFF2A313B),
    attitudeRingUpper = Color(0xFF66C0F4),
    attitudeRingLower = Color(0xFF1A9FFF),
    attitudeGlow = Color(0xFF1A9FFF),
    attitudeHorizon = Color(0xFF8B929A),
    attitudeFace = Color(0xFF0A0F15),
    attitudeInk = Color(0xFF0E141B)
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
    lampOff = Color(0xFF181818),
    attitudeRingUpper = Color(0xFF3F6212),
    attitudeRingLower = Color(0xFF1E3A5F),
    attitudeGlow = Color(0xFFA3A3A3),
    attitudeHorizon = Color(0xFF525252),
    attitudeFace = Color(0xFF050505),
    attitudeInk = Color(0xFF0A0A0A)
)

/** Classic green/amber phosphor LCD (VFD-adjacent) cockpit. */
private val RetroLcdScheme = HudColorScheme(
    background = Color(0xFF061008),
    panel = Color(0xFF0C1A10),
    border = Color(0xFF1A3A22),
    textPrimary = Color(0xFF39FF14),
    textDim = Color(0xFF2A8A40),
    accent = Color(0xFFFFB000),
    boostBlue = Color(0xFF33D17A),
    green = Color(0xFF39FF14),
    amber = Color(0xFFFFB000),
    red = Color(0xFFFF6A00),
    tachTrack = Color(0xFF0A160C),
    tachFill = Color(0xFF5CFF3A),
    lampOff = Color(0xFF14301A),
    attitudeRingUpper = Color(0xFF39FF14),
    attitudeRingLower = Color(0xFFFFB000),
    attitudeGlow = Color(0xFF7CFF55),
    attitudeHorizon = Color(0xFF2A8A40),
    attitudeFace = Color(0xFF040A06),
    attitudeInk = Color(0xFF0C1A10)
)

/** Theme-aware colors for pitch / roll / G-force inclinometer rings. */
data class AttitudePalette(
    val face: Color,
    val ringUpper: Color,
    val ringLower: Color,
    val glow: Color,
    val horizon: Color,
    val ink: Color
)

internal fun hudColorScheme(theme: HudColorTheme): HudColorScheme = when (theme) {
    HudColorTheme.DARK -> DarkScheme
    HudColorTheme.LIGHT -> LightScheme
    HudColorTheme.CONTRAST -> ContrastScheme
    HudColorTheme.OLED -> OledScheme
    HudColorTheme.CLAUDE -> ClaudeScheme
    HudColorTheme.HANDHELD -> HandheldScheme
    HudColorTheme.NIGHT -> NightScheme
    HudColorTheme.RETRO_LCD -> RetroLcdScheme
}

internal val LocalHudColors = staticCompositionLocalOf { hudColorScheme(HudColorTheme.DARK) }

/** Provides the palette and motion preference to everything below it. */
@Composable
fun HudTheme(theme: HudColorTheme, motion: MotionSetting? = null, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHudColors provides hudColorScheme(theme),
        LocalHudMotion provides motion,
        content = content
    )
}

/** The palette of the nearest [HudTheme]. */
object HudColors {
    private val scheme: HudColorScheme
        @Composable @ReadOnlyComposable get() = LocalHudColors.current

    val Background: Color
        @Composable @ReadOnlyComposable get() = scheme.background
    val Panel: Color
        @Composable @ReadOnlyComposable get() = scheme.panel
    val Border: Color
        @Composable @ReadOnlyComposable get() = scheme.border
    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = scheme.textPrimary
    val TextDim: Color
        @Composable @ReadOnlyComposable get() = scheme.textDim
    val Accent: Color
        @Composable @ReadOnlyComposable get() = scheme.accent
    val BoostBlue: Color
        @Composable @ReadOnlyComposable get() = scheme.boostBlue
    val Green: Color
        @Composable @ReadOnlyComposable get() = scheme.green
    val Amber: Color
        @Composable @ReadOnlyComposable get() = scheme.amber
    val Red: Color
        @Composable @ReadOnlyComposable get() = scheme.red
    val TachTrack: Color
        @Composable @ReadOnlyComposable get() = scheme.tachTrack
    val TachFill: Color
        @Composable @ReadOnlyComposable get() = scheme.tachFill
    val LampOff: Color
        @Composable @ReadOnlyComposable get() = scheme.lampOff

    val Attitude: AttitudePalette
        @Composable @ReadOnlyComposable get() = AttitudePalette(
            face = scheme.attitudeFace,
            ringUpper = scheme.attitudeRingUpper,
            ringLower = scheme.attitudeRingLower,
            glow = scheme.attitudeGlow,
            horizon = scheme.attitudeHorizon,
            ink = scheme.attitudeInk
        )
}
