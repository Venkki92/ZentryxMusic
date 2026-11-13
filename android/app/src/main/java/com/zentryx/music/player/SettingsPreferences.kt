package com.zentryx.music.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

/**
 * Manages persistent MBUX theme customizations, audio focus preferences,
 * and memory buffer modes for the AutoChips AC8259P / Mercedes S203 headunit.
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
        private val KEY_RESCAN_STARTUP = booleanPreferencesKey("rescan_on_startup")
        
        // Feature 2: SDVC / GALA Speed-Dependent Volume
        private val KEY_SDVC_ENABLED = booleanPreferencesKey("sdvc_enabled")
        private val KEY_SDVC_SENSITIVITY = stringPreferencesKey("sdvc_sensitivity") // LOW, MEDIUM, HIGH

        // Feature 3: Offline Pinning & Storage
        private val KEY_STORAGE_LOCATION = stringPreferencesKey("storage_location") // INTERNAL, SDCARD, USB
        private val KEY_OFFLINE_ONLY = booleanPreferencesKey("offline_only_mode")

        // Feature 4: Steering Wheel Controls
        private val KEY_SWC_LONG_PRESS_ACTION = stringPreferencesKey("swc_long_press_action") // NEXT_ALBUM, SEEK_15S, FAVORITE
        private val KEY_SWC_DOUBLE_PRESS_ACTION = stringPreferencesKey("swc_double_press_action") // FAVORITE, MUTE

        // Feature 7: Engine Standby Fade
        private val KEY_IGNITION_FADE_ENABLED = booleanPreferencesKey("ignition_fade_enabled")
    }

    val accentColorFlow: Flow<MbuxAccentColor> = context.settingsDataStore.data.map { prefs ->
        val name = prefs[KEY_ACCENT_COLOR] ?: MbuxAccentColor.LIME.name
        try {
            MbuxAccentColor.valueOf(name)
        } catch (e: Exception) {
            MbuxAccentColor.LIME
        }
    }

    val duckingEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DUCKING_ENABLED] ?: true
    }

    val bufferModeFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_BUFFER_MODE] ?: "RAM_SAVER"
    }

    val gaplessEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_GAPLESS_ENABLED] ?: true
    }

    // Feature 2 Flows
    val sdvcEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SDVC_ENABLED] ?: true
    }

    val sdvcSensitivityFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SDVC_SENSITIVITY] ?: "MEDIUM"
    }

    // Feature 3 Flows
    val storageLocationFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STORAGE_LOCATION] ?: "INTERNAL"
    }

    val offlineOnlyFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_OFFLINE_ONLY] ?: false
    }

    // Feature 4 Flows
    val swcLongPressActionFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SWC_LONG_PRESS_ACTION] ?: "SEEK_15S"
    }

    val swcDoublePressActionFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SWC_DOUBLE_PRESS_ACTION] ?: "FAVORITE"
    }

    // Feature 7 Flow
    val ignitionFadeEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_IGNITION_FADE_ENABLED] ?: true
    }

    suspend fun setAccentColor(accent: MbuxAccentColor) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_ACCENT_COLOR] = accent.name
        }
    }

    suspend fun setDuckingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DUCKING_ENABLED] = enabled
        }
    }

    suspend fun setBufferMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_BUFFER_MODE] = mode
        }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_GAPLESS_ENABLED] = enabled
        }
    }

    suspend fun setSdvcEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SDVC_ENABLED] = enabled
        }
    }

    suspend fun setSdvcSensitivity(sensitivity: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SDVC_SENSITIVITY] = sensitivity
        }
    }

    suspend fun setStorageLocation(location: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_STORAGE_LOCATION] = location
        }
    }

    suspend fun setOfflineOnly(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_OFFLINE_ONLY] = enabled
        }
    }

    suspend fun setSwcLongPressAction(action: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SWC_LONG_PRESS_ACTION] = action
        }
    }

    suspend fun setSwcDoublePressAction(action: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SWC_DOUBLE_PRESS_ACTION] = action
        }
    }

    suspend fun setIgnitionFadeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_IGNITION_FADE_ENABLED] = enabled
        }
    }
}
