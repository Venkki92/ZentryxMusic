package com.zentryx.music.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SolarCalculatorTest {

    @Test
    fun solarElevationIsPositiveAtNoonAndNegativeAtMidnightForSofia() {
        val lat = 42.6977
        val lon = 23.3219

        // June 21 solstice 10:00 UTC (~13:00 Sofia EEST local noon)
        val noonCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JUNE, 21, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val noonElevation = SolarCalculator.solarElevationDegrees(lat, lon, noonCal.timeInMillis)
        assertTrue("Expected high noon sun (>50 deg), got $noonElevation", noonElevation > 50.0)
        assertTrue(SolarCalculator.isDaylight(lat, lon, noonCal.timeInMillis))

        // June 21 22:00 UTC (~01:00 Sofia night)
        val nightCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JUNE, 21, 22, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nightElevation = SolarCalculator.solarElevationDegrees(lat, lon, nightCal.timeInMillis)
        assertTrue("Expected night solar elevation (< -10 deg), got $nightElevation", nightElevation < -10.0)
        assertFalse(SolarCalculator.isDaylight(lat, lon, nightCal.timeInMillis))
    }

    @Test
    fun nullCoordinatesFallBackToLocalClockDaylightWindow() {
        val prevTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            val dayCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.OCTOBER, 10, 12, 30, 0)
                set(Calendar.MILLISECOND, 0)
            }
            assertTrue(SolarCalculator.isDaylight(null, null, dayCal.timeInMillis))

            val nightCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.OCTOBER, 10, 23, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            assertFalse(SolarCalculator.isDaylight(null, null, nightCal.timeInMillis))
        } finally {
            TimeZone.setDefault(prevTz)
        }
    }
}
