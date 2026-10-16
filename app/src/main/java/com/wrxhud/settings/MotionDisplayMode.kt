package com.wrxhud.settings

enum class MotionDisplayMode {
    /** Compact numeric PIT / ROL / G strip. */
    TEXT,
    /** Dual circular gauges — pitch (side) + roll (rear) WRX rings. */
    RINGS,
    /** One circular gauge combining pitch + roll on a single car. */
    COMBINED;

    val label: String
        get() = when (this) {
            TEXT -> "Text"
            RINGS -> "Pitch / roll rings"
            COMBINED -> "Combined"
        }

    companion object {
        fun fromStored(raw: String?): MotionDisplayMode? {
            if (raw == null) return null
            // Migrate pre-0.9.1 "TRIPLE" (included yaw) → dual rings.
            if (raw == "TRIPLE") return RINGS
            return runCatching { valueOf(raw) }.getOrNull()
        }
    }
}
