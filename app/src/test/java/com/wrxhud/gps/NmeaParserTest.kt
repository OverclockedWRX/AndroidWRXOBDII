package com.wrxhud.gps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NmeaParserTest {
    @Test
    fun parseRmc_speedAndFix() {
        // 12.5 knots ≈ 6.43 m/s
        val line = "\$GPRMC,123519,A,4807.038,N,01131.000,E,12.5,054.7,230394,003.1,W*6A"
        val update = NmeaParser.parseLine(line)!!
        assertThat(update.hasFix).isTrue()
        assertThat(update.speedMps!!).isWithin(0.05f).of(12.5f * 0.514444f)
    }

    @Test
    fun parseRmc_void_noFix() {
        val line = "\$GPRMC,123519,V,,,,,,,230394,,,N*48"
        val update = NmeaParser.parseLine(line)!!
        assertThat(update.hasFix).isFalse()
        assertThat(update.speedMps).isNull()
    }

    @Test
    fun parseGga_quality() {
        val good = NmeaParser.parseLine("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47")!!
        assertThat(good.hasFix).isTrue()
        val bad = NmeaParser.parseLine("\$GPGGA,123519,,,,,0,,,,,,,,*64")!!
        assertThat(bad.hasFix).isFalse()
    }
}
