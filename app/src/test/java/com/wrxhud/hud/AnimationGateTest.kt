package com.wrxhud.hud

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The looping animation phase only runs while RESUMED, active and system animations are on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimationGateTest {
    @get:Rule
    val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = Owner()
    private lateinit var phase: MutableFloatState

    private fun start(active: Boolean, state: Lifecycle.State) {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { owner.registry.currentState = state }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                phase = rememberLoopPhase(active = active, periodNs = 4_000_000_000L)
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun samples(): List<Float> = List(4) {
        rule.mainClock.advanceTimeBy(160)
        phase.floatValue
    }

    @Test
    fun runsWhileResumed_stopsWhenPaused_resumesAfterwards() {
        start(active = true, state = Lifecycle.State.RESUMED)
        assertThat(samples().distinct().size).isGreaterThan(1)

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.mainClock.advanceTimeBy(100)
        assertThat(samples().distinct()).hasSize(1)

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.mainClock.advanceTimeBy(100)
        assertThat(samples().distinct().size).isGreaterThan(1)
    }

    @Test
    fun neverStartsWhenNotResumed() {
        start(active = true, state = Lifecycle.State.STARTED)
        assertThat(samples().distinct()).containsExactly(0f)
    }

    @Test
    fun inactiveStyleDoesNotAnimate() {
        start(active = false, state = Lifecycle.State.RESUMED)
        assertThat(samples().distinct()).containsExactly(0f)
    }

    @Test
    fun frozenWhenSystemAnimationsAreOff() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        start(active = true, state = Lifecycle.State.RESUMED)
        assertThat(samples().distinct()).containsExactly(0f)
    }
}
