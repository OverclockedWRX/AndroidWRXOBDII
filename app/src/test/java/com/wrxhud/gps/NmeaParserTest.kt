package com.wrxhud.gps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NmeaParserTest {
    @Test
    fun parseRmc_speedAndFix() {
        // 12.5 knots ≈ 6.43 m/s; 48°07.038'N 11°31.000'E
        val line = "\$GPRMC,123519,A,4807.038,N,01131.000,E,12.5,054.7,230394,003.1,W*6A"
        val update = NmeaParser.parseLine(line)!!
        assertThat(update.hasFix).isTrue()
        assertThat(update.speedMps!!).isWithin(0.05f).of(12.5f * 0.514444f)
        assertThat(update.latitude!!).isWithin(0.0001).of(48.0 + 7.038 / 60.0)
        assertThat(update.longitude!!).isWithin(0.0001).of(11.0 + 31.0 / 60.0)
    }

    @Test
    fun nmeaToDecimal_southernWestern() {
        assertThat(NmeaParser.nmeaToDecimal("", "S", isLat = true)).isNull()
        assertThat(NmeaParser.nmeaToDecimal("3341.123", "S", isLat = true)!!)
            .isWithin(0.0001).of(-(33.0 + 41.123 / 60.0))
        assertThat(NmeaParser.nmeaToDecimal("11815.500", "W", isLat = false)!!)
            .isWithin(0.0001).of(-(118.0 + 15.5 / 60.0))
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
