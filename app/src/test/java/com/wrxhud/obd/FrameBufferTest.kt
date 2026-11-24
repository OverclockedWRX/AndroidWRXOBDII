package com.wrxhud.obd

import com.wrxhud.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameBufferTest {
    @Test
    fun manyEditsPublishOnce() {
        val out = mutableListOf<VehicleSnapshot>()
        val fb = FrameBuffer { out += it }
        fb.edit { it.copy(rpm = 3000f) }
        fb.edit { it.copy(coolantC = 90f) }
        fb.edit { it.copy(boostPsi = 4f) }
        fb.edit { it.copy(gear = 3) }
        assertTrue(out.isEmpty())
        assertTrue(fb.publish())
        assertEquals(1, out.size)
        assertEquals(3000f, out[0].rpm)
        assertEquals(90f, out[0].coolantC)
        assertEquals(3, out[0].gear)
    }

    @Test
    fun unchangedFrameIsNotRepublished() {
        val out = mutableListOf<VehicleSnapshot>()
        val fb = FrameBuffer { out += it }
        fb.edit { it.copy(rpm = 1000f) }
        assertTrue(fb.publish())
        fb.edit { it.copy(rpm = 1000f) }
        assertFalse(fb.publish())
        assertEquals(1, out.size)
        fb.edit { it.copy(rpm = 1100f) }
        assertTrue(fb.publish())
        assertEquals(2, out.size)
    }

    @Test
    fun editsReadBackWithinCycle() {
        val fb = FrameBuffer { }
        fb.edit { it.copy(rpm = 2500f) }
        assertEquals(2500f, fb.value.rpm)
    }
}
