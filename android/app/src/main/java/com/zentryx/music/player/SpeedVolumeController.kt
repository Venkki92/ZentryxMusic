package com.zentryx.music.player

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

enum class GpsTelemetryStatus(val label: String) {
    ACTIVE("Locked"),
    WAITING("Waiting for GPS fix…"),
    PERMISSION_DENIED("Location permission needed"),
    GPS_OFF("SCV disabled"),
    UNAVAILABLE("GPS provider off / unavailable")
}

/**
 * Speed-Dependent Volume Control (SDVC / GALA) and Solar GPS Cockpit Day/Night tracker
 * for AutoChips AC8259P / Mercedes-Benz S203.
 */
@Singleton
class SpeedVolumeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences
) : LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentSpeedKmH = MutableStateFlow(0f)
    val currentSpeedKmH: StateFlow<Float> = _currentSpeedKmH.asStateFlow()

    private val _volumeMultiplier = MutableStateFlow(1.0f)
    val volumeMultiplier: StateFlow<Float> = _volumeMultiplier.asStateFlow()

    private val _currentBoostDb = MutableStateFlow(0f)
    val currentBoostDb: StateFlow<Float> = _currentBoostDb.asStateFlow()

    private val _gpsStatus = MutableStateFlow(GpsTelemetryStatus.WAITING)
    val gpsStatus: StateFlow<GpsTelemetryStatus> = _gpsStatus.asStateFlow()

    private val _isDaylightBySolar = MutableStateFlow(SolarCalculator.isDaylight(null, null))
    val isDaylightBySolar: StateFlow<Boolean> = _isDaylightBySolar.asStateFlow()

    private var isSdvcEnabled = true
    private var isSolarAutoTheme = false
    private var sensitivity = "MEDIUM"
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastCoordPersistMs = 0L

    init {
        scope.launch {
            settingsPreferences.lastGpsCoordsFlow.collectLatest { coords ->
                if (coords != null && (lastLat == null || lastLon == null)) {
                    lastLat = coords.first
                    lastLon = coords.second
                    updateSolarDaylight()
                }
            }
        }

        scope.launch {
            settingsPreferences.cockpitThemeModeFlow.collectLatest { mode ->
                isSolarAutoTheme = (mode == CockpitThemeMode.AUTO)
                updateSolarDaylight()
                refreshGps()
            }
        }

        scope.launch {
            settingsPreferences.sdvcEnabledFlow.collectLatest { enabled ->
                isSdvcEnabled = enabled
                recalculateVolumeMultiplier(_currentSpeedKmH.value)
                refreshGps()
            }
        }

        scope.launch {
            settingsPreferences.sdvcSensitivityFlow.collectLatest { sens ->
                sensitivity = sens
                recalculateVolumeMultiplier(_currentSpeedKmH.value)
            }
        }

        // Periodic solar horizon re-check every 60 seconds
        scope.launch {
            while (isActive) {
                updateSolarDaylight()
                delay(60_000L)
            }
        }

        refreshGps()
    }

    private fun updateSolarDaylight() {
        _isDaylightBySolar.value = SolarCalculator.isDaylight(lastLat, lastLon)
    }

    @SuppressLint("MissingPermission")
    fun refreshGps() {
        val needsGps = isSdvcEnabled || isSolarAutoTheme
        if (!needsGps) {
            try {
                locationManager?.removeUpdates(this)
            } catch (_: Throwable) {}
            _gpsStatus.value = GpsTelemetryStatus.GPS_OFF
            _currentSpeedKmH.value = 0f
            recalculateVolumeMultiplier(0f)
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _gpsStatus.value = GpsTelemetryStatus.PERMISSION_DENIED
            return
        }

        if (locationManager == null) {
            _gpsStatus.value = GpsTelemetryStatus.UNAVAILABLE
            return
        }

        try {
            val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (!hasGps) {
                _gpsStatus.value = GpsTelemetryStatus.UNAVAILABLE
                return
            }

            locationManager.removeUpdates(this)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                if (isSdvcEnabled) 1000L else 15_000L,
                if (isSdvcEnabled) 2f else 250f,
                this,
                Looper.getMainLooper()
            )
            _gpsStatus.value = GpsTelemetryStatus.WAITING

            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { lastLoc ->
                lastLat = lastLoc.latitude
                lastLon = lastLoc.longitude
                updateSolarDaylight()
                if (System.currentTimeMillis() - lastLoc.time < 60_000L && lastLoc.hasSpeed()) {
                    onLocationChanged(lastLoc)
                }
            }
        } catch (_: Throwable) {
            _gpsStatus.value = GpsTelemetryStatus.UNAVAILABLE
        }
    }

    override fun onLocationChanged(location: Location) {
        _gpsStatus.value = GpsTelemetryStatus.ACTIVE
        lastLat = location.latitude
        lastLon = location.longitude
        updateSolarDaylight()

        val now = System.currentTimeMillis()
        if (now - lastCoordPersistMs > 60_000L) {
            lastCoordPersistMs = now
            scope.launch {
                settingsPreferences.setLastGpsCoords(location.latitude, location.longitude)
            }
        }

        if (isSdvcEnabled && location.hasSpeed()) {
            val speedKmH = (location.speed * 3.6f).coerceIn(0f, 260f)
            val smoothedSpeed = (_currentSpeedKmH.value * 0.65f) + (speedKmH * 0.35f)
            _currentSpeedKmH.value = max(0f, smoothedSpeed)
            recalculateVolumeMultiplier(_currentSpeedKmH.value)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) refreshGps()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _gpsStatus.value = GpsTelemetryStatus.UNAVAILABLE
        }
    }

    private fun recalculateVolumeMultiplier(speedKmH: Float) {
        if (!isSdvcEnabled || speedKmH < 40f) {
            _volumeMultiplier.value = 1.0f
            _currentBoostDb.value = 0f
            return
        }

        val normalizedSpeed = min(1.0f, (speedKmH - 40f) / 100f)
        val maxLift = when (sensitivity) {
            "LOW" -> 0.18f
            "HIGH" -> 0.77f
            else -> 0.41f
        }

        val targetMultiplier = 1.0f + (normalizedSpeed * maxLift)
        _volumeMultiplier.value = targetMultiplier
        _currentBoostDb.value = (20f * log10(targetMultiplier)).coerceIn(0f, 5.5f)
    }
}
