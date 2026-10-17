package com.wrxhud.settings

/**
 * Global HUD color palette. Independent of tach/shift visual style.
 */
enum class HudColorTheme {
    CLASSIC,
    PINKY,
    NIGHT,
    EINK,
    /** Phosphor green + amber LCD / VFD look. */
    RETRO_LCD;

    val label: String
        get() = when (this) {
            CLASSIC -> "Classic"
            PINKY -> "Pinky"
            NIGHT -> "Night"
            EINK -> "E-ink day"
            RETRO_LCD -> "Retro LCD"
        }

    companion object {
        fun fromStored(raw: String?, pinkyFallback: Boolean): HudColorTheme {
            if (raw != null) {
                runCatching { valueOf(raw) }.getOrNull()?.let { return it }
            }
            return if (pinkyFallback) PINKY else CLASSIC
        }
    }
}

/**
 * How the RPM bar (and shift lights) are drawn.
 */
enum class TachVisualStyle {
    /** Enhanced extruded 3D bar / lamps. */
    BAR_3D,
    /** Liquid-water fill, same zone colors. */
    LIQUID,
    /** Double helix: RPM strand + boost strand across the tach. */
    HELIX,
    /** R8-style segmented race bar with green/amber/red shift zones. */
    RACE_SEG;

    val label: String
        get() = when (this) {
            BAR_3D -> "3D bar"
            LIQUID -> "Liquid"
            HELIX -> "RPM+Boost helix"
            RACE_SEG -> "Race segments"
        }

    companion object {
        fun fromStored(raw: String?, rpm3dFallback: Boolean): TachVisualStyle {
            if (raw != null) {
                runCatching { valueOf(raw) }.getOrNull()?.let { return it }
            }
            return if (rpm3dFallback) BAR_3D else BAR_3D
        }
    }
}
