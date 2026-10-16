package com.wrxhud.hud

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wrxhud.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val DISPLAY_MS = 3_000L

/**
 * Flash half-period matching the original Fast & Furious laptop warning strobe
 * (hard on/off, ~5 full cycles per second — ~100 ms visible, 100 ms dark).
 */
private const val FLASH_HALF_PERIOD_MS = 100L

/** Process-lifetime redline hit tracking for the current driving session. */
object DangerToManifoldSession {
    var hitCount: Int = 0
        private set
    private var wasAtRedline: Boolean = false

    /**
     * @return true when this RPM sample should trigger the graphic (2nd+ rising edge).
     */
    fun onRpmSample(enabled: Boolean, rpm: Float?, redlineRpm: Int): Boolean {
        if (!enabled) {
            wasAtRedline = false
            return false
        }
        val atRedline = rpm != null && rpm >= redlineRpm
        var trigger = false
        if (atRedline && !wasAtRedline) {
            hitCount += 1
            trigger = hitCount >= 2
        }
        wasAtRedline = atRedline
        return trigger
    }
}

/**
 * When [enabled], crossing redline a second (or later) time this session shows the
 * Fast & Furious "Danger to Manifold" graphic for 3 seconds, strobing like the film.
 */
@Composable
fun DangerToManifoldHost(
    enabled: Boolean,
    rpm: Float?,
    redlineRpm: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var overlayUntilMs by remember { mutableLongStateOf(0L) }
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, rpm, redlineRpm) {
        if (DangerToManifoldSession.onRpmSample(enabled, rpm, redlineRpm)) {
            overlayUntilMs = System.currentTimeMillis() + DISPLAY_MS
        }
    }

    LaunchedEffect(overlayUntilMs) {
        if (overlayUntilMs <= 0L) return@LaunchedEffect
        showOverlay = true
        val remaining = overlayUntilMs - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        showOverlay = false
    }

    val flashOn by produceState(initialValue = true, showOverlay) {
        if (!showOverlay) {
            value = true
            return@produceState
        }
        while (isActive && showOverlay) {
            value = true
            delay(FLASH_HALF_PERIOD_MS)
            value = false
            delay(FLASH_HALF_PERIOD_MS)
        }
        value = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (enabled && showOverlay) {
            // Full-screen black behind the strobe so "off" is black, not the HUD.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (flashOn) {
                    DangerToManifoldGraphic(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DangerToManifoldGraphic(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.danger_to_manifold),
        contentDescription = "Warning!!! Danger to Manifold",
        contentScale = ContentScale.FillWidth,
        modifier = modifier
    )
}
