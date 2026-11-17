package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.DspPreset16
import com.zentryx.music.dsp.DspSettings
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.player.CacheDataSourceFactory
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val encryptedDataStore: EncryptedDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val cacheDataSourceFactory: CacheDataSourceFactory,
    private val musicRepository: MusicRepository,
    private val playerManager: ZentryxPlayerManager,
    private val dspAudioEngine: AndroidDspAudioEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val serverConfig: StateFlow<ServerConfig?> = encryptedDataStore.serverConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accentColor: StateFlow<MbuxAccentColor> = settingsPreferences.accentColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MbuxAccentColor.LIME)

    val duckingEnabled: StateFlow<Boolean> = settingsPreferences.duckingEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val bufferMode: StateFlow<String> = settingsPreferences.bufferModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "RAM_SAVER")

    val gaplessEnabled: StateFlow<Boolean> = settingsPreferences.gaplessEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // SDVC / GALA
    val sdvcEnabled: StateFlow<Boolean> = settingsPreferences.sdvcEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sdvcSensitivity: StateFlow<String> = settingsPreferences.sdvcSensitivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIUM")

    // Offline Storage Location
    val storageLocation: StateFlow<String> = settingsPreferences.storageLocationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INTERNAL")

    // SWC Controls
    val swcLongPressAction: StateFlow<String> = settingsPreferences.swcLongPressActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SEEK_15S")

    val swcDoublePressAction: StateFlow<String> = settingsPreferences.swcDoublePressActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FAVORITE")

    // Engine Standby, Auto-Resume & QOL
    val ignitionFadeEnabled: StateFlow<Boolean> = settingsPreferences.ignitionFadeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoResumeOnBoot: StateFlow<Boolean> = settingsPreferences.autoResumeOnBootFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoResumeSoftRamp: StateFlow<Boolean> = settingsPreferences.autoResumeSoftRampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val playbackSpeed: StateFlow<Float> = settingsPreferences.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val swipeGesturesEnabled: StateFlow<Boolean> = settingsPreferences.swipeGesturesEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val cockpitThemeMode: StateFlow<CockpitThemeMode> = settingsPreferences.cockpitThemeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CockpitThemeMode.NIGHT)

    val nightBrightness: StateFlow<Float> = settingsPreferences.nightBrightnessFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val amberFilterEnabled: StateFlow<Boolean> = settingsPreferences.amberFilterEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val idleScreensaverSeconds: StateFlow<Int> = settingsPreferences.idleScreensaverSecondsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 16-Band DSP Console State
    val dspSettings: StateFlow<DspSettings> = dspAudioEngine.dspSettings
    val customDspPresets: StateFlow<List<DspPreset16>> = dspAudioEngine.customPresets
    val isDspAttached: StateFlow<Boolean> = dspAudioEngine.isAttached

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _isPurgingCache = MutableStateFlow(false)
    val isPurgingCache: StateFlow<Boolean> = _isPurgingCache.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch(ioDispatcher) {
            try {
                _cacheSizeBytes.value = cacheDataSourceFactory.simpleCache.cacheSpace
            } catch (_: Exception) {
                _cacheSizeBytes.value = 0L
            }
        }
    }

    fun setAccentColor(accent: MbuxAccentColor) = viewModelScope.launch { settingsPreferences.setAccentColor(accent) }
    fun setDuckingEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setDuckingEnabled(enabled) }
    fun setBufferMode(mode: String) = viewModelScope.launch { settingsPreferences.setBufferMode(mode) }
    fun setGaplessEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setGaplessEnabled(enabled) }
    fun setSdvcEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setSdvcEnabled(enabled) }
    fun setSdvcSensitivity(sensitivity: String) = viewModelScope.launch { settingsPreferences.setSdvcSensitivity(sensitivity) }
    fun setStorageLocation(location: String) = viewModelScope.launch { settingsPreferences.setStorageLocation(location) }
    fun setSwcLongPressAction(action: String) = viewModelScope.launch { settingsPreferences.setSwcLongPressAction(action) }
    fun setSwcDoublePressAction(action: String) = viewModelScope.launch { settingsPreferences.setSwcDoublePressAction(action) }
    fun setIgnitionFadeEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setIgnitionFadeEnabled(enabled) }
    fun setAutoResumeOnBoot(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAutoResumeOnBoot(enabled) }
    fun setAutoResumeSoftRamp(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAutoResumeSoftRamp(enabled) }
    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch { settingsPreferences.setPlaybackSpeed(speed) }
    fun setSwipeGesturesEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setSwipeGesturesEnabled(enabled) }
    fun setCockpitThemeMode(mode: CockpitThemeMode) = viewModelScope.launch { settingsPreferences.setCockpitThemeMode(mode) }
    fun setNightBrightness(level: Float) = viewModelScope.launch { settingsPreferences.setNightBrightness(level) }
    fun setAmberFilterEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAmberFilterEnabled(enabled) }
    fun setIdleScreensaverSeconds(seconds: Int) = viewModelScope.launch { settingsPreferences.setIdleScreensaverSeconds(seconds) }

    fun updateDspSettings(settings: DspSettings) = dspAudioEngine.updateSettings(settings)
    fun saveCustomDspPreset(name: String) = dspAudioEngine.saveCustomPreset(name)
    fun deleteCustomDspPreset(presetId: String) = dspAudioEngine.deleteCustomPreset(presetId)

    fun purgeStreamCache() {
        viewModelScope.launch(ioDispatcher) {
            _isPurgingCache.value = true
            try {
                val cache = cacheDataSourceFactory.simpleCache
                for (key in cache.keys) {
                    cache.removeResource(key)
                }
                refreshCacheSize()
            } catch (_: Exception) {
            } finally {
                _isPurgingCache.value = false
            }
        }
    }

    fun triggerFullRescan() {
        viewModelScope.launch {
            musicRepository.startScan()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            playerManager.pause()
            encryptedDataStore.clearAll()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onLoggedOut()
            }
        }
    }
}
