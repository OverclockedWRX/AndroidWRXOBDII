package com.wrxhud.hud

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.wrxhud.settings.HudColorTheme
import com.wrxhud.settings.MotionSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeMotionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun animatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun oldStoredThemeKeysMigrate() {
        assertEquals(HudColorTheme.DARK, HudColorTheme.fromStored("CLASSIC"))
        assertEquals(HudColorTheme.LIGHT, HudColorTheme.fromStored("EINK"))
        assertEquals(HudColorTheme.DARK, HudColorTheme.fromStored("PINKY"))
        assertEquals(HudColorTheme.NIGHT, HudColorTheme.fromStored("NIGHT"))
        assertEquals(HudColorTheme.RETRO_LCD, HudColorTheme.fromStored("RETRO_LCD"))
        assertEquals(HudColorTheme.DARK, HudColorTheme.fromStored(null))
        assertEquals(HudColorTheme.DARK, HudColorTheme.fromStored("junk"))
        HudColorTheme.entries.forEach { assertEquals(it, HudColorTheme.fromStored(it.name)) }
    }

    @Test
    fun themeLabels() {
        assertEquals("Handheld", HudColorTheme.HANDHELD.label)
        assertEquals(8, HudColorTheme.entries.size)
    }

    @Test
    fun unsetMotionFollowsSystem() {
        assertNull(MotionSetting.fromStored(null))
        animatorScale(1f)
        assertEquals(MotionSetting.FULL, resolveMotion(null, context))
        animatorScale(0f)
        assertEquals(MotionSetting.REDUCED, resolveMotion(null, context))
    }

    @Test
    fun explicitMotionWins() {
        animatorScale(0f)
        MotionSetting.entries.forEach {
            assertEquals(it, resolveMotion(it, context))
            assertEquals(it, MotionSetting.fromStored(it.name))
        }
    }
}
