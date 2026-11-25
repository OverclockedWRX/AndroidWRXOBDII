package com.wrxhud.settings

/**
 * How much the HUD animates. Unset (null in preferences) follows the system: [REDUCED] when
 * system animations are off, otherwise [FULL].
 */
enum class MotionSetting {
    /** Everything: liquid / helix loops, gear flip, flashing shift lights and alarms. */
    FULL,
    /** No decorative loops, flips or shift strobes; bars still glide and critical tiles flash. */
    REDUCED,
    /** Nothing animates or flashes; values jump straight to the newest sample. */
    OFF;

    val label: String
        get() = when (this) {
            FULL -> "Full"
            REDUCED -> "Reduced"
            OFF -> "Off"
        }

    companion object {
        fun fromStored(raw: String?): MotionSetting? = entries.firstOrNull { it.name == raw }
    }
}
