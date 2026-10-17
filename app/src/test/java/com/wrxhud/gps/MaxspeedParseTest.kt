package com.wrxhud.gps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaxspeedParseTest {
    @Test
    fun parsesMphAndKph() {
        assertThat(parseMaxspeedToKph("25 mph")).isWithin(0.1).of(40.2336)
        assertThat(parseMaxspeedToKph("50")).isWithin(0.01).of(50.0)
        assertThat(parseMaxspeedToKph("80 km/h")).isWithin(0.01).of(80.0)
        assertThat(parseMaxspeedToKph("30 mph")).isWithin(0.1).of(48.2803)
    }

    @Test
    fun rejectsNonNumeric() {
        assertThat(parseMaxspeedToKph("signals")).isNull()
        assertThat(parseMaxspeedToKph("none")).isNull()
        assertThat(parseMaxspeedToKph("DE:urban")).isNull()
        assertThat(parseMaxspeedToKph("")).isNull()
    }
}
