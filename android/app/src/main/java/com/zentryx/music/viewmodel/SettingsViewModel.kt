package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.player.CacheDataSourceFactory
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

    // Feature 2: SDVC / GALA
    val sdvcEnabled: StateFlow<Boolean> = settingsPreferences.sdvcEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sdvcSensitivity: StateFlow<String> = settingsPreferences.sdvcSensitivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIUM")

    // Feature 3: Storage Location
    val storageLocation: StateFlow<String> = settingsPreferences.storageLocationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INTERNAL")

    // Feature 4: SWC
    val swcLongPressAction: StateFlow<String> = settingsPreferences.swcLongPressActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SEEK_15S")

    val swcDoublePressAction: StateFlow<String> = settingsPreferences.swcDoublePressActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FAVORITE")

    // Feature 7: Engine Standby Fade
    val ignitionFadeEnabled: StateFlow<Boolean> = settingsPreferences.ignitionFadeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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
                val size = cacheDataSourceFactory.simpleCache.cacheSpace
                _cacheSizeBytes.value = size
            } catch (e: Exception) {
                _cacheSizeBytes.value = 0L
            }
        }
    }

    fun setAccentColor(accent: MbuxAccentColor) {
        viewModelScope.launch {
            settingsPreferences.setAccentColor(accent)
        }
    }

    fun setDuckingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setDuckingEnabled(enabled)
        }
    }

    fun setBufferMode(mode: String) {
        viewModelScope.launch {
            settingsPreferences.setBufferMode(mode)
        }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setGaplessEnabled(enabled)
        }
    }

    fun setSdvcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setSdvcEnabled(enabled)
        }
    }

    fun setSdvcSensitivity(sensitivity: String) {
        viewModelScope.launch {
            settingsPreferences.setSdvcSensitivity(sensitivity)
        }
    }

    fun setStorageLocation(location: String) {
        viewModelScope.launch {
            settingsPreferences.setStorageLocation(location)
        }
    }

    fun setSwcLongPressAction(action: String) {
        viewModelScope.launch {
            settingsPreferences.setSwcLongPressAction(action)
        }
    }

    fun setSwcDoublePressAction(action: String) {
        viewModelScope.launch {
            settingsPreferences.setSwcDoublePressAction(action)
        }
    }

    fun setIgnitionFadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setIgnitionFadeEnabled(enabled)
        }
    }

    fun purgeStreamCache() {
        viewModelScope.launch(ioDispatcher) {
            _isPurgingCache.value = true
            try {
                val cache = cacheDataSourceFactory.simpleCache
                val keys = cache.keys
                for (key in keys) {
                    cache.removeResource(key)
                }
                refreshCacheSize()
            } catch (e: Exception) {
                // Ignore
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
