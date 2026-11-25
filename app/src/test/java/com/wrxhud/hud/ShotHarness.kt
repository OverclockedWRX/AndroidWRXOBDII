package com.wrxhud.hud

import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.robolectric.Shadows.shadowOf
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import com.wrxhud.settings.HudColorTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * JVM screenshot safety net (Robolectric native graphics + Compose test).
 *
 * `-PwrxShots=record` writes goldens to src/test/screenshots; default `verify` compares
 * pixel by pixel and fails above [TOLERANCE] (max per-channel difference, 0..255).
 * Every comparison is appended to build/screenshot-report.txt.
 */
object ShotHarness {
    val mode: String = (System.getProperty("wrx.shots") ?: "verify")
    private val dir = File((System.getProperty("wrx.shots.dir") ?: "src/test/screenshots"))
    private val report = File((System.getProperty("wrx.shots.report") ?: "build/screenshot-report.txt"))

    /** Max allowed per-channel difference; 1 = anti-aliasing rounding only. */
    val TOLERANCE: Int = (System.getProperty("wrx.shots.tol") ?: "1").toInt()

    @Synchronized
    private fun log(line: String) {
        report.parentFile?.mkdirs()
        report.appendText(line + "\n")
    }

    /** Returns null when OK, otherwise a failure message. */
    fun check(name: String, image: ImageBitmap): String? {
        val w = image.width
        val h = image.height
        val px = IntArray(w * h)
        image.readPixels(px)
        val file = File(dir, "$name.png")
        if (mode == "record") {
            dir.mkdirs()
            val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            bi.setRGB(0, 0, w, h, px, 0, w)
            ImageIO.write(bi, "png", file)
            log("RECORD $name ${w}x$h")
            return null
        }
        if (!file.exists()) {
            log("MISSING $name")
            return "missing golden $name"
        }
        val g = ImageIO.read(file)
        if (g.width != w || g.height != h) {
            log("SIZE $name golden=${g.width}x${g.height} actual=${w}x$h")
            return "size mismatch $name"
        }
        val gp = IntArray(w * h)
        g.getRGB(0, 0, w, h, gp, 0, w)
        var maxDiff = 0
        var differing = 0
        for (i in px.indices) {
            val a = px[i]
            val b = gp[i]
            if (a == b) continue
            var d = 0
            for (shift in intArrayOf(0, 8, 16, 24)) {
                d = maxOf(d, abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)))
            }
            if (d > 0) {
                differing++
                if (d > maxDiff) maxDiff = d
            }
        }
        log("VERIFY $name maxDiff=$maxDiff differingPixels=$differing/${w * h}")
        if (maxDiff > TOLERANCE) {
            // Keep the failing render next to the report for inspection.
            val out = File(report.parentFile, "screenshot-actual").apply { mkdirs() }
            val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            bi.setRGB(0, 0, w, h, px, 0, w)
            ImageIO.write(bi, "png", File(out, "$name.png"))
        }
        return if (maxDiff > TOLERANCE) "$name differs: maxDiff=$maxDiff pixels=$differing" else null
    }

    /**
     * Draws the scene node straight from the activity's decor view (software canvas, native
     * graphics). ComposeTestRule.captureToImage() waits for real frame callbacks, which never
     * come while the main clock is paused for deterministic animation time.
     */
    private fun capture(rule: ComposeContentTestRule): ImageBitmap {
        shadowOf(Looper.getMainLooper()).idle()
        val bounds = rule.onNodeWithTag("shot").fetchSemanticsNode().boundsInWindow
        val decor = (rule as AndroidComposeTestRule<*, *>).activity.window.decorView
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(full))
        val l = bounds.left.roundToInt()
        val t = bounds.top.roundToInt()
        val w = bounds.width.roundToInt().coerceAtMost(full.width - l)
        val h = bounds.height.roundToInt().coerceAtMost(full.height - t)
        return Bitmap.createBitmap(full, l, t, w, h).asImageBitmap()
    }

    /**
     * Changes state from the test thread and lets Compose see it: with the main clock paused
     * the apply notification is otherwise never delivered, so nothing would recompose.
     */
    fun change(rule: ComposeContentTestRule, advanceMs: Long, block: () -> Unit) {
        block()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(advanceMs)
    }

    /** Guards a documented golden substitution: the two files must really be different images. */
    fun assertGoldensDiffer(a: String, b: String) {
        val first = ImageIO.read(File(dir, "$a.png"))
        val second = ImageIO.read(File(dir, "$b.png"))
        val differs = (0 until first.height).any { y ->
            (0 until first.width).any { x -> first.getRGB(x, y) != second.getRGB(x, y) }
        }
        if (!differs) throw AssertionError("$a and $b are identical; drop the substitution")
    }

    /** Shared scene wrapper: theme palette, fixed size, dark theme background. */
    fun render(
        rule: ComposeContentTestRule,
        name: String,
        theme: HudColorTheme,
        width: Dp,
        height: Dp,
        advanceMs: Long,
        content: @Composable () -> Unit,
        beforeCapture: (ComposeContentTestRule) -> Unit = {}
    ) {
        HudThemeController.colorTheme = theme
        try {
            rule.mainClock.autoAdvance = false
            rule.setContent {
                Box(Modifier.testTag("shot").size(width, height).background(HudColors.Background)) { content() }
            }
            rule.mainClock.advanceTimeBy(advanceMs)
            beforeCapture(rule)
            val img = capture(rule)
            val failure = check(name, img)
            if (failure != null) throw AssertionError(failure)
        } finally {
            HudThemeController.colorTheme = HudColorTheme.CLASSIC
        }
    }
}
