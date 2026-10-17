package com.wrxhud.sensors

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MotionCalibrationTest {
    @Test
    fun relativeTo_zerosParkedMount() {
        val raw = MotionReading(pitchDeg = 12f, rollDeg = -8f, yawDeg = 40f, gForce = 1f, available = true)
        val calib = MotionMountCalibration(pitchDeg = 12f, rollDeg = -8f, yawDeg = 40f, gForce = 1f)
        val rel = raw.relativeTo(calib, calibrated = true)
        assertThat(rel.pitchDeg!!).isWithin(0.01f).of(0f)
        assertThat(rel.rollDeg!!).isWithin(0.01f).of(0f)
        assertThat(rel.yawDeg!!).isWithin(0.01f).of(0f)
    }

    @Test
    fun relativeTo_reportsDelta() {
        val raw = MotionReading(pitchDeg = 20f, rollDeg = 0f, yawDeg = 10f, available = true)
        val calib = MotionMountCalibration(pitchDeg = 12f, rollDeg = -5f, yawDeg = 0f)
        val rel = raw.relativeTo(calib, calibrated = true)
        assertThat(rel.pitchDeg!!).isWithin(0.01f).of(8f)
        assertThat(rel.rollDeg!!).isWithin(0.01f).of(5f)
        assertThat(rel.yawDeg!!).isWithin(0.01f).of(10f)
    }

    @Test
    fun evaluate_rejectsShaky() {
        val pitches = List(40) { if (it % 2 == 0) 0f else 10f }
        val rolls = List(40) { 0f }
        val yaws = List(40) { 0f }
        val gs = List(40) { 0.02f }
        val stats = computeCalibrationStats(pitches, rolls, yaws, gs)
        val result = evaluateMountCalibration(stats)
        assertThat(result).isInstanceOf(CalibrationResult.Rejected::class.java)
    }

    @Test
    fun evaluate_acceptsStable() {
        val pitches = List(40) { 11.2f + (it % 3) * 0.05f }
        val rolls = List(40) { -4.1f + (it % 2) * 0.04f }
        val yaws = List(40) { 90.0f }
        val gs = List(40) { 0.02f + (it % 2) * 0.01f }
        val stats = computeCalibrationStats(pitches, rolls, yaws, gs)!!
        val result = evaluateMountCalibration(stats)
        assertThat(result).isInstanceOf(CalibrationResult.Ok::class.java)
        val ok = result as CalibrationResult.Ok
        assertThat(ok.calibration.pitchDeg).isWithin(0.5f).of(11.2f)
    }

    @Test
    fun evaluate_rejectsHighDynamicG() {
        val pitches = List(40) { 0f }
        val rolls = List(40) { 0f }
        val yaws = List(40) { 0f }
        val gs = List(40) { 0.4f }
        val result = evaluateMountCalibration(computeCalibrationStats(pitches, rolls, yaws, gs))
        assertThat(result).isInstanceOf(CalibrationResult.Rejected::class.java)
    }

    @Test
    fun wrapSignedDegrees_normalizes() {
        assertThat(wrapSignedDegrees(190f)).isWithin(0.01f).of(-170f)
        assertThat(wrapSignedDegrees(-190f)).isWithin(0.01f).of(170f)
    }
}
