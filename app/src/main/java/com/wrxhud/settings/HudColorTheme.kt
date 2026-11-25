package com.wrxhud.settings

/**
 * Global HUD color palette. Independent of tach/shift visual style.
 */
enum class HudColorTheme {
    DARK,
    LIGHT,
    CONTRAST,
    OLED,
    CLAUDE,
    HANDHELD,
    NIGHT,
    /** Phosphor green + amber LCD / VFD look. */
    RETRO_LCD;

    val label: String
        get() = when (this) {
            DARK -> "Dark / Modern"
            LIGHT -> "Light"
            CONTRAST -> "High contrast"
            OLED -> "OLED Black"
            CLAUDE -> "Claude"
            HANDHELD -> "Handheld"
            NIGHT -> "Night"
            RETRO_LCD -> "Retro LCD"
        }

    companion object {
        /** Reads a stored key, including the pre-0.37 names (CLASSIC, EINK, PINKY). */
        fun fromStored(raw: String?): HudColorTheme = when (raw) {
            "EINK" -> LIGHT
            // CLASSIC, PINKY (also the old pinky_mode flag alone), unknown and unset.
            else -> entries.firstOrNull { it.name == raw } ?: DARK
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
