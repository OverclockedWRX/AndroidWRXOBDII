package com.wrxhud.hud

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared sizing for the tach side cluster: SPD / LIM stack, G-Force, and GEAR
 * all share the same total column height (no captions).
 */
internal object TachSideClusterDims {
    val width = 110.dp
    /** Full column height — SPD+LIM stack, G-Force, and GEAR all match this. */
    val columnHeight = 186.dp
    val stackGap = 4.dp
    val gearSp = 92.sp

    fun stackedFaceHeight(count: Int): Dp {
        if (count <= 1) return columnHeight
        val gaps = stackGap * (count - 1)
        return (columnHeight - gaps) / count
    }
}
