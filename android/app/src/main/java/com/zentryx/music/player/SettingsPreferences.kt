package com.zentryx.music.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
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
 * Manages persistent MBUX theme customizations, ReplayGain/Crossfade audio settings,
 * local USB scanner paths, 16-band DSP console state, and headunit ergonomics.
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
        private val KEY_REPLAYGAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
        private val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")

        // SDVC / GALA Speed-Dependent Volume & Solar GPS coords
        private val KEY_SDVC_ENABLED = booleanPreferencesKey("sdvc_enabled")
        private val KEY_SDVC_SENSITIVITY = stringPreferencesKey("sdvc_sensitivity")
        private val KEY_LAST_GPS_LAT = doublePreferencesKey("last_gps_lat")
        private val KEY_LAST_GPS_LON = doublePreferencesKey("last_gps_lon")

        // Offline Storage Target & Local USB Scanner Source
        private val KEY_STORAGE_LOCATION = stringPreferencesKey("storage_location")
        private val KEY_CUSTOM_DOWNLOAD_URI = stringPreferencesKey("custom_download_tree_uri")
        private val KEY_CUSTOM_DOWNLOAD_LABEL = stringPreferencesKey("custom_download_folder_label")
        private val KEY_LOCAL_SOURCE_URI = stringPreferencesKey("local_source_tree_uri")
        private val KEY_LOCAL_SOURCE_LABEL = stringPreferencesKey("local_source_folder_label")
        private val KEY_EXTRA_LOCAL_URIS_JSON = stringPreferencesKey("extra_local_source_uris_json")
        private val KEY_EXTRA_WEBDAV_FOLDERS_JSON = stringPreferencesKey("extra_webdav_scan_folders_json")
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
        private val KEY_ADAPTIVE_CABIN_CONFIG_JSON = stringPreferencesKey("adaptive_cabin_config_json")
        private val KEY_VISUALIZER_MODE = stringPreferencesKey("visualizer_mode")
        private val KEY_VISUALIZER_COLORWAY = stringPreferencesKey("visualizer_colorway")

        // Widescreen Dual-Pane Cockpit & Drive Mode HUD
        private val KEY_DUAL_PANE_COCKPIT = booleanPreferencesKey("dual_pane_cockpit")
        private val KEY_DRIVE_MODE_AUTO_SPEED_KMH = intPreferencesKey("drive_mode_auto_speed_kmh")

        // Headunit Hardware Automation, Global EQ, Floating Overlay, Driver Profiles & SWC Remapper
        private val KEY_AUTO_PLAY_ON_BLUETOOTH = booleanPreferencesKey("auto_play_on_bluetooth")
        private val KEY_DUCK_ON_REVERSE_GEAR = booleanPreferencesKey("duck_on_reverse_gear")
        private val KEY_GLOBAL_AUDIO_SESSION_ATTACH = booleanPreferencesKey("global_audio_session_attach")
        private val KEY_FLOATING_OVERLAY_ENABLED = booleanPreferencesKey("floating_overlay_enabled")
        private val KEY_DRIVER_PROFILES_JSON = stringPreferencesKey("driver_profiles_json")
        private val KEY_ACTIVE_DRIVER_PROFILE_ID = stringPreferencesKey("active_driver_profile_id")
        private val KEY_SWC_CUSTOM_KEYMAP_JSON = stringPreferencesKey("swc_custom_keymap_json")
        private val KEY_CRUISING_SORT_BY_GENRE = booleanPreferencesKey("cruising_sort_by_genre")
    }

    val accentColorFlow: Flow<MbuxAccentColor> = context.settingsDataStore.data.map { prefs ->
        val name = prefs[KEY_ACCENT_COLOR] ?: MbuxAccentColor.LIME.name
        try { MbuxAccentColor.valueOf(name) } catch (_: Exception) { MbuxAccentColor.LIME }
    }

    val duckingEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_DUCKING_ENABLED] ?: true }
    val bufferModeFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_BUFFER_MODE] ?: "RAM_SAVER" }
    val gaplessEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_GAPLESS_ENABLED] ?: true }
    val replayGainEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_REPLAYGAIN_ENABLED] ?: true }
    val crossfadeSecondsFlow: Flow<Int> = context.settingsDataStore.data.map { (it[KEY_CROSSFADE_SECONDS] ?: 0).coerceIn(0, 8) }

    val sdvcEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_SDVC_ENABLED] ?: true }
    val sdvcSensitivityFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_SDVC_SENSITIVITY] ?: "MEDIUM" }
    val lastGpsCoordsFlow: Flow<Pair<Double, Double>?> = context.settingsDataStore.data.map { prefs ->
        val lat = prefs[KEY_LAST_GPS_LAT]
        val lon = prefs[KEY_LAST_GPS_LON]
        if (lat != null && lon != null) lat to lon else null
    }

    val storageLocationFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_STORAGE_LOCATION] ?: "INTERNAL" }
    val customDownloadUriFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_CUSTOM_DOWNLOAD_URI] }
    val customDownloadLabelFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_CUSTOM_DOWNLOAD_LABEL] }
    val localSourceUriFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_LOCAL_SOURCE_URI] }
    val localSourceLabelFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_LOCAL_SOURCE_LABEL] }
    val localSourceUrisFlow: Flow<List<Pair<String, String>>> = context.settingsDataStore.data.map { prefs ->
        val out = linkedMapOf<String, String>()
        prefs[KEY_LOCAL_SOURCE_URI]?.takeIf { it.isNotBlank() }?.let { uri ->
            out[uri] = prefs[KEY_LOCAL_SOURCE_LABEL] ?: "Local USB Folder"
        }
        val rawJson = prefs[KEY_EXTRA_LOCAL_URIS_JSON]
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val u = obj.optString("uri").trim()
                    val lbl = obj.optString("label").ifBlank { "Local Folder" }
                    if (u.isNotEmpty()) out[u] = lbl
                }
            }
        }
        out.entries.map { it.key to it.value }
    }
    val extraWebDavFoldersFlow: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val rawJson = prefs[KEY_EXTRA_WEBDAV_FOLDERS_JSON] ?: return@map emptyList()
        runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }.getOrDefault(emptyList())
    }
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
    val adaptiveCabinConfigJsonFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_ADAPTIVE_CABIN_CONFIG_JSON] }
    val visualizerModeFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_VISUALIZER_MODE] ?: "gauge" }
    val visualizerColorwayFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_VISUALIZER_COLORWAY] ?: "amber" }
    val dualPaneCockpitFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_DUAL_PANE_COCKPIT] ?: true }
    val driveModeAutoSpeedKmHFlow: Flow<Int> = context.settingsDataStore.data.map { it[KEY_DRIVE_MODE_AUTO_SPEED_KMH] ?: 0 }

    val autoPlayOnBluetoothFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_PLAY_ON_BLUETOOTH] ?: true }
    val duckOnReverseGearFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_DUCK_ON_REVERSE_GEAR] ?: true }
    val globalAudioSessionAttachFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_GLOBAL_AUDIO_SESSION_ATTACH] ?: false }
    val floatingOverlayEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_FLOATING_OVERLAY_ENABLED] ?: false }
    val driverProfilesJsonFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_DRIVER_PROFILES_JSON] }
    val activeDriverProfileIdFlow: Flow<String> = context.settingsDataStore.data.map { it[KEY_ACTIVE_DRIVER_PROFILE_ID] ?: "driver_venki" }
    val swcCustomKeyMapJsonFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_SWC_CUSTOM_KEYMAP_JSON] }
    val cruisingSortByGenreFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_CRUISING_SORT_BY_GENRE] ?: true }

    suspend fun setAccentColor(accent: MbuxAccentColor) = context.settingsDataStore.edit { it[KEY_ACCENT_COLOR] = accent.name }
    suspend fun setDuckingEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_DUCKING_ENABLED] = enabled }
    suspend fun setBufferMode(mode: String) = context.settingsDataStore.edit { it[KEY_BUFFER_MODE] = mode }
    suspend fun setGaplessEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_GAPLESS_ENABLED] = enabled }
    suspend fun setReplayGainEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_REPLAYGAIN_ENABLED] = enabled }
    suspend fun setCrossfadeSeconds(seconds: Int) = context.settingsDataStore.edit { it[KEY_CROSSFADE_SECONDS] = seconds.coerceIn(0, 8) }
    suspend fun setSdvcEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_SDVC_ENABLED] = enabled }
    suspend fun setSdvcSensitivity(sensitivity: String) = context.settingsDataStore.edit { it[KEY_SDVC_SENSITIVITY] = sensitivity }
    suspend fun setLastGpsCoords(lat: Double, lon: Double) = context.settingsDataStore.edit {
        it[KEY_LAST_GPS_LAT] = lat
        it[KEY_LAST_GPS_LON] = lon
    }
    suspend fun setStorageLocation(location: String) = context.settingsDataStore.edit { it[KEY_STORAGE_LOCATION] = location }
    suspend fun setCustomDownloadFolder(uri: String?, label: String?) = context.settingsDataStore.edit { prefs ->
        if (uri.isNullOrBlank()) {
            prefs.remove(KEY_CUSTOM_DOWNLOAD_URI)
            prefs.remove(KEY_CUSTOM_DOWNLOAD_LABEL)
            if (prefs[KEY_STORAGE_LOCATION] == "CUSTOM") {
                prefs[KEY_STORAGE_LOCATION] = "INTERNAL"
            }
        } else {
            prefs[KEY_CUSTOM_DOWNLOAD_URI] = uri
            prefs[KEY_CUSTOM_DOWNLOAD_LABEL] = label ?: "Custom Folder"
            prefs[KEY_STORAGE_LOCATION] = "CUSTOM"
        }
    }
    suspend fun setLocalSourceFolder(uri: String?, label: String?) = context.settingsDataStore.edit { prefs ->
        if (uri.isNullOrBlank()) {
            prefs.remove(KEY_LOCAL_SOURCE_URI)
            prefs.remove(KEY_LOCAL_SOURCE_LABEL)
        } else {
            prefs[KEY_LOCAL_SOURCE_URI] = uri
            prefs[KEY_LOCAL_SOURCE_LABEL] = label ?: "Local USB Folder"
        }
    }

    suspend fun addLocalSourceFolder(uri: String, label: String?) = context.settingsDataStore.edit { prefs ->
        val cleanUri = uri.trim()
        if (cleanUri.isEmpty()) return@edit
        val cleanLabel = label?.takeIf { it.isNotBlank() } ?: "Local USB Folder"
        prefs[KEY_LOCAL_SOURCE_URI] = cleanUri
        prefs[KEY_LOCAL_SOURCE_LABEL] = cleanLabel

        val existing = linkedMapOf<String, String>()
        prefs[KEY_EXTRA_LOCAL_URIS_JSON]?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val u = obj.optString("uri").trim()
                    if (u.isNotEmpty()) existing[u] = obj.optString("label").ifBlank { "Local Folder" }
                }
            }
        }
        existing[cleanUri] = cleanLabel
        val outArr = JSONArray()
        existing.forEach { (u, lbl) ->
            outArr.put(JSONObject().put("uri", u).put("label", lbl))
        }
        prefs[KEY_EXTRA_LOCAL_URIS_JSON] = outArr.toString()
    }

    suspend fun addExtraWebDavFolder(folderPath: String) = context.settingsDataStore.edit { prefs ->
        val trimmed = folderPath.trim()
        val normalized = if (trimmed == "/") "/" else trimmed.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return@edit

        val current = mutableListOf<String>()
        prefs[KEY_EXTRA_WEBDAV_FOLDERS_JSON]?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) current.add(v)
                }
            }
        }
        if (current.none { it.equals(normalized, ignoreCase = true) }) {
            current.add(normalized)
        }
        val outArr = JSONArray()
        current.forEach { outArr.put(it) }
        prefs[KEY_EXTRA_WEBDAV_FOLDERS_JSON] = outArr.toString()
    }

    suspend fun removeExtraWebDavFolder(folderPath: String) = context.settingsDataStore.edit { prefs ->
        val target = if (folderPath.trim() == "/") "/" else folderPath.trim().replace('\\', '/').trim('/')
        val current = mutableListOf<String>()
        prefs[KEY_EXTRA_WEBDAV_FOLDERS_JSON]?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty() && !v.equals(target, ignoreCase = true)) {
                        current.add(v)
                    }
                }
            }
        }
        val outArr = JSONArray()
        current.forEach { outArr.put(it) }
        prefs[KEY_EXTRA_WEBDAV_FOLDERS_JSON] = outArr.toString()
    }

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
    suspend fun saveAdaptiveCabinConfigJson(json: String) = context.settingsDataStore.edit { it[KEY_ADAPTIVE_CABIN_CONFIG_JSON] = json }
    suspend fun setVisualizerMode(modeId: String) = context.settingsDataStore.edit { it[KEY_VISUALIZER_MODE] = modeId }
    suspend fun setVisualizerColorway(colorwayId: String) = context.settingsDataStore.edit { it[KEY_VISUALIZER_COLORWAY] = colorwayId }
    suspend fun setDualPaneCockpit(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_DUAL_PANE_COCKPIT] = enabled }
    suspend fun setDriveModeAutoSpeedKmH(speedKmH: Int) = context.settingsDataStore.edit { it[KEY_DRIVE_MODE_AUTO_SPEED_KMH] = speedKmH.coerceIn(0, 60) }
    suspend fun setAutoPlayOnBluetooth(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_AUTO_PLAY_ON_BLUETOOTH] = enabled }
    suspend fun setDuckOnReverseGear(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_DUCK_ON_REVERSE_GEAR] = enabled }
    suspend fun setGlobalAudioSessionAttach(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_GLOBAL_AUDIO_SESSION_ATTACH] = enabled }
    suspend fun setFloatingOverlayEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_FLOATING_OVERLAY_ENABLED] = enabled }
    suspend fun saveDriverProfilesJson(json: String) = context.settingsDataStore.edit { it[KEY_DRIVER_PROFILES_JSON] = json }
    suspend fun setActiveDriverProfileId(id: String) = context.settingsDataStore.edit { it[KEY_ACTIVE_DRIVER_PROFILE_ID] = id }
    suspend fun saveSwcCustomKeyMapJson(json: String) = context.settingsDataStore.edit { it[KEY_SWC_CUSTOM_KEYMAP_JSON] = json }
    suspend fun setCruisingSortByGenre(enabled: Boolean) = context.settingsDataStore.edit { it[KEY_CRUISING_SORT_BY_GENRE] = enabled }

    /**
     * Exports non-secret settings & DSP curve state as a portable JSON backup string.
     */
    suspend fun exportBackupJson(serverUrl: String?, musicFolder: String?): String {
        val prefs = context.settingsDataStore.data.firstOrNull()
        return JSONObject().apply {
            put("app", "ZentryxMusic")
            put("schema", 1)
            put("exportedAt", System.currentTimeMillis())
            serverUrl?.let { put("nextcloudUrl", it) }
            musicFolder?.let { put("musicFolder", it) }
            put("accentColor", prefs?.get(KEY_ACCENT_COLOR) ?: MbuxAccentColor.LIME.name)
            put("cockpitThemeMode", prefs?.get(KEY_COCKPIT_THEME_MODE) ?: "night")
            put("nightBrightness", (prefs?.get(KEY_NIGHT_BRIGHTNESS) ?: 1.0f).toDouble())
            put("amberFilterEnabled", prefs?.get(KEY_AMBER_FILTER) ?: false)
            put("duckingEnabled", prefs?.get(KEY_DUCKING_ENABLED) ?: true)
            put("bufferMode", prefs?.get(KEY_BUFFER_MODE) ?: "RAM_SAVER")
            put("gaplessEnabled", prefs?.get(KEY_GAPLESS_ENABLED) ?: true)
            put("replayGainEnabled", prefs?.get(KEY_REPLAYGAIN_ENABLED) ?: true)
            put("crossfadeSeconds", prefs?.get(KEY_CROSSFADE_SECONDS) ?: 0)
            put("sdvcEnabled", prefs?.get(KEY_SDVC_ENABLED) ?: true)
            put("sdvcSensitivity", prefs?.get(KEY_SDVC_SENSITIVITY) ?: "MEDIUM")
            put("swcLongPressAction", prefs?.get(KEY_SWC_LONG_PRESS_ACTION) ?: "SEEK_15S")
            put("swcDoublePressAction", prefs?.get(KEY_SWC_DOUBLE_PRESS_ACTION) ?: "FAVORITE")
            put("ignitionFadeEnabled", prefs?.get(KEY_IGNITION_FADE_ENABLED) ?: true)
            put("autoResumeOnBoot", prefs?.get(KEY_AUTO_RESUME_BOOT) ?: true)
            put("autoResumeSoftRamp", prefs?.get(KEY_AUTO_RESUME_SOFT_RAMP) ?: true)
            put("playbackSpeed", (prefs?.get(KEY_PLAYBACK_SPEED) ?: 1.0f).toDouble())
            put("swipeGesturesEnabled", prefs?.get(KEY_SWIPE_GESTURES) ?: true)
            put("idleScreensaverSeconds", prefs?.get(KEY_IDLE_SCREENSAVER_SEC) ?: 0)
            put("visualizerMode", prefs?.get(KEY_VISUALIZER_MODE) ?: "gauge")
            put("visualizerColorway", prefs?.get(KEY_VISUALIZER_COLORWAY) ?: "amber")
            put("dualPaneCockpit", prefs?.get(KEY_DUAL_PANE_COCKPIT) ?: true)
            put("driveModeAutoSpeedKmH", prefs?.get(KEY_DRIVE_MODE_AUTO_SPEED_KMH) ?: 0)
            put("autoPlayOnBluetooth", prefs?.get(KEY_AUTO_PLAY_ON_BLUETOOTH) ?: true)
            put("duckOnReverseGear", prefs?.get(KEY_DUCK_ON_REVERSE_GEAR) ?: true)
            put("globalAudioSessionAttach", prefs?.get(KEY_GLOBAL_AUDIO_SESSION_ATTACH) ?: false)
            put("cruisingSortByGenre", prefs?.get(KEY_CRUISING_SORT_BY_GENRE) ?: true)
            put("activeDriverProfileId", prefs?.get(KEY_ACTIVE_DRIVER_PROFILE_ID) ?: "driver_venki")
            prefs?.get(KEY_DRIVER_PROFILES_JSON)?.let { put("driverProfilesJson", it) }
            prefs?.get(KEY_SWC_CUSTOM_KEYMAP_JSON)?.let { put("swcCustomKeyMapJson", it) }
            prefs?.get(KEY_DSP_SETTINGS_JSON)?.let { put("dspSettingsJson", it) }
            prefs?.get(KEY_CUSTOM_DSP_PRESETS_JSON)?.let { put("customDspPresetsJson", it) }
            prefs?.get(KEY_ADAPTIVE_CABIN_CONFIG_JSON)?.let { put("adaptiveCabinConfigJson", it) }
        }.toString(2)
    }

    /**
     * Restores settings & DSP curves from a portable JSON backup string.
     */
    suspend fun restoreBackupJson(rawJson: String): Boolean {
        return try {
            val obj = JSONObject(rawJson)
            context.settingsDataStore.edit { prefs ->
                obj.optString("accentColor").takeIf { it.isNotBlank() }?.let { prefs[KEY_ACCENT_COLOR] = it }
                obj.optString("cockpitThemeMode").takeIf { it.isNotBlank() }?.let { prefs[KEY_COCKPIT_THEME_MODE] = it }
                if (obj.has("nightBrightness")) prefs[KEY_NIGHT_BRIGHTNESS] = obj.optDouble("nightBrightness", 1.0).toFloat().coerceIn(0.35f, 1.0f)
                if (obj.has("amberFilterEnabled")) prefs[KEY_AMBER_FILTER] = obj.optBoolean("amberFilterEnabled", false)
                if (obj.has("duckingEnabled")) prefs[KEY_DUCKING_ENABLED] = obj.optBoolean("duckingEnabled", true)
                obj.optString("bufferMode").takeIf { it.isNotBlank() }?.let { prefs[KEY_BUFFER_MODE] = it }
                if (obj.has("gaplessEnabled")) prefs[KEY_GAPLESS_ENABLED] = obj.optBoolean("gaplessEnabled", true)
                if (obj.has("replayGainEnabled")) prefs[KEY_REPLAYGAIN_ENABLED] = obj.optBoolean("replayGainEnabled", true)
                if (obj.has("crossfadeSeconds")) prefs[KEY_CROSSFADE_SECONDS] = obj.optInt("crossfadeSeconds", 0).coerceIn(0, 8)
                if (obj.has("sdvcEnabled")) prefs[KEY_SDVC_ENABLED] = obj.optBoolean("sdvcEnabled", true)
                obj.optString("sdvcSensitivity").takeIf { it.isNotBlank() }?.let { prefs[KEY_SDVC_SENSITIVITY] = it }
                obj.optString("swcLongPressAction").takeIf { it.isNotBlank() }?.let { prefs[KEY_SWC_LONG_PRESS_ACTION] = it }
                obj.optString("swcDoublePressAction").takeIf { it.isNotBlank() }?.let { prefs[KEY_SWC_DOUBLE_PRESS_ACTION] = it }
                if (obj.has("ignitionFadeEnabled")) prefs[KEY_IGNITION_FADE_ENABLED] = obj.optBoolean("ignitionFadeEnabled", true)
                if (obj.has("autoResumeOnBoot")) prefs[KEY_AUTO_RESUME_BOOT] = obj.optBoolean("autoResumeOnBoot", true)
                if (obj.has("autoResumeSoftRamp")) prefs[KEY_AUTO_RESUME_SOFT_RAMP] = obj.optBoolean("autoResumeSoftRamp", true)
                if (obj.has("playbackSpeed")) prefs[KEY_PLAYBACK_SPEED] = obj.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.75f, 2.0f)
                if (obj.has("swipeGesturesEnabled")) prefs[KEY_SWIPE_GESTURES] = obj.optBoolean("swipeGesturesEnabled", true)
                if (obj.has("idleScreensaverSeconds")) prefs[KEY_IDLE_SCREENSAVER_SEC] = obj.optInt("idleScreensaverSeconds", 0)
                obj.optString("visualizerMode").takeIf { it.isNotBlank() }?.let { prefs[KEY_VISUALIZER_MODE] = it }
                obj.optString("visualizerColorway").takeIf { it.isNotBlank() }?.let { prefs[KEY_VISUALIZER_COLORWAY] = it }
                if (obj.has("dualPaneCockpit")) prefs[KEY_DUAL_PANE_COCKPIT] = obj.optBoolean("dualPaneCockpit", true)
                if (obj.has("driveModeAutoSpeedKmH")) prefs[KEY_DRIVE_MODE_AUTO_SPEED_KMH] = obj.optInt("driveModeAutoSpeedKmH", 0).coerceIn(0, 60)
                if (obj.has("autoPlayOnBluetooth")) prefs[KEY_AUTO_PLAY_ON_BLUETOOTH] = obj.optBoolean("autoPlayOnBluetooth", true)
                if (obj.has("duckOnReverseGear")) prefs[KEY_DUCK_ON_REVERSE_GEAR] = obj.optBoolean("duckOnReverseGear", true)
                if (obj.has("globalAudioSessionAttach")) prefs[KEY_GLOBAL_AUDIO_SESSION_ATTACH] = obj.optBoolean("globalAudioSessionAttach", false)
                if (obj.has("cruisingSortByGenre")) prefs[KEY_CRUISING_SORT_BY_GENRE] = obj.optBoolean("cruisingSortByGenre", true)
                obj.optString("activeDriverProfileId").takeIf { it.isNotBlank() }?.let { prefs[KEY_ACTIVE_DRIVER_PROFILE_ID] = it }
                obj.optString("driverProfilesJson").takeIf { it.isNotBlank() }?.let { prefs[KEY_DRIVER_PROFILES_JSON] = it }
                obj.optString("swcCustomKeyMapJson").takeIf { it.isNotBlank() }?.let { prefs[KEY_SWC_CUSTOM_KEYMAP_JSON] = it }
                obj.optString("dspSettingsJson").takeIf { it.isNotBlank() }?.let { prefs[KEY_DSP_SETTINGS_JSON] = it }
                obj.optString("customDspPresetsJson").takeIf { it.isNotBlank() }?.let { prefs[KEY_CUSTOM_DSP_PRESETS_JSON] = it }
                obj.optString("adaptiveCabinConfigJson").takeIf { it.isNotBlank() }?.let { prefs[KEY_ADAPTIVE_CABIN_CONFIG_JSON] = it }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
