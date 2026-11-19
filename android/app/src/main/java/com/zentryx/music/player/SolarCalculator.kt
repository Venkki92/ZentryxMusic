package com.zentryx.music.player

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Astronomical NOAA Solar Elevation calculator for GPS-aware automatic
 * Cockpit Day / Night Mode switching (Mercedes S203 headunit comfort).
 */
object SolarCalculator {

    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI

    /**
     * Computes true solar elevation angle in degrees for the given GPS coordinate & UTC calendar.
     */
    fun solarElevationDegrees(latitude: Double, longitude: Double, timeMillis: Long): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            this.timeInMillis = timeMillis
        }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val hourUtc = cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60.0 +
            cal.get(Calendar.SECOND) / 3600.0

        // Fractional year gamma in radians
        val gamma = (2.0 * PI / 365.0) * (dayOfYear - 1 + (hourUtc - 12.0) / 24.0)

        // Equation of time (minutes)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        // Solar declination angle (radians)
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        // True solar time (minutes)
        val trueSolarTimeMin = ((hourUtc * 60.0 + eqTime + 4.0 * longitude) % 1440.0 + 1440.0) % 1440.0
        val hourAngleDeg = (trueSolarTimeMin / 4.0) - 180.0
        val hourAngleRad = hourAngleDeg * DEG_TO_RAD

        val latRad = latitude * DEG_TO_RAD
        val cosZenith = (sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(hourAngleRad))
            .coerceIn(-1.0, 1.0)
        val zenithDeg = kotlin.math.acos(cosZenith) * RAD_TO_DEG
        return 90.0 - zenithDeg
    }

    /**
     * Returns true when the sun is above the civil twilight horizon (-0.833 degrees).
     * Falls back cleanly to local clock hours (06:30–19:30) if GPS fix is not available yet.
     */
    fun isDaylight(
        latitude: Double?,
        longitude: Double?,
        timeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (latitude != null && longitude != null) {
            return solarElevationDegrees(latitude, longitude, timeMillis) > -0.833
        }
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val minsOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return minsOfDay in (6 * 60 + 30)..(19 * 60 + 30)
    }
}
