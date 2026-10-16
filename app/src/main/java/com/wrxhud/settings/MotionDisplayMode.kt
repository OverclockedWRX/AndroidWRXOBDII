package com.wrxhud.settings

enum class MotionDisplayMode {
    /** Compact numeric PIT / ROL / YAW / G strip. */
    TEXT,
    /** Three circular gauges, each with a 3D car for pitch, roll, yaw. */
    TRIPLE,
    /** One circular gauge combining pitch + roll + yaw on a single car. */
    COMBINED;

    val label: String
        get() = when (this) {
            TEXT -> "Text"
            TRIPLE -> "Triple rings"
            COMBINED -> "Combined"
        }
}
