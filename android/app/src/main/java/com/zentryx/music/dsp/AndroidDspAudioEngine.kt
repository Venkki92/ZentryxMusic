package com.zentryx.music.dsp

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zentryx.music.player.SettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.math.abs

/**
 * Native Android Headunit Audio DSP Engine.
 * - Combines CarDspAudioProcessor (Butterworth HPF, Sub LPF/Phase, Mono fold-down, ms L/R Time Alignment, Limiter ceiling)
 *   with hardware DynamicsProcessing (16-band Pre-EQ, Preamp/Cabin Stage + GALA SDVC boost, MBC, Limiter).
 * - Monitors active AudioOutputRoute (Cabin Speakers, Bluetooth A2DP, USB DAC / AUX) for auto-profiles.
 * - Provides instant A/B DSP profile comparison and temporary Hold-to-Bypass comparison.
 */
/**
 * Alias matching the cross-platform HeadunitDspEngine name for native Android diagnostics & debuggers.
 */
typealias HeadunitDspEngine = AndroidDspAudioEngine

@Singleton
class AndroidDspAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences,
    private val carDspAudioProcessor: CarDspAudioProcessor,
    private val cabinNoiseMonitor: CabinNoiseMonitor
) {
    companion object {
        private const val TAG = "ZentryxDspEngine"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _dspSettings = MutableStateFlow(DspSettings())
    val dspSettings: StateFlow<DspSettings> = _dspSettings.asStateFlow()

    private val _customPresets = MutableStateFlow<List<DspPreset16>>(emptyList())
    val customPresets: StateFlow<List<DspPreset16>> = _customPresets.asStateFlow()

    private val _isAttached = MutableStateFlow(false)
    val isAttached: StateFlow<Boolean> = _isAttached.asStateFlow()

    private val _isGlobalEqAttached = MutableStateFlow(false)
    val isGlobalEqAttached: StateFlow<Boolean> = _isGlobalEqAttached.asStateFlow()

    private val _isHardwareVirtualizerActive = MutableStateFlow(false)
    val isHardwareVirtualizerActive: StateFlow<Boolean> = _isHardwareVirtualizerActive.asStateFlow()

    private val _lastSurroundDiagnostics = MutableStateFlow<SurroundMatrixDiagnostics?>(null)
    val lastSurroundDiagnostics: StateFlow<SurroundMatrixDiagnostics?> = _lastSurroundDiagnostics.asStateFlow()

    @Volatile private var lastHardwareVirtualizerStrengthPermille: Int = 0

    private val _activeOutputRoute = MutableStateFlow(detectCurrentOutputRoute())
    val activeOutputRoute: StateFlow<AudioOutputRoute> = _activeOutputRoute.asStateFlow()

    // A/B Profile Compare state (Slot A is live unless activeSlot == 'B')
    private val _activeCompareSlot = MutableStateFlow("A")
    val activeCompareSlot: StateFlow<String> = _activeCompareSlot.asStateFlow()

    private var slotBSnapshot: DspSettings = applyPresetToDsp(DspSettings(), FACTORY_DSP_PRESETS.first { it.id == "flat" })
    private val _slotBPresetName = MutableStateFlow("Flat / Studio Direct")
    val slotBPresetName: StateFlow<String> = _slotBPresetName.asStateFlow()

    private var isHoldBypassed: Boolean = false
    private var preHoldDspEnabled: Boolean = true
    private var isHoldSurroundBypassed: Boolean = false
    private var preHoldSurroundMode: SurroundMode = SurroundMode.OFF
    private val _isHoldSurroundDirect = MutableStateFlow(false)
    val isHoldSurroundDirect: StateFlow<Boolean> = _isHoldSurroundDirect.asStateFlow()

    private val _adaptiveCabinConfig = MutableStateFlow(AdaptiveCabinConfig())
    val adaptiveCabinConfig: StateFlow<AdaptiveCabinConfig> = _adaptiveCabinConfig.asStateFlow()

    private val _adaptiveEvaluationState = MutableStateFlow(AdaptiveEvaluationState())
    val adaptiveEvaluationState: StateFlow<AdaptiveEvaluationState> = _adaptiveEvaluationState.asStateFlow()

    private val _adaptiveDspDelta = MutableStateFlow(AdaptiveDspDelta())
    val adaptiveDspDelta: StateFlow<AdaptiveDspDelta> = _adaptiveDspDelta.asStateFlow()

    val cabinNoiseDb: StateFlow<Float?> = cabinNoiseMonitor.cabinNoiseDb
    val cabinMicStatus: StateFlow<CabinMicMonitorStatus> = cabinNoiseMonitor.status

    private var adaptiveEvalJob: Job? = null

    private var currentAudioSessionId: Int = 0
    private var globalSessionRequested: Boolean = false
    private var attachedSessionId: Int = -1
    private var dynamicSdvcBoostDb: Float = 0f
    private var currentVehicleSpeedKmH: Float = 0f
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var legacyBassBoost: BassBoost? = null
    private var legacyLoudness: LoudnessEnhancer? = null
    private var hardwareVirtualizer: Virtualizer? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            handleOutputRouteChanged(detectCurrentOutputRoute())
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            handleOutputRouteChanged(detectCurrentOutputRoute())
        }
    }

    init {
        scope.launch {
            settingsPreferences.dspSettingsJsonFlow.collectLatest { json ->
                val parsed = deserializeDspSettings(json)
                _dspSettings.value = parsed
                applyEffectiveDsp(parsed)
            }
        }
        scope.launch {
            settingsPreferences.adaptiveCabinConfigJsonFlow.collectLatest { json ->
                val parsed = deserializeAdaptiveCabinConfig(json)
                _adaptiveCabinConfig.value = parsed
                syncAdaptiveMonitoring(parsed)
            }
        }
        scope.launch {
            settingsPreferences.customDspPresetsJsonFlow.collectLatest { json ->
                _customPresets.value = deserializeCustomPresets(json)
            }
        }
        scope.launch {
            settingsPreferences.globalAudioSessionAttachFlow.collectLatest { globalEnabled ->
                if (globalEnabled != globalSessionRequested) {
                    globalSessionRequested = globalEnabled
                    rebindAudioSession()
                }
            }
        }
        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            Log.w(TAG, "AudioDeviceCallback registration warning: ${t.message}")
        }
    }

    private fun applyEffectiveDsp(baseSettings: DspSettings) {
        val effective = applyAdaptiveCabinOverlay(baseSettings, _adaptiveDspDelta.value)
        carDspAudioProcessor.updateSettings(effective)
        applyDspToHardware(effective)
    }

    private fun syncAdaptiveMonitoring(config: AdaptiveCabinConfig) {
        if (!config.enabled) {
            adaptiveEvalJob?.cancel()
            adaptiveEvalJob = null
            cabinNoiseMonitor.stop()
            _adaptiveEvaluationState.value = AdaptiveEvaluationState(
                activeProfile = AdaptiveCabinProfile.QUIET,
                triggerReason = "disabled"
            )
            if (_adaptiveDspDelta.value != AdaptiveDspDelta()) {
                _adaptiveDspDelta.value = AdaptiveDspDelta()
                applyEffectiveDsp(_dspSettings.value)
            }
            return
        }

        if (config.source == AdaptiveCabinSource.SPEED) {
            cabinNoiseMonitor.stop()
        } else {
            cabinNoiseMonitor.start()
        }

        if (adaptiveEvalJob?.isActive != true) {
            adaptiveEvalJob = scope.launch {
                var lastTickMs = System.currentTimeMillis()
                while (isActive) {
                    val nowMs = System.currentTimeMillis()
                    val dtMs = (nowMs - lastTickMs).coerceAtLeast(50L)
                    lastTickMs = nowMs

                    val cfg = _adaptiveCabinConfig.value
                    val nextState = evaluateAdaptiveCabinState(
                        prevState = _adaptiveEvaluationState.value,
                        config = cfg,
                        cabinNoiseDb = cabinNoiseMonitor.cabinNoiseDb.value,
                        speedKmH = currentVehicleSpeedKmH,
                        nowMs = nowMs
                    )
                    _adaptiveEvaluationState.value = nextState
                    val targetDelta = calculateAdaptiveDspDelta(nextState.activeProfile, cfg)
                    val smoothedDelta = smoothAdaptiveDspDelta(_adaptiveDspDelta.value, targetDelta, dtMs)
                    if (smoothedDelta != _adaptiveDspDelta.value) {
                        _adaptiveDspDelta.value = smoothedDelta
                        applyEffectiveDsp(_dspSettings.value)
                    }
                    delay(200L)
                }
            }
        } else {
            val targetDelta = calculateAdaptiveDspDelta(_adaptiveEvaluationState.value.activeProfile, config)
            val smoothedDelta = smoothAdaptiveDspDelta(_adaptiveDspDelta.value, targetDelta, 200L)
            if (smoothedDelta != _adaptiveDspDelta.value) {
                _adaptiveDspDelta.value = smoothedDelta
                applyEffectiveDsp(_dspSettings.value)
            }
        }
    }

    fun updateAdaptiveCabinConfig(newConfig: AdaptiveCabinConfig) {
        val safe = sanitizeAdaptiveCabinConfig(newConfig)
        _adaptiveCabinConfig.value = safe
        syncAdaptiveMonitoring(safe)
        scope.launch {
            settingsPreferences.saveAdaptiveCabinConfigJson(serializeAdaptiveCabinConfig(safe))
        }
    }

    fun restartCabinMicMonitorIfEnabled() {
        val cfg = _adaptiveCabinConfig.value
        if (cfg.enabled && cfg.source != AdaptiveCabinSource.SPEED) {
            cabinNoiseMonitor.stop()
            cabinNoiseMonitor.start()
        }
    }

    private fun detectCurrentOutputRoute(): AudioOutputRoute {
        val devices = try {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        } catch (_: Throwable) {
            emptyArray()
        }
        if (devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
            return AudioOutputRoute.BLUETOOTH_A2DP
        }
        if (devices.any {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_AUX_LINE
            }
        ) {
            return AudioOutputRoute.USB_DAC_AUX
        }
        return AudioOutputRoute.HEADUNIT_SPEAKERS
    }

    private fun handleOutputRouteChanged(newRoute: AudioOutputRoute) {
        if (_activeOutputRoute.value == newRoute) return
        _activeOutputRoute.value = newRoute
        val current = _dspSettings.value
        if (current.autoSwitchDevicePreset) {
            val targetPresetId = current.devicePresetMap[newRoute.id]
            val preset = (FACTORY_DSP_PRESETS + _customPresets.value).firstOrNull { it.id == targetPresetId }
            if (preset != null && current.selectedPresetId != preset.id) {
                updateSettings(applyPresetToDsp(current, preset))
            }
        }
    }

    fun selectOutputRouteManually(route: AudioOutputRoute) {
        _activeOutputRoute.value = route
        val current = _dspSettings.value
        if (current.autoSwitchDevicePreset) {
            val targetPresetId = current.devicePresetMap[route.id]
            val preset = (FACTORY_DSP_PRESETS + _customPresets.value).firstOrNull { it.id == targetPresetId }
            if (preset != null) {
                updateSettings(applyPresetToDsp(current, preset))
            }
        }
    }

    fun snapshotCurrentToSlotB() {
        val current = _dspSettings.value
        slotBSnapshot = current
        val name = (FACTORY_DSP_PRESETS + _customPresets.value)
            .firstOrNull { it.id == current.selectedPresetId }?.name ?: "Custom Curve"
        _slotBPresetName.value = name
    }

    fun toggleCompareSlot() {
        val current = _dspSettings.value
        val previousOther = slotBSnapshot
        slotBSnapshot = current
        val oldName = (FACTORY_DSP_PRESETS + _customPresets.value)
            .firstOrNull { it.id == current.selectedPresetId }?.name ?: "Custom Curve"
        _slotBPresetName.value = oldName
        _activeCompareSlot.value = if (_activeCompareSlot.value == "A") "B" else "A"
        updateSettings(previousOther)
    }

    fun setHoldBypass(bypassed: Boolean) {
        if (bypassed && !isHoldBypassed) {
            isHoldBypassed = true
            preHoldDspEnabled = _dspSettings.value.dspEnabled
            val temp = _dspSettings.value.copy(dspEnabled = false)
            _dspSettings.value = temp
            carDspAudioProcessor.updateSettings(temp)
            applyDspToHardware(temp)
        } else if (!bypassed && isHoldBypassed) {
            isHoldBypassed = false
            val restored = _dspSettings.value.copy(dspEnabled = preHoldDspEnabled)
            _dspSettings.value = restored
            applyEffectiveDsp(restored)
        }
    }

    /**
     * Momentarily bypasses only the Surround Matrix (forcing SurroundMode.OFF) while keeping
     * EQ/crossover intact so the listener can instantaneously A/B audition Surround vs Direct Stereo
     * without mutating persisted DataStore preferences.
     */
    fun setHoldSurroundBypass(bypassed: Boolean) {
        if (bypassed && !isHoldSurroundBypassed) {
            val current = _dspSettings.value
            if (current.surroundMode == SurroundMode.OFF) return
            isHoldSurroundBypassed = true
            preHoldSurroundMode = current.surroundMode
            _isHoldSurroundDirect.value = true
            val temp = current.copy(surroundMode = SurroundMode.OFF)
            applyEffectiveDsp(temp)
        } else if (!bypassed && isHoldSurroundBypassed) {
            isHoldSurroundBypassed = false
            _isHoldSurroundDirect.value = false
            val restored = _dspSettings.value.copy(surroundMode = preHoldSurroundMode)
            _dspSettings.value = restored
            applyEffectiveDsp(restored)
        }
    }

    fun applyPresetById(presetId: String) {
        val preset = (FACTORY_DSP_PRESETS + _customPresets.value).firstOrNull { it.id == presetId } ?: return
        val current = _dspSettings.value
        if (current.selectedPresetId == preset.id && current.dspEnabled) return
        val updated = applyPresetToDsp(current, preset)
        updateSettings(updated)
        logSurroundMatrixDiagnostics("applyPresetById(${preset.id})", updated)
    }

    val isPcmProcessorConfigured: Boolean
        get() = carDspAudioProcessor.isPcmConfigured

    val activeSampleRateHz: Int
        get() = carDspAudioProcessor.activeSampleRateHz

    val activeEffectiveWidthPercent: Int
        get() = carDspAudioProcessor.activeEffectiveWidthPercent

    fun logSurroundMatrixDiagnostics(
        trigger: String,
        settings: DspSettings = _dspSettings.value
    ): SurroundMatrixDiagnostics {
        val effective = applyAdaptiveCabinOverlay(settings, _adaptiveDspDelta.value)
        val diag = carDspAudioProcessor.buildLiveSurroundDiagnostics(
            trigger = trigger,
            settings = effective,
            hardwareVirtualizerActive = _isHardwareVirtualizerActive.value,
            hardwareVirtualizerStrengthPermille = lastHardwareVirtualizerStrengthPermille
        )
        _lastSurroundDiagnostics.value = diag
        val line = formatSurroundMatrixDiagnosticLog(diag)
        try {
            Log.i(TAG, line)
        } catch (_: Throwable) {
            // Safe when running under local JVM unit tests without android.util.Log mock
        }
        return diag
    }

    fun getSurroundDebugSnapshot(trigger: String = "debugger_live_poll"): SurroundDebugSnapshot {
        val effective = applyAdaptiveCabinOverlay(_dspSettings.value, _adaptiveDspDelta.value)
        val diag = carDspAudioProcessor.buildLiveSurroundDiagnostics(
            trigger = trigger,
            settings = effective,
            hardwareVirtualizerActive = _isHardwareVirtualizerActive.value,
            hardwareVirtualizerStrengthPermille = lastHardwareVirtualizerStrengthPermille
        )
        return SurroundDebugSnapshot(
            diagnostics = diag,
            bitstream = carDspAudioProcessor.snapshotBitstreamTelemetry(),
            hardwareAttached = _isAttached.value,
            globalSessionAttached = _isGlobalEqAttached.value,
            activeOutputRouteId = _activeOutputRoute.value.id,
            snapshotTimestampMs = System.currentTimeMillis()
        )
    }

    fun snapshotStereoSpectrum(): StereoSpectrumSnapshot = carDspAudioProcessor.snapshotStereoSpectrum()

    fun selectSurroundMode(mode: SurroundMode) {
        if (isHoldSurroundBypassed) {
            isHoldSurroundBypassed = false
            _isHoldSurroundDirect.value = false
        }
        val current = _dspSettings.value
        val liftedIntensity = if (mode != SurroundMode.OFF && current.surroundIntensity < 35) {
            75
        } else {
            current.surroundIntensity
        }
        val liftedCenter = if (mode != SurroundMode.OFF && current.centerCoherence == 0) {
            55
        } else {
            current.centerCoherence
        }
        val liftedHeight = if (
            (mode == SurroundMode.DOLBY_ATMOS_VIRTUAL || mode == SurroundMode.VIRTUAL_SURROUND_360) &&
            current.virtualHeight == 0
        ) {
            65
        } else {
            current.virtualHeight
        }
        val updated = current.copy(
            dspEnabled = true,
            surroundMode = mode,
            monoFoldDown = if (mode != SurroundMode.OFF) false else current.monoFoldDown,
            surroundIntensity = liftedIntensity,
            centerCoherence = liftedCenter,
            virtualHeight = liftedHeight
        )
        updateSettings(updated)
        logSurroundMatrixDiagnostics("selectSurroundMode(${mode.id})", updated)
    }

    fun cycleRoutePreset(route: AudioOutputRoute) {
        val current = _dspSettings.value
        val allPresets = FACTORY_DSP_PRESETS + _customPresets.value
        if (allPresets.isEmpty()) return
        val assignedId = current.devicePresetMap[route.id] ?: "flat"
        val idx = allPresets.indexOfFirst { it.id == assignedId }
        val nextPreset = allPresets[(idx + 1) % allPresets.size]
        val updatedMap = current.devicePresetMap.toMutableMap().apply { put(route.id, nextPreset.id) }
        updateSettings(current.copy(devicePresetMap = updatedMap))
    }

    fun cycleSurroundMode(): SurroundMode {
        val modes = SurroundMode.entries
        val cur = _dspSettings.value.surroundMode
        val next = modes[(modes.indexOf(cur) + 1) % modes.size]
        selectSurroundMode(next)
        return next
    }

    fun cycleEqPreset(): DspPreset16 {
        val all = FACTORY_DSP_PRESETS + _customPresets.value
        val curIdx = all.indexOfFirst { it.id == _dspSettings.value.selectedPresetId }
        val next = all[(curIdx + 1) % all.size]
        val updated = applyPresetToDsp(_dspSettings.value, next)
        updateSettings(updated)
        logSurroundMatrixDiagnostics("cycleEqPreset(${next.id})", updated)
        return next
    }

    fun updateVehicleSpeedKmH(speedKmH: Float) {
        currentVehicleSpeedKmH = speedKmH.coerceIn(0f, 240f)
        carDspAudioProcessor.setRoadNoiseSpeedKmH(currentVehicleSpeedKmH)
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 && !globalSessionRequested) return
        if (audioSessionId > 0) {
            currentAudioSessionId = audioSessionId
        }
        rebindAudioSession()
    }

    private fun rebindAudioSession() {
        val targetSession = if (globalSessionRequested) 0 else currentAudioSessionId
        if (targetSession < 0 || (!globalSessionRequested && targetSession == 0)) return
        if (targetSession == attachedSessionId && _isAttached.value) return

        releaseEffects()
        var attached = false
        var globalSucceeded = false

        if (globalSessionRequested) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attached = initDynamicsProcessing(0)
            }
            if (!attached) {
                attached = initLegacyEffects(0)
            }
            if (attached) {
                globalSucceeded = true
                attachedSessionId = 0
            } else if (currentAudioSessionId > 0) {
                Log.w(TAG, "Global session 0 rejected by OEM firmware; falling back to player session $currentAudioSessionId")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    attached = initDynamicsProcessing(currentAudioSessionId)
                }
                if (!attached) {
                    attached = initLegacyEffects(currentAudioSessionId)
                }
                if (attached) attachedSessionId = currentAudioSessionId
            }
        } else if (currentAudioSessionId > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attached = initDynamicsProcessing(currentAudioSessionId)
            }
            if (!attached) {
                attached = initLegacyEffects(currentAudioSessionId)
            }
            if (attached) attachedSessionId = currentAudioSessionId
        }

        if (attached && attachedSessionId >= 0) {
            hardwareVirtualizer = try {
                Virtualizer(0, attachedSessionId)
            } catch (t: Throwable) {
                Log.w(TAG, "Hardware Virtualizer unavailable on headunit (PCM surround still active): ${t.message}")
                null
            }
        }

        _isAttached.value = attached
        _isGlobalEqAttached.value = globalSucceeded
        applyEffectiveDsp(_dspSettings.value)
    }

    private fun initDynamicsProcessing(sessionId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, 16,
                true, 1,
                false, 0,
                true
            )
            builder.setPreferredFrameDuration(10f)
            val dp = DynamicsProcessing(0, sessionId, builder.build())
            dp.enabled = _dspSettings.value.dspEnabled || dynamicSdvcBoostDb > 0.1f
            dynamicsProcessing = dp
            true
        } catch (t: Throwable) {
            Log.w(TAG, "DynamicsProcessing unavailable on headunit, falling back to Legacy Equalizer: ${t.message}")
            dynamicsProcessing?.release()
            dynamicsProcessing = null
            false
        }
    }

    private fun initLegacyEffects(sessionId: Int): Boolean {
        return try {
            val eq = Equalizer(0, sessionId)
            val bb = try { BassBoost(0, sessionId) } catch (_: Throwable) { null }
            val le = try { LoudnessEnhancer(sessionId) } catch (_: Throwable) { null }
            legacyEqualizer = eq
            legacyBassBoost = bb
            legacyLoudness = le
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy AudioEffect attach failed: ${t.message}")
            false
        }
    }

    fun updateSettings(newSettings: DspSettings) {
        _dspSettings.value = newSettings
        applyEffectiveDsp(newSettings)
        scope.launch {
            settingsPreferences.saveDspSettingsJson(serializeDspSettings(newSettings))
        }
    }

    /**
     * Applies dynamic GPS GALA / SDVC speed compensation gain (in dB) directly through
     * the hardware pre-gain & brickwall limiter stage so highway boost never clips DACs.
     */
    fun setDynamicSdvcBoostDb(boostDb: Float) {
        val clamped = boostDb.coerceIn(0f, 6f)
        if (abs(clamped - dynamicSdvcBoostDb) < 0.1f) return
        dynamicSdvcBoostDb = clamped
        applyEffectiveDsp(_dspSettings.value)
    }

    fun saveCustomPreset(name: String) {
        val cleanName = name.trim().ifBlank { "Custom ${_customPresets.value.size + 1}" }
        val s = _dspSettings.value
        val preset = DspPreset16(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            description = "Saved headunit calibration",
            bands16 = s.bands16,
            bassBoostDb = s.bassBoostDb,
            bassBoostFreqHz = s.bassBoostFreqHz,
            preampDb = s.preampDb,
            hpfCutoffHz = s.hpfCutoffHz.hz,
            loudnessContour = s.loudnessContour,
            compressorProfileId = s.compressorProfile.id,
            surroundModeId = s.surroundMode.id,
            surroundIntensity = s.surroundIntensity,
            centerCoherence = s.centerCoherence,
            virtualHeight = s.virtualHeight,
            isCustom = true
        )
        val updated = listOf(preset) + _customPresets.value
        _customPresets.value = updated
        scope.launch {
            settingsPreferences.saveCustomDspPresetsJson(serializeCustomPresets(updated))
        }
        updateSettings(s.copy(selectedPresetId = preset.id))
    }

    fun deleteCustomPreset(presetId: String) {
        val updated = _customPresets.value.filterNot { it.id == presetId }
        _customPresets.value = updated
        scope.launch {
            settingsPreferences.saveCustomDspPresetsJson(serializeCustomPresets(updated))
        }
    }

    private fun applyHardwareVirtualizer(settings: DspSettings) {
        val virt = hardwareVirtualizer
        if (virt == null) {
            lastHardwareVirtualizerStrengthPermille = 0
            _isHardwareVirtualizerActive.value = false
            return
        }
        try {
            val shouldEnable = settings.dspEnabled &&
                !settings.monoFoldDown &&
                settings.surroundMode != SurroundMode.OFF &&
                settings.surroundIntensity > 0
            virt.enabled = shouldEnable
            var permille = 0
            if (shouldEnable && virt.strengthSupported) {
                val scale = when (settings.surroundMode) {
                    SurroundMode.VIRTUAL_SURROUND_360 -> 1.0f
                    SurroundMode.DOLBY_ATMOS_VIRTUAL -> 0.85f
                    SurroundMode.DOLBY_PRO_LOGIC_II -> 0.72f
                    SurroundMode.CONCERT_HALL -> 0.78f
                    SurroundMode.LOGIC7_SURROUND -> 0.65f
                    SurroundMode.STUDIO_FOCUSED -> 0.35f
                    SurroundMode.OFF -> 0f
                }
                permille = (settings.surroundIntensity * 10f * scale).toInt().coerceIn(0, 1000)
                virt.setStrength(permille.toShort())
            }
            lastHardwareVirtualizerStrengthPermille = permille
            _isHardwareVirtualizerActive.value = shouldEnable
        } catch (t: Throwable) {
            Log.w(TAG, "Hardware Virtualizer apply warning: ${t.message}")
            lastHardwareVirtualizerStrengthPermille = 0
            _isHardwareVirtualizerActive.value = false
        }
    }

    private fun applyDspToHardware(settings: DspSettings) {
        applyHardwareVirtualizer(settings)
        val sdvcActive = dynamicSdvcBoostDb > 0.1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = dynamicsProcessing
            if (dp != null) {
                try {
                    val active = settings.dspEnabled || sdvcActive
                    dp.enabled = active
                    if (!active) return

                    // Balance + Fader cabin stage + preamp + GALA SDVC speed gain per L/R channel
                    val (leftStageDb, rightStageDb) = calculateCabinStageGains(settings.balance, settings.fader)
                    val basePreamp = if (settings.dspEnabled) settings.preampDb else 0f
                    dp.setInputGainbyChannel(0, (basePreamp + (if (settings.dspEnabled) leftStageDb else 0f) + dynamicSdvcBoostDb).coerceIn(-30f, 12f))
                    dp.setInputGainbyChannel(1, (basePreamp + (if (settings.dspEnabled) rightStageDb else 0f) + dynamicSdvcBoostDb).coerceIn(-30f, 12f))

                    // 16-band Parametric Pre-EQ (zeroed if user bypassed DSP but kept SDVC active)
                    for (i in DSP_16_FREQUENCIES.indices) {
                        val bandGain = if (settings.dspEnabled) effectiveBandGainDb(i, settings) else 0f
                        val eqBand = DynamicsProcessing.EqBand(settings.dspEnabled, DSP_16_FREQUENCIES[i], bandGain)
                        dp.setPreEqBandAllChannelsTo(i, eqBand)
                    }

                    // Dynamics Compressor stage (MBC)
                    val comp = settings.compressorProfile
                    val mbcEnabled = settings.dspEnabled && comp != CompressorProfile.OFF
                    val mbcBand = DynamicsProcessing.MbcBand(
                        mbcEnabled,
                        20000f,
                        comp.attackMs,
                        comp.releaseMs,
                        comp.ratio.coerceAtLeast(1f),
                        comp.thresholdDb,
                        comp.kneeWidthDb,
                        -90f,
                        1f,
                        0f,
                        0f
                    )
                    dp.setMbcBandAllChannelsTo(0, mbcBand)

                    // Safety Brickwall Output Limiter with user-selectable peak ceiling
                    val ceiling = if (settings.dspEnabled) sanitizeLimiterCeilingDb(settings.peakLimiterCeilingDb) else -0.5f
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        2f,
                        80f,
                        10f,
                        ceiling,
                        0f
                    )
                    dp.setLimiterAllChannelsTo(limiter)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "Error updating DynamicsProcessing: ${t.message}")
                }
            }
        }

        // Legacy Equalizer fallback (API 26–27 or restricted firmware)
        try {
            val eq = legacyEqualizer
            if (eq != null) {
                eq.enabled = settings.dspEnabled
                if (settings.dspEnabled) {
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()
                    val numBands = eq.numberOfBands.toInt()
                    for (b in 0 until numBands) {
                        val centerHz = eq.getCenterFreq(b.toShort()) / 1000f
                        val closestIdx = DSP_16_FREQUENCIES.indices.minByOrNull { abs(DSP_16_FREQUENCIES[it] - centerHz) } ?: 0
                        val targetMb = (effectiveBandGainDb(closestIdx, settings) * 100f).toInt().coerceIn(minMb, maxMb)
                        eq.setBandLevel(b.toShort(), targetMb.toShort())
                    }
                }
            }
            legacyBassBoost?.let { bb ->
                bb.enabled = settings.dspEnabled && settings.bassBoostDb > 0.5f
                if (bb.strengthSupported) {
                    val strength = ((settings.bassBoostDb / 12f) * 1000f).toInt().coerceIn(0, 1000)
                    bb.setStrength(strength.toShort())
                }
            }
            legacyLoudness?.let { le ->
                val loudnessBoostDb = (if (settings.dspEnabled && settings.loudnessContour) 4f else 0f) + dynamicSdvcBoostDb
                le.enabled = loudnessBoostDb > 0.2f
                if (le.enabled) {
                    le.setTargetGain((loudnessBoostDb.coerceIn(0f, 10f) * 100f).toInt())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy Equalizer apply warning: ${t.message}")
        }
    }

    fun releaseEffects() {
        try { dynamicsProcessing?.release() } catch (_: Throwable) {}
        try { legacyEqualizer?.release() } catch (_: Throwable) {}
        try { legacyBassBoost?.release() } catch (_: Throwable) {}
        try { legacyLoudness?.release() } catch (_: Throwable) {}
        try { hardwareVirtualizer?.release() } catch (_: Throwable) {}
        dynamicsProcessing = null
        legacyEqualizer = null
        legacyBassBoost = null
        legacyLoudness = null
        hardwareVirtualizer = null
        attachedSessionId = -1
        _isAttached.value = false
        _isGlobalEqAttached.value = false
        _isHardwareVirtualizerActive.value = false
    }
}
