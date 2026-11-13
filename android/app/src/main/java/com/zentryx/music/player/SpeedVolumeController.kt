package com.zentryx.music.player

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Feature 2: Speed-Dependent Volume Control (SDVC / GALA) for Mercedes-Benz S203 cockpit.
 * Reads GPS speed via AutoChips AC8259P GNSS receiver.
 * Dynamically compensates for OM646 diesel rumble and tire/wind noise as highway speed increases.
 */
@Singleton
class SpeedVolumeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences
) : LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentSpeedKmH = MutableStateFlow(0f)
    val currentSpeedKmH: StateFlow<Float> = _currentSpeedKmH.asStateFlow()

    private val _volumeMultiplier = MutableStateFlow(1.0f)
    val volumeMultiplier: StateFlow<Float> = _volumeMultiplier.asStateFlow()

    private var isSdvcEnabled = true
    private var sensitivity = "MEDIUM"

    init {
        scope.launch {
            settingsPreferences.sdvcEnabledFlow.collectLatest { enabled ->
                isSdvcEnabled = enabled
                recalculateVolumeMultiplier(_currentSpeedKmH.value)
            }
        }

        scope.launch {
            settingsPreferences.sdvcSensitivityFlow.collectLatest { sens ->
                sensitivity = sens
                recalculateVolumeMultiplier(_currentSpeedKmH.value)
            }
        }

        startGpsListening()
    }

    @SuppressLint("MissingPermission")
    private fun startGpsListening() {
        if (locationManager == null) return
        try {
            val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (hasGps) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 second intervals
                    2f,    // 2 meters delta
                    this,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            // Location permissions or mock provider fallback
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            val speedKmH = location.speed * 3.6f // m/s to km/h
            val smoothedSpeed = (_currentSpeedKmH.value * 0.7f) + (speedKmH * 0.3f)
            _currentSpeedKmH.value = max(0f, smoothedSpeed)
            recalculateVolumeMultiplier(_currentSpeedKmH.value)
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun recalculateVolumeMultiplier(speedKmH: Float) {
        if (!isSdvcEnabled || speedKmH < 40f) {
            _volumeMultiplier.value = 1.0f
            return
        }

        // Base activation starts at 40 km/h, max lift at 140 km/h
        val normalizedSpeed = min(1.0f, (speedKmH - 40f) / 100f)

        // dB Lift: LOW = +1.5 dB (~1.18x), MEDIUM = +3.0 dB (~1.41x), HIGH = +5.0 dB (~1.77x)
        val maxLift = when (sensitivity) {
            "LOW" -> 0.18f
            "HIGH" -> 0.77f
            else -> 0.41f // MEDIUM
        }

        val targetMultiplier = 1.0f + (normalizedSpeed * maxLift)
        _volumeMultiplier.value = targetMultiplier
    }
}
