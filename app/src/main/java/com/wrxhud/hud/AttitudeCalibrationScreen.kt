package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.sensors.CalibrationResult
import com.wrxhud.sensors.MotionReading
import com.wrxhud.sensors.computeCalibrationStats
import com.wrxhud.sensors.evaluateMountCalibration
import com.wrxhud.sensors.formatGForce
import com.wrxhud.sensors.formatSignedDegrees
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SAMPLE_MS = 2800L
private const val SAMPLE_INTERVAL_MS = 40L

/**
 * Required after mounting the phone in the car: captures a parked accelerometer /
 * rotation baseline so pitch / roll / yaw read zero at rest.
 */
@Composable
fun AttitudeCalibrationScreen(
    motion: MotionReading,
    getLiveMotion: () -> MotionReading = { motion },
    isRecalibration: Boolean,
    onCalibrated: (com.wrxhud.sensors.MotionMountCalibration) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val pitches = mutableListOf<Float>()
        val rolls = mutableListOf<Float>()
        val yaws = mutableListOf<Float>()
        val gs = mutableListOf<Float>()
        error = null
        status = "Sampling mount attitude…"
        progress = 0f
        val start = System.currentTimeMillis()
        while (isActive && System.currentTimeMillis() - start < SAMPLE_MS) {
            val sample = getLiveMotion()
            val p = sample.pitchDeg
            val r = sample.rollDeg
            val y = sample.yawDeg
            if (p != null && r != null && y != null) {
                pitches += p
                rolls += r
                yaws += y
            }
            sample.gForce?.let { gs += it }
            progress = ((System.currentTimeMillis() - start).toFloat() / SAMPLE_MS).coerceIn(0f, 1f)
            delay(SAMPLE_INTERVAL_MS)
        }
        val stats = computeCalibrationStats(pitches, rolls, yaws, gs)
        when (val result = evaluateMountCalibration(stats)) {
            is CalibrationResult.Ok -> {
                status = "Calibrated — pitch/roll zeroed to this mount."
                running = false
                progress = 0f
                onCalibrated(result.calibration)
            }
            is CalibrationResult.Rejected -> {
                error = result.detail
                status = null
                running = false
                progress = 0f
            }
        }
    }

    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isRecalibration) "Recalibrate attitude" else "Mount calibration",
            color = HudColors.Accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Pitch and roll are measured relative to how the phone sits in the car. " +
                "After mounting, park on level ground and calibrate once so the HUD shows true attitude.",
            color = HudColors.TextPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(HudColors.Panel, shape)
                .border(1.dp, HudColors.Border, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Before you start",
                color = HudColors.Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Checklist("Phone locked in its mount (landscape)")
            Checklist("Car parked on level ground, not moving")
            Checklist("Don’t touch the phone during the ~3s sample")
            Checklist("Engine idle is OK — avoid hard idle shake if possible")
            Checklist("After remounting or rotating landscape, recalibrate")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(HudColors.Panel, shape)
                .border(1.dp, HudColors.Border, shape)
                .padding(14.dp)
        ) {
            Text(
                "Live sensors (raw)",
                color = HudColors.TextDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            if (!motion.available) {
                Text(
                    "No accelerometer / rotation sensor detected on this device.",
                    color = HudColors.Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LiveCell("PIT", formatSignedDegrees(motion.pitchDeg))
                    LiveCell("ROL", formatSignedDegrees(motion.rollDeg))
                    LiveCell("G", formatGForce(motion.gForce))
                }
            }
        }

        if (running) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = HudColors.Accent,
                trackColor = HudColors.Border
            )
        }

        status?.let {
            Text(it, color = HudColors.Green, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        error?.let {
            Text(it, color = HudColors.Red, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (!running && motion.available) running = true },
                enabled = !running && motion.available,
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (running) "Hold still…" else "Start mount test",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            if (onCancel != null) {
                Button(
                    onClick = onCancel,
                    enabled = !running,
                    colors = ButtonDefaults.buttonColors(containerColor = HudColors.Panel),
                    modifier = Modifier.weight(0.55f)
                ) {
                    Text("Cancel", fontFamily = FontFamily.Monospace, color = HudColors.TextPrimary)
                }
            }
        }

        if (!isRecalibration) {
            Text(
                "This step is required once per mount. You can recalibrate anytime in Settings.",
                color = HudColors.TextDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun Checklist(text: String) {
    Text(
        text = "• $text",
        color = HudColors.TextPrimary,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun LiveCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = HudColors.TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(
            value,
            color = HudColors.TextPrimary,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
