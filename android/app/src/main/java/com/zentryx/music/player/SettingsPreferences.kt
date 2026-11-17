package com.zentryx.music.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "zentryx_settings_prefs")

enum class MbuxAccentColor(val displayName: String, val hexColor: Long) {
    LIME("Zentryx Electric Lime", 0xFFA3E635),
    AMBER("Mercedes Solar Amber", 0xFFF59E0B),
    CYAN("Deep Sky Cyan", 0xFF0082C9),
    SILVER("Silver Arrow", 0xFF94A3B8),
    RED("AMG Solar Red", 0xFFEF4444)
}

enum class CockpitThemeMode(val id: String, val label: String) {
    NIGHT("night", "Night (OLED)"),
    AUTO("auto", "Auto (Solar)"),
    DAY("day", "Day (Contrast)");

    companion object {
        fun fromId(id: String): CockpitThemeMode = entries.firstOrNull { it.id == id } ?: NIGHT
    }
}

/**
 * Manages persistent MBUX theme customizations, 16-band DSP console state,
 * visualizer modes, and headunit ergonomics for AutoChips AC8259P / Mercedes S203.
 */
@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ACCENT_COLOR = stringPreferencesKey("mbux_accent_color")
        private val KEY_DUCKING_ENABLED = booleanPreferencesKey("audio_focus_ducking_enabled")
        private val KEY_BUFFER_MODE = stringPreferencesKey("buffer_memory_mode")
        private val KEY_GAPLESS_ENABLED = booleanPreferencesKey("gapless_playback_enabled")

        // SDVC / GALA Speed-Dependent Volume
        private val KEY_SDVC_ENABLED = booleanPreferencesKey("sdvc_enabled")
        private val KEY_SDVC_SENSITIVITY = stringPreferencesKey("sdvc_sensitivity")

        // Offline Storage Target
        private val KEY_STORAGE_LOCATION = stringPreferencesKey("storage_location")
        private val KEY_OFFLINE_ONLY = booleanPreferencesKey("offline_only_mode")

        // Steering Wheel Controls (CAN-Bus SWC)
        private val KEY_SWC_LONG_PRESS_ACTION = stringPreferencesKey("swc_long_press_action")
        private val KEY_SWC_DOUBLE_PRESS_ACTION = stringPreferencesKey("swc_double_press_action")

        // Ignition & Auto-Resume QOL
        private val KEY_IGNITION_FADE_ENABLED = booleanPreferencesKey("ignition_fade_enabled")
        private val KEY_AUTO_RESUME_BOOT = booleanPreferencesKey("auto_resume_on_boot")
        private val KEY_AUTO_RESUME_SOFT_RAMP = booleanPreferencesKey("auto_resume_soft_ramp")

        // Podcast & Car Ergonomics
        private val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_SWIPE_GESTURES = booleanPreferencesKey("swipe_gestures_enabled")
        private val KEY_COCKPIT_THEME_MODE = stringPreferencesKey("cockpit_theme_mode")
        private val KEY_NIGHT_BRIGHTNESS = floatPreferencesKey("night_brightness")
        private val KEY_AMBER_FILTER = booleanPreferencesKey("amber_filter_enabled")
        private val KEY_IDLE_SCREENSAVER_SEC = intPreferencesKey("idle_screensaver_seconds")

        // 16-Band DSP Console & Visualizers
        private val KEY_DSP_SETTINGS_JSON = stringPreferencesKey("dsp_settings_json")
        private val KEY_CUSTOM_DSP_PRESETS_JSON = stringPreferencesKey("custom_dsp_presets_json")
        private val KEY_VISUALIZER_MODE = stringPreferencesKey("visualizer_mode")
        private val KEY_VISUALIZER_COLORWAY = stringPreferencesKey("visualizer_colorway")
    }

    val accentColorFlow: Flow<MbuxAccentColor> = context.settingsDataStore.data.map { prefs ->
        val name = prefs[KEY_ACCENT_COLOR] ?: MbuxAccentColor.LIME.name
        try { MbuxAccentColor.valueOf(name) } catch (_: Exception) { MbuxAccentColor.LIME }
    }

    val duckingEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_DUCKING_ENABLED] ?: true }
    val bufferModeFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_BUFFER_MODE] ?: "RAM_SAVER" }
    val gaplessEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_GAPLESS_ENABLED] ?: true }

    val sdvcEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_SDVC_ENABLED] ?: true }
    val sdvcSensitivityFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_SDVC_SENSITIVITY] ?: "MEDIUM" }

    val storageLocationFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_STORAGE_LOCATION] ?: "INTERNAL" }
    val offlineOnlyFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_OFFLINE_ONLY] ?: false }

    val swcLongPressActionFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_SWC_LONG_PRESS_ACTION] ?: "SEEK_15S" }
    val swcDoublePressActionFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_SWC_DOUBLE_PRESS_ACTION] ?: "FAVORITE" }

    val ignitionFadeEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_IGNITION_FADE_ENABLED] ?: true }
    val autoResumeOnBootFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_RESUME_BOOT] ?: true }
    val autoResumeSoftRampFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_RESUME_SOFT_RAMP] ?: true }

    val playbackSpeedFlow: Flow<Float> = context.settingsDataStore.data.map { (it[KEY_PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.75f, 2.0f) }
    val swipeGesturesEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_SWIPE_GESTURES] ?: true }
    val cockpitThemeModeFlow: Flow<CockpitThemeMode> = context.settingsDataStore.data.map {
        CockpitThemeMode.fromId(it[KEY_COCKPIT_THEME_MODE] ?: "night")
    }
    val nightBrightnessFlow: Flow<Float> = context.settingsDataStore.data.map { (it[KEY_NIGHT_BRIGHTNESS] ?: 1.0f).coerceIn(0.35f, 1.0f) }
    val amberFilterEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AMBER_FILTER] ?: false }
    val idleScreensaverSecondsFlow: Flow<Int> = context.settingsDataStore.data.map { it[KEY_IDLE_SCREENSAVER_SEC] ?: 0 }

    val dspSettingsJsonFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_DSP_SETTINGS_JSON] }
    val customDspPresetsJsonFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_CUSTOM_DSP_PRESETS_JSON] }
    val visualizerModeFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_VISUALIZER_MODE] ?: "gauge" }
    val visualizerColorwayFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_VISUALIZER_COLORWAY] ?: "amber" }

    suspend fun setAccentColor(accent: MbuxAccentColor) = context.settingsDataStore.edit { it[KEY_ACCENT_COLOR] = accent.name }
    suspend fun setDuckingEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_DUCKING_ENABLED] = enabled }
    suspend fun setBufferMode(mode: String) = context.settingsDataStore.edit { it[KEY_BUFFER_MODE] = mode }
    suspend fun setGaplessEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_GAPLESS_ENABLED] = enabled }
    suspend fun setSdvcEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_SDVC_ENABLED] = enabled }
    suspend fun setSdvcSensitivity(sensitivity: String) = context.settingsDataStore.edit { it[KEY_SDVC_SENSITIVITY] = sensitivity }
    suspend fun setStorageLocation(location: String) = context.settingsDataStore.edit { it[KEY_STORAGE_LOCATION] = location }
    suspend fun setOfflineOnly(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_OFFLINE_ONLY] = enabled }
    suspend fun setSwcLongPressAction(action: String) = context.settingsDataStore.edit { it[KEY_SWC_LONG_PRESS_ACTION] = action }
    suspend fun setSwcDoublePressAction(action: String) = context.settingsDataStore.edit { it[KEY_SWC_DOUBLE_PRESS_ACTION] = action }
    suspend fun setIgnitionFadeEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_IGNITION_FADE_ENABLED] = enabled }
    suspend fun setAutoResumeOnBoot(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_AUTO_RESUME_BOOT] = enabled }
    suspend fun setAutoResumeSoftRamp(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_AUTO_RESUME_SOFT_RAMP] = enabled }
    suspend fun setPlaybackSpeed(speed: Float) = context.settingsDataStore.edit { it[KEY_PLAYBACK_SPEED] = speed.coerceIn(0.75f, 2.0f) }
    suspend fun setSwipeGesturesEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_SWIPE_GESTURES] = enabled }
    suspend fun setCockpitThemeMode(mode: CockpitThemeMode) = context.settingsDataStore.edit { it[KEY_COCKPIT_THEME_MODE] = mode.id }
    suspend fun setNightBrightness(level: Float) = context.settingsDataStore.edit { it[KEY_NIGHT_BRIGHTNESS] = level.coerceIn(0.35f, 1.0f) }
    suspend fun setAmberFilterEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_AMBER_FILTER] = enabled }
    suspend fun setIdleScreensaverSeconds(seconds: Int) = context.settingsDataStore.edit { it[KEY_IDLE_SCREENSAVER_SEC] = seconds }
    suspend fun saveDspSettingsJson(json: String) = context.settingsDataStore.edit { it[KEY_DSP_SETTINGS_JSON] = json }
    suspend fun saveCustomDspPresetsJson(json: String) = context.settingsDataStore.edit { it[KEY_CUSTOM_DSP_PRESETS_JSON] = json }
    suspend fun setVisualizerMode(modeId: String) = context.settingsDataStore.edit { it[KEY_VISUALIZER_MODE] = modeId }
    suspend fun setVisualizerColorway(colorwayId: String) = context.settingsDataStore.edit { it[KEY_VISUALIZER_COLORWAY] = colorwayId }
}
