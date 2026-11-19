package com.zentryx.music.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.dsp.*
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.models.Song
import com.zentryx.music.overlay.FloatingAudioOverlayService
import com.zentryx.music.player.*
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.scanner.UsbVolumeMonitor
import com.zentryx.music.security.EncryptedDataStore
import com.zentryx.music.swc.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedDataStore: EncryptedDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val cacheDataSourceFactory: CacheDataSourceFactory,
    private val musicRepository: MusicRepository,
    private val playerManager: ZentryxPlayerManager,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val cabinRtaCalibrator: CabinRtaCalibrator,
    private val speedVolumeController: SpeedVolumeController,
    private val downloadManager: DownloadManager,
    private val settingsBackupManager: SettingsBackupManager,
    private val steeringWheelManager: SteeringWheelManager,
    private val usbVolumeMonitor: UsbVolumeMonitor,
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

    val replayGainEnabled: StateFlow<Boolean> = settingsPreferences.replayGainEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val crossfadeSeconds: StateFlow<Int> = settingsPreferences.crossfadeSecondsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // SDVC / GALA & Live GPS Telemetry
    val sdvcEnabled: StateFlow<Boolean> = settingsPreferences.sdvcEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sdvcSensitivity: StateFlow<String> = settingsPreferences.sdvcSensitivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIUM")

    val currentSpeedKmH: StateFlow<Float> = speedVolumeController.currentSpeedKmH
    val currentBoostDb: StateFlow<Float> = speedVolumeController.currentBoostDb
    val gpsStatus: StateFlow<GpsTelemetryStatus> = speedVolumeController.gpsStatus

    // Offline Storage Location & Local USB Audio Source
    val storageLocation: StateFlow<String> = settingsPreferences.storageLocationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INTERNAL")

    val customDownloadUri: StateFlow<String?> = settingsPreferences.customDownloadUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customDownloadLabel: StateFlow<String?> = settingsPreferences.customDownloadLabelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val localSourceLabel: StateFlow<String?> = settingsPreferences.localSourceLabelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mountedUsbVolumes: StateFlow<List<String>> = usbVolumeMonitor.mountedVolumes
    val usbEventMessage: StateFlow<String?> = usbVolumeMonitor.lastEventMessage

    // SWC Controls & Interactive Key Learner
    val swcLongPressAction: StateFlow<String> = settingsPreferences.swcLongPressActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SEEK_15S")

    val swcDoublePressAction: StateFlow<String> = settingsPreferences.swcDoublePressActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FAVORITE")

    val swcCustomKeyMap: StateFlow<SwcCustomKeyMap> = steeringWheelManager.customKeyMap
    val swcLearningTarget: StateFlow<SwcLearnTarget?> = steeringWheelManager.learningTarget
    val swcLastCapturedKeyLabel: StateFlow<String?> = steeringWheelManager.lastCapturedKeyLabel

    // Engine Standby, Auto-Resume, Headunit Hardware Automation & QOL
    val ignitionFadeEnabled: StateFlow<Boolean> = settingsPreferences.ignitionFadeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoResumeOnBoot: StateFlow<Boolean> = settingsPreferences.autoResumeOnBootFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoResumeSoftRamp: StateFlow<Boolean> = settingsPreferences.autoResumeSoftRampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoPlayOnBluetooth: StateFlow<Boolean> = settingsPreferences.autoPlayOnBluetoothFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val duckOnReverseGear: StateFlow<Boolean> = settingsPreferences.duckOnReverseGearFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val globalAudioSessionAttach: StateFlow<Boolean> = settingsPreferences.globalAudioSessionAttachFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val floatingOverlayEnabled: StateFlow<Boolean> = settingsPreferences.floatingOverlayEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    val dualPaneCockpit: StateFlow<Boolean> = settingsPreferences.dualPaneCockpitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val driveModeAutoSpeedKmH: StateFlow<Int> = settingsPreferences.driveModeAutoSpeedKmHFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Multi-Profile Driver Cockpit Slots
    val driverProfiles: StateFlow<List<DriverProfile>> = settingsPreferences.driverProfilesJsonFlow
        .map { deserializeDriverProfiles(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DRIVER_PROFILES)

    val activeDriverProfileId: StateFlow<String> = settingsPreferences.activeDriverProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DRIVER_PROFILES.first().id)

    // Offline Roadtrip Budget Planner
    private val _roadtripHours = MutableStateFlow(3)
    val roadtripHours: StateFlow<Int> = _roadtripHours.asStateFlow()

    private val _roadtripPool = MutableStateFlow(RoadtripPool.FAVORITES_AND_UNHEARD)
    val roadtripPool: StateFlow<RoadtripPool> = _roadtripPool.asStateFlow()

    val roadtripPlan: StateFlow<RoadtripPlan> = combine(
        musicRepository.getAllSongs(),
        _roadtripHours,
        _roadtripPool
    ) { songs: List<Song>, hours: Int, pool: RoadtripPool ->
        buildRoadtripOfflinePlan(songs, hours, pool)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RoadtripPlan(3, RoadtripPool.FAVORITES_AND_UNHEARD, emptyList(), emptyList(), 0, 0, 0)
    )

    val batchDownloadProgress: StateFlow<Pair<Int, Int>?> = downloadManager.batchProgress

    // 16-Band DSP Console & Logic 7 Surround State
    val dspSettings: StateFlow<DspSettings> = dspAudioEngine.dspSettings
    val customDspPresets: StateFlow<List<DspPreset16>> = dspAudioEngine.customPresets
    val isDspAttached: StateFlow<Boolean> = dspAudioEngine.isAttached
    val isGlobalEqAttached: StateFlow<Boolean> = dspAudioEngine.isGlobalEqAttached
    val isHardwareVirtualizerActive: StateFlow<Boolean> = dspAudioEngine.isHardwareVirtualizerActive
    val activeOutputRoute: StateFlow<AudioOutputRoute> = dspAudioEngine.activeOutputRoute
    val activeCompareSlot: StateFlow<String> = dspAudioEngine.activeCompareSlot
    val slotBPresetName: StateFlow<String> = dspAudioEngine.slotBPresetName

    // Automated Cabin Noise & Speed Adaptive DSP Profile Switching
    val adaptiveCabinConfig: StateFlow<AdaptiveCabinConfig> = dspAudioEngine.adaptiveCabinConfig
    val adaptiveEvaluationState: StateFlow<AdaptiveEvaluationState> = dspAudioEngine.adaptiveEvaluationState
    val adaptiveDspDelta: StateFlow<AdaptiveDspDelta> = dspAudioEngine.adaptiveDspDelta
    val cabinNoiseDb: StateFlow<Float?> = dspAudioEngine.cabinNoiseDb
    val cabinMicStatus: StateFlow<CabinMicMonitorStatus> = dspAudioEngine.cabinMicStatus

    // Cabin RTA 5-Second Pink-Noise Auto-Calibration State
    private val _rtaRunning = MutableStateFlow(false)
    val rtaRunning: StateFlow<Boolean> = _rtaRunning.asStateFlow()

    private val _rtaProgress = MutableStateFlow(0f)
    val rtaProgress: StateFlow<Float> = _rtaProgress.asStateFlow()

    private val _rtaStatusText = MutableStateFlow("Ready — Park cabin quietly & run 5s pink-noise sweep")
    val rtaStatusText: StateFlow<String> = _rtaStatusText.asStateFlow()

    private val _rtaLiveBandsDb = MutableStateFlow<List<Float>>(List(16) { 0f })
    val rtaLiveBandsDb: StateFlow<List<Float>> = _rtaLiveBandsDb.asStateFlow()

    private val _rtaResult = MutableStateFlow<CabinRtaResult?>(null)
    val rtaResult: StateFlow<CabinRtaResult?> = _rtaResult.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _isPurgingCache = MutableStateFlow(false)
    val isPurgingCache: StateFlow<Boolean> = _isPurgingCache.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _isLocalScanning = MutableStateFlow(false)
    val isLocalScanning: StateFlow<Boolean> = _isLocalScanning.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private var scanJob: Job? = null
    private var rtaJob: Job? = null

    init {
        refreshCacheSize()
        usbVolumeMonitor.refreshVolumes()
    }

    fun getStorageSummary(
        location: String,
        customLabel: String? = null,
        customUri: String? = null
    ): String = downloadManager.getActiveStorageSummary(location, customLabel, customUri)

    fun refreshCacheSize() {
        viewModelScope.launch(ioDispatcher) {
            try {
                _cacheSizeBytes.value = cacheDataSourceFactory.simpleCache.cacheSpace
            } catch (_: Exception) {
                _cacheSizeBytes.value = 0L
            }
        }
    }

    fun refreshUsbVolumes() {
        usbVolumeMonitor.refreshVolumes()
    }

    fun pollGps() {
        speedVolumeController.refreshGps()
    }

    fun canDrawFloatingOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun setFloatingOverlayEnabled(enabled: Boolean): Boolean {
        if (enabled && !canDrawFloatingOverlay()) {
            _backupStatusMessage.value = "Grant 'Display over other apps' permission to float over Maps/Waze"
            return false
        }
        viewModelScope.launch {
            settingsPreferences.setFloatingOverlayEnabled(enabled)
        }
        if (enabled) {
            FloatingAudioOverlayService.startOverlay(context)
        } else {
            FloatingAudioOverlayService.stopOverlay(context)
        }
        return true
    }

    fun selectDriverProfile(profileId: String) = viewModelScope.launch {
        val profile = driverProfiles.value.firstOrNull { it.id == profileId } ?: return@launch
        settingsPreferences.setActiveDriverProfileId(profile.id)
        settingsPreferences.setAccentColor(profile.accentColor)
        settingsPreferences.setCockpitThemeMode(profile.themeMode)
        settingsPreferences.setNightBrightness(profile.nightBrightness)
        settingsPreferences.setAmberFilterEnabled(profile.amberFilterEnabled)

        val preset = (FACTORY_DSP_PRESETS + customDspPresets.value).firstOrNull { it.id == profile.dspPresetId }
        val baseDsp = if (preset != null) applyPresetToDsp(dspSettings.value, preset) else dspSettings.value
        dspAudioEngine.updateSettings(
            baseDsp.copy(
                dspEnabled = true,
                listeningStage = profile.listeningStage,
                leftDelayMs = profile.listeningStage.leftDelayMs,
                rightDelayMs = profile.listeningStage.rightDelayMs,
                balance = profile.balance,
                fader = profile.fader,
                surroundMode = profile.surroundMode,
                surroundIntensity = profile.surroundIntensity,
                subTrimDb = profile.subTrimDb
            )
        )
        _backupStatusMessage.value = "Loaded driver profile: ${profile.name}"
    }

    fun saveCurrentTuneToActiveDriverProfile() = viewModelScope.launch {
        val activeId = activeDriverProfileId.value
        val curDsp = dspSettings.value
        val updated = driverProfiles.value.map { profile ->
            if (profile.id != activeId) profile
            else profile.copy(
                accentColor = accentColor.value,
                themeMode = cockpitThemeMode.value,
                nightBrightness = nightBrightness.value,
                amberFilterEnabled = amberFilterEnabled.value,
                listeningStage = curDsp.listeningStage,
                balance = curDsp.balance,
                fader = curDsp.fader,
                surroundMode = curDsp.surroundMode,
                surroundIntensity = curDsp.surroundIntensity,
                subTrimDb = curDsp.subTrimDb,
                dspPresetId = curDsp.selectedPresetId
            )
        }
        settingsPreferences.saveDriverProfilesJson(serializeDriverProfiles(updated))
        val activeName = updated.firstOrNull { it.id == activeId }?.name ?: "Profile"
        _backupStatusMessage.value = "Saved current cabin tune to $activeName"
    }

    fun setRoadtripHours(hours: Int) {
        _roadtripHours.value = hours.coerceIn(1, 24)
    }

    fun setRoadtripPool(pool: RoadtripPool) {
        _roadtripPool.value = pool
    }

    fun pinRoadtripOffline() {
        val missing = roadtripPlan.value.missingOfflineSongs
        if (missing.isEmpty()) {
            _backupStatusMessage.value = "All planned roadtrip tracks are already offline!"
            return
        }
        downloadManager.downloadBatch(missing)
        _backupStatusMessage.value = "Pinning ${missing.size} roadtrip tracks offline…"
    }

    fun startCabinRtaSweep() {
        if (_rtaRunning.value) return
        rtaJob?.cancel()
        rtaJob = viewModelScope.launch {
            val wasPlaying = playerManager.playbackState.value.isPlaying
            if (wasPlaying) playerManager.pause()
            _rtaRunning.value = true
            _rtaProgress.value = 0f
            _rtaResult.value = null
            _rtaStatusText.value = "Emitting pink noise sweep…"
            try {
                val result = cabinRtaCalibrator.runFiveSecondSweep { progress, liveBands, status ->
                    _rtaProgress.value = progress
                    _rtaLiveBandsDb.value = liveBands
                    _rtaStatusText.value = status
                }
                _rtaResult.value = result
                _rtaProgress.value = 1f
                _rtaStatusText.value = "Sweep complete (${result.sourceLabel}) · Peak correction ±${result.peakDeviationDb} dB"
            } finally {
                _rtaRunning.value = false
            }
        }
    }

    fun applyCabinRtaCorrections() {
        val result = _rtaResult.value ?: return
        val current = dspSettings.value
        val calibratedBands = (0 until 16).map { idx ->
            clampStep(current.bands16.getOrElse(idx) { 0f } + result.suggestedCorrections16[idx], -12f, 12f, 0.5f)
        }
        updateDspSettings(
            current.copy(
                dspEnabled = true,
                selectedPresetId = "custom",
                bands16 = calibratedBands
            )
        )
        _rtaStatusText.value = "Applied Harman Cabin RTA calibration to 16-band EQ"
    }

    fun setAccentColor(accent: MbuxAccentColor) = viewModelScope.launch { settingsPreferences.setAccentColor(accent) }
    fun setDuckingEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setDuckingEnabled(enabled) }
    fun setBufferMode(mode: String) = viewModelScope.launch { settingsPreferences.setBufferMode(mode) }
    fun setGaplessEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setGaplessEnabled(enabled) }
    fun setReplayGainEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setReplayGainEnabled(enabled) }
    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch { settingsPreferences.setCrossfadeSeconds(seconds) }
    fun setSdvcEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setSdvcEnabled(enabled) }
    fun setSdvcSensitivity(sensitivity: String) = viewModelScope.launch { settingsPreferences.setSdvcSensitivity(sensitivity) }
    fun setStorageLocation(location: String) = viewModelScope.launch { settingsPreferences.setStorageLocation(location) }
    fun setCustomDownloadFolder(uri: String?, label: String?) = viewModelScope.launch { settingsPreferences.setCustomDownloadFolder(uri, label) }
    fun setLocalSourceFolder(uri: Uri?, label: String?) = viewModelScope.launch {
        settingsPreferences.setLocalSourceFolder(uri?.toString(), label)
        if (uri != null) {
            scanLocalAudio(uri)
        }
    }
    fun scanLocalAudio(treeUri: Uri? = null) = viewModelScope.launch {
        _isLocalScanning.value = true
        try {
            usbVolumeMonitor.refreshVolumes()
            val count = musicRepository.scanLocalLibrary(treeUri)
            _backupStatusMessage.value = "Indexed $count local USB/device tracks"
        } finally {
            _isLocalScanning.value = false
        }
    }
    fun setSwcLongPressAction(action: String) = viewModelScope.launch { settingsPreferences.setSwcLongPressAction(action) }
    fun setSwcDoublePressAction(action: String) = viewModelScope.launch { settingsPreferences.setSwcDoublePressAction(action) }
    fun startSwcLearning(action: SwcAction, pressType: SwcPressType) = steeringWheelManager.startLearning(action, pressType)
    fun cancelSwcLearning() = steeringWheelManager.cancelLearning()
    fun clearSwcCustomKeyMap() = steeringWheelManager.clearCustomKeyMap()

    fun setIgnitionFadeEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setIgnitionFadeEnabled(enabled) }
    fun setAutoResumeOnBoot(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAutoResumeOnBoot(enabled) }
    fun setAutoResumeSoftRamp(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAutoResumeSoftRamp(enabled) }
    fun setAutoPlayOnBluetooth(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAutoPlayOnBluetooth(enabled) }
    fun setDuckOnReverseGear(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setDuckOnReverseGear(enabled) }
    fun setGlobalAudioSessionAttach(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setGlobalAudioSessionAttach(enabled) }
    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch { settingsPreferences.setPlaybackSpeed(speed) }
    fun setSwipeGesturesEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setSwipeGesturesEnabled(enabled) }
    fun setCockpitThemeMode(mode: CockpitThemeMode) = viewModelScope.launch { settingsPreferences.setCockpitThemeMode(mode) }
    fun setNightBrightness(level: Float) = viewModelScope.launch { settingsPreferences.setNightBrightness(level) }
    fun setAmberFilterEnabled(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setAmberFilterEnabled(enabled) }
    fun setIdleScreensaverSeconds(seconds: Int) = viewModelScope.launch { settingsPreferences.setIdleScreensaverSeconds(seconds) }
    fun setDualPaneCockpit(enabled: Boolean) = viewModelScope.launch { settingsPreferences.setDualPaneCockpit(enabled) }
    fun setDriveModeAutoSpeedKmH(kmh: Int) = viewModelScope.launch { settingsPreferences.setDriveModeAutoSpeedKmH(kmh) }

    fun exportSettingsToUri(uri: Uri) = viewModelScope.launch {
        val res = settingsBackupManager.exportToUri(uri)
        _backupStatusMessage.value = res.fold(onSuccess = { it }, onFailure = { it.message ?: "Export failed" })
    }

    fun importSettingsFromUri(uri: Uri) = viewModelScope.launch {
        val res = settingsBackupManager.importFromUri(uri)
        _backupStatusMessage.value = res.fold(onSuccess = { it }, onFailure = { it.message ?: "Import failed" })
    }

    fun pushBackupToNextcloud() = viewModelScope.launch {
        val res = settingsBackupManager.pushBackupToNextcloud()
        _backupStatusMessage.value = res.fold(onSuccess = { it }, onFailure = { it.message ?: "Cloud backup failed" })
    }

    fun pullBackupFromNextcloud() = viewModelScope.launch {
        val res = settingsBackupManager.pullBackupFromNextcloud()
        _backupStatusMessage.value = res.fold(onSuccess = { it }, onFailure = { it.message ?: "Cloud restore failed" })
    }

    fun clearBackupStatus() {
        _backupStatusMessage.value = null
    }

    fun updateDspSettings(settings: DspSettings) {
        if (settings.selectedPresetId != dspSettings.value.selectedPresetId) {
            playerManager.markManualPresetOverrideForCurrentTrack()
        }
        dspAudioEngine.updateSettings(settings)
    }

    fun saveCustomDspPreset(name: String) = dspAudioEngine.saveCustomPreset(name)
    fun deleteCustomDspPreset(presetId: String) = dspAudioEngine.deleteCustomPreset(presetId)
    fun toggleCompareSlot() = dspAudioEngine.toggleCompareSlot()
    fun snapshotCurrentToSlotB() = dspAudioEngine.snapshotCurrentToSlotB()
    fun setHoldBypass(bypassed: Boolean) = dspAudioEngine.setHoldBypass(bypassed)
    fun selectOutputRouteManually(route: AudioOutputRoute) = dspAudioEngine.selectOutputRouteManually(route)
    fun updateAdaptiveCabinConfig(config: AdaptiveCabinConfig) = dspAudioEngine.updateAdaptiveCabinConfig(config)
    fun onRecordAudioPermissionGranted() = dspAudioEngine.restartCabinMicMonitorIfEnabled()

    fun purgeStreamCache() {
        viewModelScope.launch(ioDispatcher) {
            _isPurgingCache.value = true
            try {
                val cache = cacheDataSourceFactory.simpleCache
                for (key in cache.keys.toList()) {
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
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            musicRepository.startScan().collect { progress ->
                _scanProgress.value = progress
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            playerManager.pause()
            FloatingAudioOverlayService.stopOverlay(context)
            encryptedDataStore.clearAll()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onLoggedOut()
            }
        }
    }
}
