package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.sensors.MotionReading
import com.wrxhud.sensors.formatGForce
import com.wrxhud.sensors.formatSignedDegrees

/**
 * Compact pitch / roll / yaw / g-force strip — sits under shift lights, right of RPM.
 */
@Composable
fun MotionTelemetryStrip(
    motion: MotionReading,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MotionCell("PIT", formatSignedDegrees(motion.pitchDeg))
        MotionCell("ROL", formatSignedDegrees(motion.rollDeg))
        MotionCell("YAW", formatSignedDegrees(motion.yawDeg))
        MotionCell("G", formatGForce(motion.gForce))
    }
}

@Composable
private fun MotionCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = HudColors.TextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = HudColors.TextPrimary,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
