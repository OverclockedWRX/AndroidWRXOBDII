package com.wrxhud.obd

import com.wrxhud.vehicle.VehicleSnapshot

/**
 * Single-writer working frame for the poller. Edits during a cycle only touch this
 * buffer; [publish] hands the finished frame to [sink] once, and only if it changed.
 */
class FrameBuffer(
    initial: VehicleSnapshot = VehicleSnapshot(),
    private val sink: (VehicleSnapshot) -> Unit
) {
    var value: VehicleSnapshot = initial
        private set
    private var version = 0L
    private var publishedVersion = -1L

    fun edit(transform: (VehicleSnapshot) -> VehicleSnapshot) {
        val next = transform(value)
        if (next != value) {
            value = next
            version++
        }
    }

    /** @return true when a frame was emitted. */
    fun publish(): Boolean {
        if (version == publishedVersion) return false
        publishedVersion = version
        sink(value)
        return true
    }
}
