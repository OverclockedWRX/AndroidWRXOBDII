package com.wrxhud.vehicle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnitConvertersTest {
    @Test
    fun celsiusToFahrenheit() {
        assertThat(UnitConverters.celsiusToFahrenheit(0f)).isWithin(0.01f).of(32f)
        assertThat(UnitConverters.celsiusToFahrenheit(100f)).isWithin(0.01f).of(212f)
    }

    @Test
    fun mpsToMph() {
        assertThat(UnitConverters.mpsToMph(26.8224f)).isWithin(0.1f).of(60f)
    }

    @Test
    fun formatGear() {
        assertThat(UnitConverters.formatGear(null)).isEqualTo("N")
        assertThat(UnitConverters.formatGear(-1)).isEqualTo("R")
        assertThat(UnitConverters.formatGear(4)).isEqualTo("4")
    }

    @Test
    fun formatTempNull() {
        assertThat(UnitConverters.formatTemp(null, true)).isEqualTo("—")
    }

    @Test
    fun kpaToPsi() {
        assertThat(UnitConverters.kpaToPsi(100f)).isWithin(0.1f).of(14.5f)
    }
}
